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

package org.xgvela.cnf;

public class Constants {
	public static final String TAILF_NAMESPACE = "http://www.tailf.com";
	public static final String EMPTY_STRING = "";

	public static final String DEPENDENCY_KEY = "dependency.json";
	public static final String UPDATE_POLICY_KEY = "updatePolicy.json";
	public static final String REVISION_KEY = "revision";

	public static final String MICROSERVICE_LABEL = "microSvcName";

	public static final String DEFAULT_UPDATE_POLICY = "Restart";
	public static final String DYNAMIC = "Dynamic";
	public static final String RESTART = "Restart";

	public static final String ANN_TMAAS = "xgvela.com/tmaas";
	public static final String ANN_MUTATE = "cmaas.mutate";
	public static final String ANN_INIT = "init";
	public static final String CONFIG_MGMT = "configMgmt";
	public static final String LOAD_CONFIG = "fullConfigOnRestart";
	public static final String SVC_VERSION = "svcVersion";
	public static final String NF_VERSION = "xgvela.com/tmaas.nf.nfVersion";

	public static final String YANG_FILE_PATH = "/netconf/apps/config/model/";
	public static final String FXS_FILE_PATH = "/netconf/etc/netconf/";

	public static final String YANG = ".yang";
	public static final String JSON = ".json";
	public static final String XML = ".xml";
	public static final String FXS = ".fxs";
	public static final String REV = "_rev_";

	public static final String CHANGE_SET_PREFIX = "change-set/";
	public static final String COMMIT_CONFIG_PREFIX = "commit-config/";

	public static final String MAP_LEVEL_NF = "nf";
	public static final String MAP_LEVEL_MS = "ms";

	public static final String ACTIVITY = "ACTIVITY_CMaaS_";

	public static final String UPDATE = "ConfigUpdate: ";
	public static final String INIT = "Initialization: ";
	public static final String DP = "DataProvider: ";
	public static final String WATCH = "Watcher: ";
	public static final String AUDIT = "Auditor: ";
	public static final String K8S = "K8S: ";
	public static final String EVENT = "Notification: ";
	public static final String UPGRADE = "Upgrade: ";
	public static final String NONE = "";

	public static final String ME_ID = "meId";
	public static final String NF_ID = "nfId";
	public static final String NF_TYPE = "nfType";
	public static final String NF_SERVICE_ID = "nfServiceId";
	public static final String NF_SERVICE_TYPE = "nfServiceType";
	public static final String NF_SERVICE_INSTANCE_ID = "nfServiceInstanceId";
	public static final String DN_PREFIX = "dnPrefix";
	public static final String XGVELA_ID = "xgvelaId";
}
