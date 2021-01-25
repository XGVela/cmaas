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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.xgvela.cnf.enums.State;

public class TopologyTree {

	@JsonProperty("name")
	private String name;

	@JsonProperty("state")
	private State state;

	@JsonIgnore
	private Map<String, ManagedElement> elem = new HashMap<>();

	@JsonProperty("managed_elements")
	private List<ManagedElement> elemList = new ArrayList<>();

	public void refreshList() {
		this.elemList.clear();
		this.elemList.addAll(this.elem.values());
	}
}
