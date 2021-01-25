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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.xgvela.cnf.Constants;

public class ConfigMapMetadata {

	@JsonProperty("yang-file")
	private String yangFile = Constants.NONE;

	@JsonProperty("data-key")
	@JsonInclude(value = Include.NON_EMPTY)
	private String dataKey = Constants.NONE;

	@JsonProperty("namespace")
	private String k8sNamespace = Constants.NONE;

	@JsonProperty("name")
	private String configmapName = Constants.NONE;

	@JsonProperty("nf-id")
	private String nfId = Constants.NONE;

	public ConfigMapMetadata(String k8sNamespace, String configmapName, String nfId, String yangFile) {
		this.k8sNamespace = k8sNamespace;
		this.configmapName = configmapName;
		this.nfId = nfId;
		this.yangFile = yangFile;
	}

	public ConfigMapMetadata() {
	}

	public String getK8sNamespace() {
		return k8sNamespace;
	}

	public void setK8sNamespace(String k8sNamespace) {
		this.k8sNamespace = k8sNamespace;
	}

	public String getConfigmapName() {
		return configmapName;
	}

	public void setConfigmapName(String configmapName) {
		this.configmapName = configmapName;
	}

	public String getNfId() {
		return nfId;
	}

	public void setNfId(String nfId) {
		this.nfId = nfId;
	}

	public String getYangFile() {
		return yangFile;
	}

	public void setYangFile(String yangFile) {
		this.yangFile = yangFile;
	}

	public String getDataKey() {
		return dataKey;
	}

	public void setDataKey(String dataKey) {
		this.dataKey = dataKey;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConfigMapMetadata other = (ConfigMapMetadata) obj;
		if (configmapName == null) {
			if (other.configmapName != null)
				return false;
		} else if (!configmapName.equals(other.configmapName))
			return false;
		if (k8sNamespace == null) {
			if (other.k8sNamespace != null)
				return false;
		} else if (!k8sNamespace.equals(other.k8sNamespace))
			return false;
		if (nfId == null) {
			if (other.nfId != null)
				return false;
		} else if (!nfId.equals(other.nfId))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((configmapName == null) ? 0 : configmapName.hashCode());
		result = prime * result + ((k8sNamespace == null) ? 0 : k8sNamespace.hashCode());
		result = prime * result + ((nfId == null) ? 0 : nfId.hashCode());
		return result;
	}
}
