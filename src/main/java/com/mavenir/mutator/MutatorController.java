// Copyright 2020 Mavenir
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.xgvela.mutator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.k8s.K8sUtil;
import org.xgvela.cnf.util.Utils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.admission.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.AdmissionResponse;
import io.fabric8.kubernetes.api.model.admission.AdmissionReview;

@RestController
public class MutatorController {

	private static enum Operation {

		ADD("add"), REMOVE("remove"), COPY("copy"), REPLACE("replace"), MOVE("move");

		private final String value;
		private final static Map<String, Operation> CONSTANTS = new HashMap<String, Operation>();

		static {
			for (Operation c : values()) {
				CONSTANTS.put(c.value, c);
			}
		}

		private Operation(String value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return this.value;
		}

		@JsonValue
		public String value() {
			return this.value;
		}

		@JsonCreator
		public static Operation fromValue(String value) {
			Operation constant = CONSTANTS.get(value);
			if (constant == null) {
				throw new IllegalArgumentException(value);
			} else {
				return constant;
			}
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private class JsonPatchOperation {

		@JsonProperty("op")
		Operation op;

		@JsonProperty("path")
		String path = null;

		@JsonProperty("value")
		String value = null;

		public JsonPatchOperation() {
		}

		public void setOp(Operation op) {
			this.op = op;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

	private static final Logger LOG = LogManager.getLogger(MutatorController.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Autowired
	private K8sUtil k8s;

	@PostMapping(path = "/mutate", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AdmissionReview> mutate(@RequestBody AdmissionReview admReview) {

		log("Reached CMaaS Mutator Controller !!!");

		AdmissionRequest admRequest = admReview.getRequest();
		log("ConfigMap Name: [" + admRequest.getName() + "], Namespace: [" + admRequest.getNamespace() + "]");

		AdmissionResponse admResponse = new AdmissionResponse();
		admResponse.setAllowed(true);
		admResponse.setUid(admRequest.getUid());

		try {
			ConfigMap oldConfigMap = (ConfigMap) admRequest.getOldObject();
			ConfigMap newConfigMap = (ConfigMap) admRequest.getObject();

			if (k8s.isEditable(oldConfigMap) && k8s.isEditable(newConfigMap)) {

				Map<String, String> oldConfig = oldConfigMap.getData();
				String oldSvcVersion = oldConfigMap.getMetadata().getAnnotations().get(Constants.SVC_VERSION);
				String oldRevision = oldConfig.get(Constants.REVISION_KEY);

				Map<String, String> newConfig = newConfigMap.getData();
				String newSvcVersion = newConfigMap.getMetadata().getAnnotations().get(Constants.SVC_VERSION);

				// helm upgrade inititated for a service
				if ((!oldSvcVersion.equals(newSvcVersion)
						|| newConfigMap.getMetadata().getAnnotations().containsKey(Constants.ANN_INIT))) {

					log("NF UPGRADE IN-PROGRESS !!!");
					List<JsonPatchOperation> patch = new ArrayList<>();

					// keep old revision value during update
					log("Adding patch: Replace revision");
					patch.add(getRevisionOp(oldRevision));

					newConfig.keySet().stream()
							.filter(key -> Utils.isValidJsonDataKey(key) && oldConfig.containsKey(key))
							.forEach(dataKey -> {
								log("Adding patch: Replace JSON data (" + dataKey + ")");
								JsonPatchOperation operation = new JsonPatchOperation();
								operation.setOp(Operation.REPLACE);
								operation.setPath("/data/" + dataKey);
								operation.setValue(oldConfig.get(dataKey));
								patch.add(operation);
							});

					// remove init annotation
					if (newConfigMap.getMetadata().getAnnotations().containsKey(Constants.ANN_INIT)) {
						log("Adding patch: Remove init annotation: " + Constants.ANN_INIT);
						patch.add(getRmOp());
					}

					// set mutate annotation
					if (!oldSvcVersion.equals(newSvcVersion)) {
						log("Adding patch: Add mutate annotation: " + Constants.ANN_MUTATE);
						patch.add(getAddOp());
					}

					log("Reported patch: \n" + MAPPER.writeValueAsString(patch));
					admResponse.setPatchType("JSONPatch");
					admResponse.setPatch(encode(patch));
				}
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return ResponseEntity.status(HttpStatus.OK)
				.body(new AdmissionReview(admReview.getApiVersion(), admReview.getKind(), admRequest, admResponse));
	}

	private static void log(String msg) {
		LOG.info(Constants.ACTIVITY + "Mutator: >>> " + msg);
	}

	private JsonPatchOperation getRevisionOp(String revision) {
		JsonPatchOperation op = new JsonPatchOperation();
		op.setOp(Operation.REPLACE);
		op.setPath("/data/revision");
		op.setValue(revision);
		return op;
	}

	private JsonPatchOperation getAddOp() {
		JsonPatchOperation op = new JsonPatchOperation();
		op.setOp(Operation.ADD);
		op.setPath("/metadata/annotations/" + Constants.ANN_MUTATE);
		op.setValue("true");
		return op;
	}

	private JsonPatchOperation getRmOp() {
		JsonPatchOperation op = new JsonPatchOperation();
		op.setOp(Operation.REMOVE);
		op.setPath("/metadata/annotations/" + Constants.ANN_INIT);
		return op;
	}

	private String encode(List<JsonPatchOperation> patch) throws JsonProcessingException {
		return Base64.getEncoder().encodeToString(MAPPER.writeValueAsString(patch).getBytes(StandardCharsets.UTF_8));
	}

}
