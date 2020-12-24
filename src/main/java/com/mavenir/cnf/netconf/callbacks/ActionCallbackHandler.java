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

package org.xgvela.cnf.netconf.callbacks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xgvela.cnf.netconf.models.ExecResponse;
import org.xgvela.cnf.netconf.models.GetRecordRequest;
import org.xgvela.cnf.netconf.models.GetRecordResponse;
import org.xgvela.cnf.netconf.models.OperationRequest;
import org.xgvela.cnf.netconf.models.OperationRequestParameter;
import org.xgvela.cnf.enums.OperationState;
import org.xgvela.cnf.enums.OperationStatus;
import org.xgvela.cnf.tmaas.model.OperationRecord;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamStart;
import com.tailf.conf.ConfXMLParamStop;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.proto.ActionCBType;

@Component
public class ActionCallbackHandler {

	private static final Logger LOG = LogManager.getLogger(ActionCallbackHandler.class);
	private static final String EXEC = "nf-operations-exec";
	private static final String RECORD_GET = "nf-operations-record-get";
	private static final String PREFIX = "nfXgvela";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String operation_exec_api = "/api/v1/tmaas/operations/exec";
	private static final String operation_records_api = "/api/v1/tmaas/operations/record";

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private CallbackUtil cbUtil;

	@ActionCallback(callPoint = RECORD_GET, callType = ActionCBType.ACTION)
	public ConfXMLParam[] doActionRecordGet(final DpActionTrans trans, final ConfTag name, final ConfObject[] keyPath,
			final ConfXMLParam[] params) throws DpCallbackException {

		LOG.info("=======> doActionRecordGet name=" + name.toString());
		cbUtil.logKp(keyPath);

		String nfId = ((ConfKey) keyPath[2]).elementAt(0).toString();
		String txnId = params[0].getValue() != null ? params[0].getValue().toString() : null;
		LOG.info("TransactionId: " + txnId + ", NfId: " + nfId);

		ConfXMLParam[] retVal = null;
		GetRecordRequest recordRequest = new GetRecordRequest(txnId, nfId, null);

		try {
			// set body
			String reqBody = MAPPER.writeValueAsString(recordRequest);
			LOG.info("RequestBody: " + reqBody);

			// set headers
			HttpHeaders reqHeaders = new HttpHeaders();
			reqHeaders.setContentType(MediaType.APPLICATION_JSON);

			HttpEntity<String> entity = new HttpEntity<String>(reqBody, reqHeaders);

			// GET REST call to TMaaS
			try {
				ResponseEntity<String> response = restTemplate
						.postForEntity(cbUtil.getTopoFqdn() + operation_records_api, entity, String.class);

				LOG.info("Request succeeded with response: " + response.getBody());
				retVal = prepareResponse(MAPPER.readValue(response.getBody(), GetRecordResponse.class).getRecord());

			} catch (HttpClientErrorException | HttpServerErrorException e) {

				// throw error in case tmaas returns 4xx/5xx
				LOG.error("Request failed with code: " + e.getStatusCode() + ", body: " + e.getResponseBodyAsString());
				throw new DpCallbackException(
						"Topo-Engine returned: " + e.getResponseBodyAsString() + ", Code: " + e.getStatusCode());

			} catch (ResourceAccessException e) {

				// throw IO errors
				LOG.error(e.getMessage(), e);
				throw new DpCallbackException("Unable to reach TMaaS");

			} catch (Exception e) {

				// throw error
				LOG.error(e.getMessage(), e);
				throw new DpCallbackException("Exception: " + e.getMessage());
			}
		} catch (JsonProcessingException e) {

			// throw error
			LOG.error(e.getMessage(), e);
			throw new DpCallbackException("Unable to deserialize request/response to Bean");
		}
		LOG.info("<======= doActionRecordGet retVal=" + (retVal == null ? null : Arrays.toString(retVal)));
		return retVal;
	}

	private ConfXMLParam[] prepareResponse(OperationRecord record) throws ConfException {

		ConfObject state = record.getOperationState() != null
				? new ConfEnumeration(getValue(record.getOperationState()))
				: new ConfEnumeration(4);

		ConfObject status = record.getOperationStatus() != null
				? new ConfEnumeration(getValue(record.getOperationStatus()))
				: new ConfEnumeration(0);

		return new ConfXMLParam[] { new ConfXMLParamStart(PREFIX, "operations-record"),
				new ConfXMLParamValue(PREFIX, "transactionId", resolve(record.getTransactionID())),
				new ConfXMLParamValue(PREFIX, "parentTransactionId", resolve(record.getParentTransactionID())),
				new ConfXMLParamValue(PREFIX, "operationId", resolve(record.getOperationId())),
				new ConfXMLParamValue(PREFIX, "target", resolve(record.getTarget())),
				new ConfXMLParamValue(PREFIX, "state", state), new ConfXMLParamValue(PREFIX, "status", status),
				new ConfXMLParamValue(PREFIX, "createTime", resolve(record.getCreateTime())),
				new ConfXMLParamValue(PREFIX, "updateTime", resolve(record.getUpdateTime())),
				new ConfXMLParamValue(PREFIX, "request", resolve(record.getRequest())),
				new ConfXMLParamValue(PREFIX, "response", resolve(record.getResponse())),
				new ConfXMLParamValue(PREFIX, "schedule", resolve(record.getSchedule())),
				new ConfXMLParamStop(PREFIX, "operations-record") };
	}

	private ConfObject resolve(String value) {
		return value != null ? new ConfBuf(value) : new ConfBuf("null");
	}

	private int getValue(OperationState state) {
		switch (state) {
		case ABORTING:
			return 3;
		case COMPLETED:
			return 4;
		case PENDING:
			return 1;
		case RUNNING:
			return 2;
		case SCHEDULED:
			return 0;
		}
		return 4;
	}

	private int getValue(OperationStatus status) {
		switch (status) {
		case CANCELLED:
			return 1;
		case FAILED:
			return 2;
		case SUCCESS:
			return 3;
		}
		return 0;
	}

	@ActionCallback(callPoint = EXEC, callType = ActionCBType.ACTION)
	public ConfXMLParam[] doActionExec(final DpActionTrans trans, final ConfTag name, final ConfObject[] keyPath,
			final ConfXMLParam[] params) throws DpCallbackException {

		LOG.info("=======> doActionExec name=" + name.toString());
		cbUtil.logKp(keyPath);

		OperationRequest operationRequest = new OperationRequest();

		// nf operation
		if (keyPath.length == 5) {
			operationRequest.setNfId(((ConfKey) keyPath[1]).elementAt(0).toString());

		} // nf service operation
		else {
			operationRequest.setNfServiceId(((ConfKey) keyPath[1]).elementAt(0).toString());
			operationRequest.setNfId(((ConfKey) keyPath[3]).elementAt(0).toString());
		}

		ConfXMLParam param;
		Marker marker = Marker.BEGIN;

		OperationRequestParameter parameter = null;
		List<OperationRequestParameter> parameters = new ArrayList<>();

		for (int i = 0; i < params.length; i++) {
			param = params[i];
			String tag = param.getTag(), value = param.getValue() != null ? param.getValue().toString() : null;

			LOG.info("param: " + i + ", tag:" + tag + ", value:" + value);

			if (tag.equals("operationId")) {
				operationRequest.setOperationId(value);

			} else if (tag.equals("parameters")) {

				switch (marker) {
				case BEGIN:
					parameter = new OperationRequestParameter();
					marker = Marker.END;
					break;

				case END:
					parameters.add(parameter);
					marker = Marker.BEGIN;
				}

			} else if (tag.equals("name")) {
				parameter.setName(value);

			} else if (tag.equals("value")) {
				parameter.setValue(value);

			} else if (tag.equals("body")) {
				operationRequest.setBody(value);
			}
		}

		operationRequest.setParameters(parameters);
		ConfXMLParam[] retVal = null;

		try {
			// set body
			String reqBody = MAPPER.writeValueAsString(operationRequest);
			LOG.info("RequestBody: " + reqBody);

			// set headers
			HttpHeaders reqHeaders = new HttpHeaders();
			reqHeaders.setContentType(MediaType.APPLICATION_JSON);

			HttpEntity<String> entity = new HttpEntity<String>(reqBody, reqHeaders);

			// POST REST call to TMaaS
			try {
				ResponseEntity<String> response = restTemplate.postForEntity(cbUtil.getTopoFqdn() + operation_exec_api,
						entity, String.class);

				LOG.info("Request succeeded with response: " + response.getBody());
				ExecResponse execResponse = MAPPER.readValue(response.getBody(), ExecResponse.class);

				retVal = new ConfXMLParam[] {
						new ConfXMLParamValue(PREFIX, "transactionId", new ConfBuf(execResponse.getTransactionId())) };

			} catch (HttpClientErrorException | HttpServerErrorException e) {

				// throw error in case tmaas returns 4xx/5xx
				LOG.error("Request failed with code: " + e.getStatusCode() + ", body: " + e.getResponseBodyAsString());
				throw new DpCallbackException(
						"Topo-Engine returned: " + e.getResponseBodyAsString() + ", Code: " + e.getStatusCode());

			} catch (ResourceAccessException e) {

				// throw IO errors
				LOG.error(e.getMessage(), e);
				throw new DpCallbackException("Unable to reach TMaaS");

			} catch (Exception e) {

				// throw error
				LOG.error(e.getMessage(), e);
				throw new DpCallbackException("Exception: " + e.getMessage());
			}
		} catch (JsonProcessingException e) {

			// throw error
			LOG.error(e.getMessage(), e);
			throw new DpCallbackException("Unable to deserialize request/response to Bean");
		}
		LOG.info("<======= doActionExec retVal=" + (retVal == null ? null : Arrays.toString(retVal)));
		return retVal;
	}

	private static enum Marker {
		BEGIN, END
	}
}
