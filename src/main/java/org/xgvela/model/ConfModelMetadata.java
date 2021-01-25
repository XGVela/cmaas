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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.util.Utils.RootType;

public class ConfModelMetadata {

	@JsonProperty("data-key")
	@JsonInclude(value = Include.NON_EMPTY)
	String dataKey = Constants.NONE;

	@JsonProperty("yang-namespace")
	@JsonInclude(value = Include.NON_EMPTY)
	String yangNamespace = Constants.NONE;

	@JsonProperty("yang-prefix")
	@JsonInclude(value = Include.NON_EMPTY)
	String prefix = Constants.NONE;

	@JsonProperty("yang-module")
	@JsonInclude(value = Include.NON_EMPTY)
	String moduleName = Constants.NONE;

	@JsonProperty("root-name")
	@JsonInclude(value = Include.NON_EMPTY)
	String rootName = Constants.NONE;

	@JsonProperty("root-type")
	@JsonInclude(value = Include.NON_NULL)
	RootType rootType = null;

	public ConfModelMetadata() {
	}

	public String getRootName() {
		return rootName;
	}

	public void setRootName(String rootName) {
		this.rootName = rootName;
	}

	public RootType getRootType() {
		return rootType;
	}

	public void setRootType(RootType rootType) {
		this.rootType = rootType;
	}

	public String getYangNamespace() {
		return yangNamespace;
	}

	public void setYangNamespace(String yangNamespace) {
		this.yangNamespace = yangNamespace;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public String getModuleName() {
		return moduleName;
	}

	public void setModuleName(String moduleName) {
		this.moduleName = moduleName;
	}

	public String getDataKey() {
		return dataKey;
	}

	public void setDataKey(String dataKey) {
		this.dataKey = dataKey;
	}
}
