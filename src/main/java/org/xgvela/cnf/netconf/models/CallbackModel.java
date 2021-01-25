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

package org.xgvela.cnf.netconf.models;

public class CallbackModel {

	private String yangNamespace;
	private String yangPrefix;
	private boolean restart;

	public String getYangNamespace() {
		return yangNamespace;
	}

	public void setYangNamespace(String yangNamespace) {
		this.yangNamespace = yangNamespace;
	}

	public String getYangPrefix() {
		return yangPrefix;
	}

	public void setYangPrefix(String yangPrefix) {
		this.yangPrefix = yangPrefix;
	}

	public boolean isRestart() {
		return restart;
	}

	public void setRestart(boolean restart) {
		this.restart = restart;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (restart ? 1231 : 1237);
		result = prime * result + ((yangNamespace == null) ? 0 : yangNamespace.hashCode());
		result = prime * result + ((yangPrefix == null) ? 0 : yangPrefix.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CallbackModel other = (CallbackModel) obj;
		if (restart != other.restart)
			return false;
		if (yangNamespace == null) {
			if (other.yangNamespace != null)
				return false;
		} else if (!yangNamespace.equals(other.yangNamespace))
			return false;
		if (yangPrefix == null) {
			if (other.yangPrefix != null)
				return false;
		} else if (!yangPrefix.equals(other.yangPrefix))
			return false;
		return true;
	}

	public CallbackModel(String yangNamespace, String yangPrefix, boolean restart) {
		super();
		this.yangNamespace = yangNamespace;
		this.yangPrefix = yangPrefix;
		this.restart = restart;
	}

	@Override
	public String toString() {
		return "CallbackModel [namespace=" + yangNamespace + ", prefix=" + yangPrefix + ", restart=" + restart + "]";
	}
}
