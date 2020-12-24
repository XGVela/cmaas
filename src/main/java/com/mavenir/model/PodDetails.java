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

package org.xgvela.model;

import io.fabric8.kubernetes.client.Watcher.Action;

public class PodDetails {

	private Action action;
	private String podName;
	private String namespace;
	private String nfName;
	private String nfType;
	private String nfServiceName;
	private String nfServiceType;

	@Override
	public String toString() {
		return "PodDetails [action=" + action + ", podName=" + podName + ", namespace=" + namespace + ", nfName="
				+ nfName + ", nfType=" + nfType + ", nfServiceName=" + nfServiceName + ", nfServiceType="
				+ nfServiceType + "]";
	}

	public PodDetails() {
	}

	public PodDetails(Action action, String podName, String namespace, String nfName, String nfType,
			String nfServiceName, String nfServiceType) {
		super();
		this.action = action;
		this.podName = podName;
		this.namespace = namespace;
		this.nfName = nfName;
		this.nfType = nfType;
		this.nfServiceName = nfServiceName;
		this.nfServiceType = nfServiceType;
	}

	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}

	public String getPodName() {
		return podName;
	}

	public void setPodName(String podName) {
		this.podName = podName;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getNfName() {
		return nfName;
	}

	public void setNfName(String nfName) {
		this.nfName = nfName;
	}

	public String getNfType() {
		return nfType;
	}

	public void setNfType(String nfType) {
		this.nfType = nfType;
	}

	public String getNfServiceName() {
		return nfServiceName;
	}

	public void setNfServiceName(String nfServiceName) {
		this.nfServiceName = nfServiceName;
	}

	public String getNfServiceType() {
		return nfServiceType;
	}

	public void setNfServiceType(String nfServiceType) {
		this.nfServiceType = nfServiceType;
	}

}
