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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.xgvela.cnf.enums.State;

public class NFService {

	@JsonProperty("id")
	private String id;

	@JsonProperty("name")
	private String name = null;

	@JsonIgnore
	private String namespace = null;

	@JsonProperty("userLabel")
	private String userLabel;

	@JsonProperty("nfServiceType")
	private String nfServiceType;

	@JsonProperty("state")
	private State state = State.INSTANTIATED_NOT_CONFIGURED;

	@JsonProperty("nfServiceSwVersion")
	private String swVersion = "";

	@JsonProperty("adminstrativeState")
	private String adminstrativeState = "UNLOCKED";

	@JsonProperty("operationalState")
	private String operationalState = "ENABLED";

	@JsonProperty("usageState")
	private String usageState = "ACTIVE";

	@JsonProperty("operations")
	private Operations serviceOperation = new Operations();

	@JsonProperty("haEnabled")
	private boolean haEnabled = false;

	@JsonProperty("numStandby")
	private int numStandby = 0;

	@JsonProperty("monitoringMode")
	private String monitoringMode = "";

	@JsonProperty("mode")
	private String mode = "";

	@JsonIgnore
	private String kind = "ReplicaSet";

	@JsonIgnore
	private Map<String, NFServiceInstance> elem = new HashMap<>();

	@JsonProperty("nf_service_instances")
	private List<NFServiceInstance> elemList = new ArrayList<>();

	@JsonProperty("extendedAttrs")
	private Map<String, String> extendedAttrs;

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

	public List<NFServiceInstance> getElemList() {
		return elemList;
	}

	public void setElemList(List<NFServiceInstance> elemList) {
		this.elemList = elemList;
	}

	@JsonIgnore
	private String k8sUid = null;

	public void refreshList() {
		this.elemList.clear();
		this.elemList.addAll(this.elem.values());
	}

	@JsonIgnore
	private int instanceCount;

	@JsonIgnore
	private int readyCount;

	@JsonIgnore
	private int notReadyCount;

	@JsonIgnore
	private int nullCount;

	@JsonIgnore
	private int activeReadyCount;

	@JsonIgnore
	private NetworkFunction parent = null;

	public NFService() {
	}

	public String getSwVersion() {
		return swVersion;
	}

	public void setSwVersion(String swVersion) {
		this.swVersion = swVersion;
	}

	public int getNumStandby() {
		return numStandby;
	}

	public void setNumStandby(int numStandby) {
		this.numStandby = numStandby;
	}

	public String getUserLabel() {
		return userLabel;
	}

	public void setUserLabel(String userLabel) {
		this.userLabel = userLabel;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public boolean isHaEnabled() {
		return haEnabled;
	}

	public void setHaEnabled(boolean haEnabled) {
		this.haEnabled = haEnabled;
	}

	public String getNfServiceType() {
		return nfServiceType;
	}

	public Operations getServiceOperation() {
		return serviceOperation;
	}

	public void setServiceOperation(Operations serviceOperation) {
		this.serviceOperation = serviceOperation;
	}

	public void setNfServiceType(String nfServiceType) {
		this.nfServiceType = nfServiceType;
	}

	public String getAdminstrativeState() {
		return adminstrativeState;
	}

	public void setAdminstrativeState(String adminstrativeState) {
		this.adminstrativeState = adminstrativeState;
	}

	public String getOperationalState() {
		return operationalState;
	}

	public void setOperationalState(String operationalState) {
		this.operationalState = operationalState;
	}

	public String getUsageState() {
		return usageState;
	}

	public void setUsageState(String usageState) {
		this.usageState = usageState;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public State getState() {
		return state;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getK8sUid() {
		return k8sUid;
	}

	public void setK8sUid(String k8sUid) {
		this.k8sUid = k8sUid;
	}

	public String getMonitoringMode() {
		return monitoringMode;
	}

	public void setMonitoringMode(String monitoringMode) {
		this.monitoringMode = monitoringMode;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public void setState(State state) {
		this.state = state;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public Map<String, NFServiceInstance> getElem() {
		return elem;
	}

	public boolean has(String id) {
		return this.elem.containsKey(id);
	}

	public NFServiceInstance get(String id) {
		return this.elem.get(id);
	}

	public void setElem(Map<String, NFServiceInstance> elem) {
		this.elem = elem;
	}

	public void addElem(String key, NFServiceInstance value) {
		this.elem.put(key, value);
	}

	public void removeElem(String key) {
		this.elem.remove(key);
	}

	public int getInstanceCount() {
		return instanceCount;
	}

	public void setInstanceCount(int instanceCount) {
		this.instanceCount = instanceCount;
	}

	public NetworkFunction getParent() {
		return parent;
	}

	public void setParent(NetworkFunction parent) {
		this.parent = parent;
	}

	public Map<String, String> getExtendedAttrs() {
		return extendedAttrs;
	}

	public void setExtendedAttrs(Map<String, String> extendedAttrs) {
		this.extendedAttrs = extendedAttrs;
	}
}