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

public class NetworkFunction {

	@JsonProperty("id")
	private String id;

	@JsonProperty("name")
	private String name = null;

	@JsonProperty("userLabel")
	private String userLabel;

	@JsonProperty("nfType")
	private String nfType;

	@JsonProperty("state")
	private State state = State.NULL;

	@JsonProperty("nfSwVersion")
	private String swVersion = "";

	@JsonProperty("administrativeState")
	private String administrativeState = "UNLOCKED";

	@JsonProperty("operationalState")
	private String operationalState = "ENABLED";

	@JsonProperty("usageState")
	private String usageState = "ACTIVE";

	@JsonProperty("operations")
	private Operations operation;

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

	@JsonIgnore
	private String namespace = null;

	@JsonIgnore
	private Map<String, NFService> elem = new HashMap<>();

	@JsonProperty("nf_services")
	private List<NFService> elemList = new ArrayList<>();

	public List<NFService> getElemList() {
		return elemList;
	}

	public void setElemList(List<NFService> elemList) {
		this.elemList = elemList;
	}

	public void refreshList() {
		this.elemList.clear();
		this.elemList.addAll(this.elem.values());
	}

	@JsonIgnore
	private int instanceCount = 0;

	@JsonIgnore
	private int instantiatedConfActive;

	@JsonIgnore
	private int instantiatedNotConf;

	@JsonIgnore
	private ManagedElement parent = null;

	public NetworkFunction() {
	}

	public String getUserLabel() {
		return userLabel;
	}

	public void setUserLabel(String userLabel) {
		this.userLabel = userLabel;
	}

	public String getSwVersion() {
		return swVersion;
	}

	public void setSwVersion(String swVersion) {
		this.swVersion = swVersion;
	}

	public String getNfType() {
		return nfType;
	}

	public void setNfType(String nfType) {
		this.nfType = nfType;
	}

	public String getAdministrativeState() {
		return administrativeState;
	}

	public void setAdministrativeState(String administrativeState) {
		this.administrativeState = administrativeState;
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

	public Operations getOperation() {
		return operation;
	}

	public void setOperation(Operations operation) {
		this.operation = operation;
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

	public void setState(State state) {
		this.state = state;
	}

	public boolean has(String id) {
		return this.elem.containsKey(id);
	}

	public NFService get(String id) {
		return this.elem.get(id);
	}

	public Map<String, NFService> getElem() {
		return elem;
	}

	public void setElem(Map<String, NFService> elem) {
		this.elem = elem;
	}

	public void addElem(String key, NFService value) {
		this.elem.put(key, value);
	}

	public void removeElem(String key) {
		this.elem.remove(key);
	}

	public int getInstanceCount() {
		return instanceCount;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public void setInstanceCount(int instanceCount) {
		this.instanceCount = instanceCount;
	}

	public int getInstantiatedConfActive() {
		return instantiatedConfActive;
	}

	public void setInstantiatedConfActive(int instantiatedConfActive) {
		this.instantiatedConfActive = instantiatedConfActive;
	}

	public int getInstantiatedNotConf() {
		return instantiatedNotConf;
	}

	public void setInstantiatedNotConf(int instantiatedNotConf) {
		this.instantiatedNotConf = instantiatedNotConf;
	}

	public ManagedElement getParent() {
		return parent;
	}

	public void setParent(ManagedElement parent) {
		this.parent = parent;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<String, String> getExtendedAttrs() {
		return extendedAttrs;
	}

	public void setExtendedAttrs(Map<String, String> extendedAttrs) {
		this.extendedAttrs = extendedAttrs;
	}
}