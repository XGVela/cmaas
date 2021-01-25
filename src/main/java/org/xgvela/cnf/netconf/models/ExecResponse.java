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

public class ExecResponse {

	String transactionId;
	OperationResponseHeaders headers;
	String body;
	String error;

	public ExecResponse(String transactionId, OperationResponseHeaders headers, String body, String error) {
		this.transactionId = transactionId;
		this.headers = headers;
		this.body = body;
		this.error = error;
	}

	public ExecResponse() {
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public OperationResponseHeaders getHeaders() {
		return headers;
	}

	public void setHeaders(OperationResponseHeaders headers) {
		this.headers = headers;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}
}