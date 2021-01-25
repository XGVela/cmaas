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

import com.fasterxml.jackson.annotation.JsonProperty;

public class RegisterRequest {

	public RegisterRequest() {
	}

	@JsonProperty("container_name")
	private String containerName;

	@JsonProperty("container_id")
	private String containerId;

	public String getContainerName() {
		return containerName;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	public String getContainerId() {
		return containerId;
	}

	public void setContainerId(String containerId) {
		this.containerId = containerId;
	}

	public RegisterRequest(String containerName, String containerId) {
		this.containerName = containerName;
		this.containerId = containerId;
	}

}
