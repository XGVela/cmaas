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

package org.xgvela.cnf.tmaas.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.xgvela.cnf.enums.Invocation;
import org.xgvela.cnf.enums.OperationState;
import org.xgvela.cnf.enums.OperationStatus;

public class OperationRecord {

	@JsonProperty("nfID")
	private String nfID;

	@JsonProperty("nfsID")
	private String nfsID;

	@JsonProperty("nfName")
	private String nfName;

	@JsonProperty("nfsName")
	private String nfsName;

	@JsonProperty("transactionId")
	private String transactionID;

	@JsonProperty("parentTransactionId")
	private String parentTransactionID;

	@JsonProperty("operationId")
	private String operationId;

	@JsonProperty("target")
	private String target;

	@JsonProperty("request")
	private String request;

	@JsonProperty("response")
	private String response;

	@JsonProperty("createTime")
	private String createTime;

	@JsonProperty("updateTime")
	private String updateTime;

	@JsonProperty("schedule")
	private String schedule;

	@JsonProperty("state")
	private OperationState operationState;

	@JsonProperty("status")
	private OperationStatus operationStatus;

	@JsonProperty("invocationMode")
	private Invocation invocation;

	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

	public String getNfID() {
		return nfID;
	}

	public void setNfID(String nfID) {
		this.nfID = nfID;
	}

	public String getNfsID() {
		return nfsID;
	}

	public void setNfsID(String nfsID) {
		this.nfsID = nfsID;
	}

	public String getNfName() {
		return nfName;
	}

	public void setNfName(String nfName) {
		this.nfName = nfName;
	}

	public String getNfsName() {
		return nfsName;
	}

	public void setNfsName(String nfsName) {
		this.nfsName = nfsName;
	}

	public String getTransactionID() {
		return transactionID;
	}

	public void setTransactionID(String transactionID) {
		this.transactionID = transactionID;
	}

	public String getParentTransactionID() {
		return parentTransactionID;
	}

	public void setParentTransactionID(String parentTransactionID) {
		this.parentTransactionID = parentTransactionID;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public String getRequest() {
		return request;
	}

	public void setRequest(String request) {
		this.request = request;
	}

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}

	public String getCreateTime() {
		return createTime;
	}

	public void setCreateTime(String createTime) {
		this.createTime = createTime;
	}

	public String getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(String updateTime) {
		this.updateTime = updateTime;
	}

	public String getSchedule() {
		return schedule;
	}

	public void setSchedule(String schedule) {
		this.schedule = schedule;
	}

	public OperationState getOperationState() {
		return operationState;
	}

	public void setOperationState(OperationState operationState) {
		this.operationState = operationState;
	}

	public OperationStatus getOperationStatus() {
		return operationStatus;
	}

	public void setOperationStatus(OperationStatus operationStatus) {
		this.operationStatus = operationStatus;
	}

	public Invocation getInvocation() {
		return invocation;
	}

	public void setInvocation(Invocation invocation) {
		this.invocation = invocation;
	}

	public String getOperationId() {
		return operationId;
	}

	public void setOperationId(String operationId) {
		this.operationId = operationId;
	}
}
