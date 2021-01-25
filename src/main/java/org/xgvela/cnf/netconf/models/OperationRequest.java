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

package org.xgvela.cnf.netconf.models;

import java.util.ArrayList;
import java.util.List;

public class OperationRequest {

	String operationId;
	List<OperationRequestParameter> parameters = new ArrayList<>();
	String body = null;
	String nfId;
	String nfServiceId;

	public OperationRequest(String operationId, List<OperationRequestParameter> parameters, String body, String nfId,
			String nfServiceId) {
		this.operationId = operationId;
		this.parameters = parameters;
		this.body = body;
		this.nfId = nfId;
		this.nfServiceId = nfServiceId;
	}

	public OperationRequest() {
		// TODO Auto-generated constructor stub
	}

	public String getOperationId() {
		return operationId;
	}

	public void setOperationId(String operationId) {
		this.operationId = operationId;
	}

	public List<OperationRequestParameter> getParameters() {
		return parameters;
	}

	public void setParameters(List<OperationRequestParameter> parameters) {
		this.parameters = parameters;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getNfId() {
		return nfId;
	}

	public void setNfId(String nfId) {
		this.nfId = nfId;
	}

	public String getNfServiceId() {
		return nfServiceId;
	}

	public void setNfServiceId(String nfServiceId) {
		this.nfServiceId = nfServiceId;
	}
}
