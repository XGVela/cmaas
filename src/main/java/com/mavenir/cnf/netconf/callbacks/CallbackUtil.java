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

package org.xgvela.cnf.netconf.callbacks;

import java.util.Base64;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.xgvela.cnf.k8s.K8sUtil;
import com.tailf.conf.ConfObject;
import com.tailf.dp.DpTrans;

@Component
public class CallbackUtil {

	private static final Logger LOG = LogManager.getLogger(CallbackUtil.class);

	@Autowired
	private K8sUtil k8sUtil;

	private static String TopoFqdn = null;

	// singleton method for topo-engine fqdn
	public String getTopoFqdn() {
		if (TopoFqdn == null) {
			TopoFqdn = "http://" + k8sUtil.getServiceName("topo-engine") + "." + System.getenv("K8S_NAMESPACE")
					+ ".svc.cluster.local:8080";
			LOG.debug("Topo-Engine FQDN = " + TopoFqdn);
		}
		return TopoFqdn;
	}

	public void logKp(ConfObject[] kp) {
		LOG.debug("=> KeyPath length: " + kp.length);
		for (ConfObject confObject : kp)
			LOG.debug("===> " + confObject.toString());
	}

	public void logTxn(DpTrans txn) {
		/*
		 * LOG.debug("Dev no.=" + txn.getDevNo() + ", Opaque=" + txn.getOpaque() +
		 * ", DB name=" + txn.getDBName() + ", Secondary index=" +
		 * txn.getSecondaryIndex() + ", Mode=" + txn.getMode());
		 */
	}

	public String encodeBase64(String spec) {
		return Base64.getEncoder().encodeToString(spec.getBytes());
	}
}
