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
import org.xgvela.model.PodDetails;

public class NFServiceInstance {

	@JsonProperty("id")
	private String id;

	@JsonProperty("name")
	private String name = null;

	@JsonProperty("dnPrefix")
	private String dnPrefix;

	@JsonProperty("userLabel")
	private String userLabel;

	@JsonProperty("state")
	private State state = State.NULL;

	@JsonProperty("haRole")
	private String haRole = null;

	@JsonProperty("msuid")
	private String msUid = null;

	@JsonProperty("network-status")
	private List<PodNetworksStatus> nws = new ArrayList<>();

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
	private PodDetails podDetails = null;

	@JsonIgnore
	private NFService parent;

	public NFServiceInstance() {
	}

	public String getDnPrefix() {
		return dnPrefix;
	}

	public void setDnPrefix(String dnPrefix) {
		this.dnPrefix = dnPrefix;
	}

	public String getUserLabel() {
		return userLabel;
	}

	public void setUserLabel(String userLabel) {
		this.userLabel = userLabel;
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

	public NFService getParent() {
		return parent;
	}

	public void setParent(NFService parent) {
		this.parent = parent;
	}

	public String getHaRole() {
		return haRole;
	}

	public void setHaRole(String haRole) {
		this.haRole = haRole;
	}

	public String getMsUid() {
		return msUid;
	}

	public void setMsUid(String msUid) {
		this.msUid = msUid;
	}

	public PodDetails getPodDetails() {
		return podDetails;
	}

	public void setPodDetails(PodDetails podDetails) {
		this.podDetails = podDetails;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<PodNetworksStatus> getNws() {
		return nws;
	}

	public void setNws(List<PodNetworksStatus> nws) {
		this.nws = nws;
	}

	public Map<String, String> getExtendedAttrs() {
		return extendedAttrs;
	}

	public void setExtendedAttrs(Map<String, String> extendedAttrs) {
		this.extendedAttrs = extendedAttrs;
	}
}
