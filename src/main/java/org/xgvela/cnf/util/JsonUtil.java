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

package org.xgvela.cnf.util;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.netconf.NetconfUtil;
import org.xgvela.cnf.util.Utils.ConfigDatatype;
import org.xgvela.cnf.util.Utils.RootType;
import org.xgvela.model.ConfModelMetadata;

import io.fabric8.zjsonpatch.JsonDiff;

@Component
public class JsonUtil {
	private static final Logger LOG = LogManager.getLogger(JsonUtil.class);

	@Autowired
	NetconfUtil netconf;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public JsonNode getJsonDiff(String initData, String updatedData) {

		// JSON Diff Patch
		JsonNode initialNode = null, updatedNode = null;
		try {
			if (initData.isEmpty())
				initialNode = JsonNodeFactory.instance.objectNode();
			else
				initialNode = MAPPER.readTree(initData);

			if (updatedData.isEmpty()) {
				updatedNode = JsonNodeFactory.instance.objectNode();
			} else
				updatedNode = MAPPER.readTree(updatedData);

			JsonNode diffNode = JsonDiff.asJson(initialNode, updatedNode);
			LOG.debug("Json Diff: " + diffNode.toPrettyString());
			return diffNode;
		} catch (NullPointerException e) {
			LOG.error("Unable to get diff-patch", e);
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
		return null;
	}

	public JSONArray getMicrosvcList(Map<String, String> cmapData, String dataFile) {

		JSONObject dependencies = new JSONObject(cmapData.get(Constants.DEPENDENCY_KEY));
		JSONArray microservices = dependencies.getJSONArray(dataFile.substring(0, dataFile.indexOf(Constants.JSON)));
		return microservices;
	}

	public String getUpdatedConfig(ConfModelMetadata model,
			ConcurrentHashMap<String, ConfModelMetadata> mapOfConfModels, ConfigDatatype datatype, String nfId) {

		LOG.debug("Module: [" + model.getModuleName() + "]");
		String configData = getUpdatedConfig(model, datatype);

		if (datatype.equals(ConfigDatatype.JSON)) {

			// remove module names
			Iterator<ConfModelMetadata> iterator = mapOfConfModels.values().iterator();
			while (iterator.hasNext()) {

				ConfModelMetadata confModel = iterator.next();
				if (configData.contains(confModel.getModuleName())) {
					LOG.debug("Removing module name: " + confModel.getModuleName());
					configData = configData.replaceAll(confModel.getModuleName() + ":", Constants.EMPTY_STRING);
				}
			}
			LOG.debug("Formatted JSON:\n" + configData);
		} else {

			// remove nf id
			configData = configData.replaceAll(":" + nfId, Constants.EMPTY_STRING);
			LOG.debug("Formatted XML:\n" + configData);
		}
		return configData;
	}

	private String getUpdatedConfig(ConfModelMetadata model, ConfigDatatype datatype) {

		String configData = netconf.get(model.getPrefix(), model.getRootName(), model.getRootType(), datatype);
		LOG.debug("Configuration in Netconf:\n" + configData);

		if (configData == null || configData.isEmpty()) // no entries
			return Constants.EMPTY_STRING;

		if (model.getRootType().equals(RootType.LIST)) {

			if (datatype.equals(ConfigDatatype.JSON)) {
				try {
					configData = MAPPER.writerWithDefaultPrettyPrinter()
							.writeValueAsString(MAPPER.readTree(configData).get("collection"));
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
			} else {
				String[] lines = configData.split("\n");
				for (int i = 0; i < lines.length; i++) {

					if (lines[i].startsWith("<collection")) {
						lines[i] = Constants.EMPTY_STRING;
						i = lines.length - 3;
					}
					if (lines[i].trim().equalsIgnoreCase("</collection>")) {
						lines[i] = Constants.EMPTY_STRING;
						break;
					}
				}
				StringBuilder builder = new StringBuilder(Constants.EMPTY_STRING);
				for (String line : lines) {
					if (!line.equals(Constants.EMPTY_STRING)) {
						builder.append(line).append("\n");
					}
				}
				configData = builder.toString();
			}
		}
		return configData;
	}
}