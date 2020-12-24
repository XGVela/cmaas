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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.xgvela.cnf.Constants;
import org.xgvela.cnf.netconf.models.CallbackModel;
import org.xgvela.cnf.notification.KeyValueBean;
import org.xgvela.cnf.notification.NotificationUtil;
import org.xgvela.cnf.util.ConfigUtil;
import org.xgvela.cnf.util.MetricsUtil;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfValue;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.DpUserInfo;
import com.tailf.dp.annotations.DataCallback;
import com.tailf.dp.annotations.TransCallback;
import com.tailf.dp.proto.DataCBType;
import com.tailf.dp.proto.TransCBType;

import io.prometheus.client.Counter;

@Component
public class ConfigUpdateCallback {

	private static final Logger LOG = LogManager.getLogger(ConfigUpdateCallback.class);

	private static final String CONFIG_UPDATE = "configUpdate";
	private static final String RESTART_CONFIG_UPDATE = "restartConfigUpdate";

	private static final Counter configDataUpdateAttempts = MetricsUtil
			.addCounter("cmaas_configdata_update_attempts_total", "No. of transactions completed in Netconf");

	public static HashMap<Integer, CopyOnWriteArrayList<CallbackModel>> txToCbModels = new HashMap<>();

	@Autowired
	ConfigUtil configUtil;

	@DataCallback(callPoint = CONFIG_UPDATE, callType = { DataCBType.SET_ELEM })
	public int setElemDynamic(DpTrans trans, ConfObject[] kp, ConfValue newval) {

//		LOG.debug("End Indicator CB: SET_ELEM (predefined)");
		return commonHelper(trans, kp, false);
	}

	@DataCallback(callPoint = CONFIG_UPDATE, callType = { DataCBType.CREATE })
	public int createDynamic(DpTrans trans, ConfObject[] kp) {

//		LOG.debug("End Indicator CB: CREATE (predefined)");
		return commonHelper(trans, kp, false);
	}

	@DataCallback(callPoint = CONFIG_UPDATE, callType = { DataCBType.REMOVE })
	public int deleteDynamic(DpTrans trans, ConfObject[] kp) {

//		LOG.debug("End Indicator CB: REMOVE (predefined)");
		return commonHelper(trans, kp, false);
	}

	@DataCallback(callPoint = RESTART_CONFIG_UPDATE, callType = { DataCBType.SET_ELEM })
	public int setElemRestart(DpTrans trans, ConfObject[] kp, ConfValue newval) {

//		LOG.debug("End Indicator CB: SET_ELEM (restart)");
		return commonHelper(trans, kp, true);
	}

	@DataCallback(callPoint = RESTART_CONFIG_UPDATE, callType = { DataCBType.CREATE })
	public int createRestart(DpTrans trans, ConfObject[] kp) {

//		LOG.debug("End Indicator CB: CREATE (restart)");
		return commonHelper(trans, kp, true);
	}

	@DataCallback(callPoint = RESTART_CONFIG_UPDATE, callType = { DataCBType.REMOVE })
	public int deleteRestart(DpTrans trans, ConfObject[] kp) {

//		LOG.debug("End Indicator CB: REMOVE (restart)");
		return commonHelper(trans, kp, true);
	}

	@TransCallback(callType = { TransCBType.FINISH })
	public void finish(DpTrans trans) throws DpCallbackException {
		LOG.debug("End Indicator CB: FINISH");
		logTransDetails(trans);

		DpUserInfo dpUserInfo = trans.getUserInfo();
		LOG.info(Constants.ACTIVITY + Constants.UPDATE + "User Name: " + dpUserInfo.getUserName() + ", IP: "
				+ dpUserInfo.getIPAddress().toString() + ", Context: " + dpUserInfo.getContext());

		if (dpUserInfo.getIPAddress().toString().equals("127.0.0.1")) {
			LOG.debug("DAY_0 configuration");
			return;
		}

		CopyOnWriteArrayList<CallbackModel> cbModels = txToCbModels.get(trans.getTransaction());
		if (cbModels != null) {
			try {
				configDataUpdateAttempts.inc();
				NotificationUtil.sendEvent("CmaasConfigUpdateReceived", getMgdObjs(cbModels));
				LOG.info(Constants.ACTIVITY + Constants.UPDATE + "Config Update received.");

				LOG.info("List of Callback Models for this transaction -\n" + cbModels.toString());
				configUtil.resolveConfigType(cbModels);

			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}
		}
		cleanup(trans.getTransaction());
	}

	private ArrayList<KeyValueBean> getMgdObjs(List<CallbackModel> cbModels) {
		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		for (int i = 0; i < cbModels.size(); i++)
			mgdObjs.add(new KeyValueBean("namespace-" + (i + 1), cbModels.get(i).getYangNamespace()));
		return mgdObjs;
	}

	private int commonHelper(DpTrans trans, ConfObject[] kp, boolean restartFlag) {
//		logTransDetails(trans);
		ConfTag cTag = getConfTag(kp);
//		logConfTagDetails(cTag);

		transHelper(trans.getTransaction(), cTag.getConfNamespace().toString(), cTag.getPrefix(), restartFlag);
		return Conf.REPLY_OK;
	}

	private void logTransDetails(DpTrans trans) {
		LOG.debug("Transaction: " + trans.getTransaction() + ", Name: " + trans.getName() + ", State: "
				+ trans.getState());
	}

	@SuppressWarnings("unused")
	private void logConfTagDetails(ConfTag cTag) {
		LOG.debug(
				"Prefix: " + cTag.getPrefix() + ", Namespace: " + cTag.getConfNamespace() + ", Tag: " + cTag.getTag());
	}

	private ConfTag getConfTag(ConfObject[] kp) {
//		for (ConfObject i : kp) {
//			LOG.debug("ConfObject: " + i.toString());
//		}
		return (ConfTag) kp[kp.length - 1];
	}

	private void transHelper(int transaction, String yangNs, String yangPrefix, boolean restartFlag) {
//		LOG.debug("Transaction: " + transaction + ", Namespace: " + yangNs + ", Prefix: " + yangPrefix
//				+ " RestartFlag: " + restartFlag);

		CopyOnWriteArrayList<CallbackModel> cbList;

		if (!txToCbModels.containsKey(transaction)) { // transaction is ongoing
			cbList = new CopyOnWriteArrayList<>();

		} else {
			CallbackModel tempCbModel = null;
			cbList = txToCbModels.get(transaction);

			for (CallbackModel cbModel : cbList) {
				if (cbModel.getYangNamespace().equals(yangNs)) { // if namespace is already present
					if (cbModel.isRestart()) {
						restartFlag = true;
					}
					tempCbModel = cbModel; // store and remove existing model
					break;
				}
			}
			if (tempCbModel != null)
				cbList.remove(tempCbModel);
		}
		cbList.add(new CallbackModel(yangNs, yangPrefix, restartFlag));
		txToCbModels.put(transaction, cbList);

//		LOG.debug("Internal map (models/transaction): " + txToCbModels);
	}

	private void cleanup(int trans) {
		txToCbModels.remove(trans);
	}
}
