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

package org.xgvela.cnf.enums;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum State {

	@JsonEnumDefaultValue
	NULL("NULL"), READY("READY"), NOT_READY("NOT_READY"), INSTANTIATED_NOT_CONFIGURED("INSTANTIATED_NOT_CONFIGURED"),
	INSTANTIATED_CONFIGURED_INACTIVE("INSTANTIATED_CONFIGURED_INACTIVE"),
	INSTANTIATED_CONFIGURED_ACTIVE("INSTANTIATED_CONFIGURED_ACTIVE"), TERMINATED("TERMINATED");

	private final String value;

	private final static Map<String, State> CONSTANTS = new HashMap<String, State>();

	static {
		for (State c : values()) {
			CONSTANTS.put(c.value, c);
		}
	}

	private State(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return this.value;
	}

	@JsonValue
	public String value() {
		return this.value;
	}

	@JsonCreator
	public static State fromValue(String value) {
		State constant = CONSTANTS.get(value);
		if (constant == null) {
			throw new IllegalArgumentException(value);
		} else {
			return constant;
		}
	}
}
