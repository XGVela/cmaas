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

package org.xgvela.cnf.updateconfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.notification.KeyValueBean;
import org.xgvela.cnf.notification.NotificationUtil;
import org.xgvela.cnf.util.MetricsUtil;
import org.xgvela.cnf.util.NatsUtil;

import io.fabric8.zjsonpatch.JsonPatch;
import io.nats.client.Connection;
import io.prometheus.client.Counter;

@Component
public class UpdateConfigHelper {
	private static Logger LOG = LogManager.getLogger(UpdateConfigHelper.class);
	public static JSONObject jsonObjectFile;
	public static JSONObject configJson;
	private static final Counter cmaasSelfConfigUpdateSuccessTotal = MetricsUtil.addCounter(
			"cmaas_self_config_update_success_total", "The number of times CMaaS config updated successfully");
	private static final Counter cmaasSelfConfigUpdateFailureTotal = MetricsUtil.addCounter(
			"cmaas_self_config_update_failure_total", "The number of times CMaaS config failed to get updated");

	public void publishToCim(UpdateConfigBean updateConfigBean, Boolean done) {
		JSONObject json = new JSONObject();
		json.put("change-set-key", updateConfigBean.getChangeSetKey());
		json.put("revision", updateConfigBean.getRevision());
		if (done) {
			cmaasSelfConfigUpdateSuccessTotal.inc();
			json.put("status", "success");
			json.put("remarks", "Successfully applied the config");
			try {
				NotificationUtil.sendEvent("CmaasSelfConfigUpdateSuccess", getreturnMgdObjs(json));
				LOG.info(Constants.ACTIVITY + Constants.INIT + "CMaaS config updated Successfully ");
			} catch (Exception e1) {
				LOG.error(e1.getMessage(), e1 + "Error occured while raising CMAAS_CONFIG_UPDATE_SUCCESS event");
			}
		} else {
			cmaasSelfConfigUpdateFailureTotal.inc();
			json.put("status", "failure");
			json.put("remarks", "Failed to apply the config");
			try {
				NotificationUtil.sendEvent("CmaasSelfConfigUpdateFailure", getreturnMgdObjs(json));
				LOG.info(Constants.ACTIVITY + Constants.INIT + "CMaaS config update Failed ");
			} catch (Exception e1) {
				LOG.error("Error occured while raising CMAAS_CONFIG_UPDATE_SUCCESS event", e1);
			}

		}
		LOG.debug("Config response for CIM:  " + json.toString());
		Connection natsConnection = NatsUtil.getConnection();
		try {
			natsConnection.publish("CONFIG", json.toString().getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			LOG.error("Error occured while publishing to NATS", e);
		}
		LOG.info("Published response to CIM");
	}

	public Boolean updateCmaasConfig(String diff) throws IOException {
		LOG.debug("Updating configJson object");
		ObjectMapper mapper = new ObjectMapper();
		JsonNode patchObject = mapper.readTree(diff);
		JsonNode jsonObject = mapper.readTree(jsonObjectFile.toString());
		LOG.debug("jsonObject: " + jsonObject);
		LOG.debug("jsonDiff: " + patchObject);
		jsonObject = JsonPatch.apply(patchObject, jsonObject);
		jsonObjectFile = new JSONObject(jsonObject.toString());
		configJson = (JSONObject) jsonObjectFile.get("config");
		LOG.info("Updated configJson successfully with updated value: " + configJson.toString());
		LOG.debug("Applying new config");
		updateLogLevel(configJson.get("logLevel"));
		LOG.info("Applied new config successfully, exiting updateCmaasConfig");
		return true;
	}

	public void updateLogLevel(Object object) {
		Configurator.setAllLevels("org.xgvela", Level.toLevel(object.toString()));
		LOG.info("Log level Changed to " + object);
	}

	public static void initCmaasConfig() {
		try {
			LOG.info("Initialising CMaaS configuration");
			File file = new File("/cmaasconfig/cmaasconfig.json");
			InputStream inputStream = new FileInputStream(file);
			JSONTokener tokener = new JSONTokener(inputStream);
			JSONObject obj = new JSONObject(tokener);
			LOG.debug("object parsed from file: " + obj.toString());
			jsonObjectFile = (JSONObject) obj;
			configJson = (JSONObject) jsonObjectFile.get("config");
			LOG.debug("configJson: " + configJson.toString());
			Configurator.setAllLevels("org.xgvela",
					Level.toLevel(((String) configJson.get("logLevel")).toLowerCase()));
			LOG.info("Completed initialising CMaaS configuration");

		} catch (IOException e) {
			LOG.error("Error occured while parsing config from file", e);
		}
	}

	public ArrayList<KeyValueBean> getAttemptMgdObjs(UpdateConfigBean updateConfigBean) {
		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		mgdObjs.add(new KeyValueBean("change-set-key", updateConfigBean.getChangeSetKey()));
		mgdObjs.add(new KeyValueBean("config-patch", updateConfigBean.getConfigPatch()));
		mgdObjs.add(new KeyValueBean("change-set-key", updateConfigBean.getChangeSetKey()));
		mgdObjs.add(new KeyValueBean("revision", updateConfigBean.getRevision()));
		return mgdObjs;
	}

	public ArrayList<KeyValueBean> getreturnMgdObjs(JSONObject json) {
		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		mgdObjs.add(new KeyValueBean("change-set-key", (String) json.get("change-set-key")));
		mgdObjs.add(new KeyValueBean("revision", (String) json.get("revision")));
		mgdObjs.add(new KeyValueBean("status", (String) json.get("status")));
		mgdObjs.add(new KeyValueBean("remarks", (String) json.get("remarks")));
		return mgdObjs;
	}

}