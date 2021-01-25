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

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.notification.NotificationUtil;
import org.xgvela.cnf.util.MetricsUtil;

import io.prometheus.client.Counter;

@RestController
public class UpdateConfigController {
	private static Logger LOG = LogManager.getLogger(UpdateConfigController.class);
	private static final Counter cmaasSelfConfigUpdateattemptTotal = MetricsUtil
			.addCounter("cmaas_self_config_update_attempt_total", "The number of times CMaas config update attempted");

	@Autowired
	UpdateConfigHelper updateConfigHelper;

	@PostMapping(path = "/updateConfig")
	@ResponseStatus(HttpStatus.OK)
	public @ResponseBody StatusBean updateConfig(@RequestBody JsonNode diffNode) throws IOException {
		cmaasSelfConfigUpdateattemptTotal.inc();
		LOG.debug("Recieved request for config update");

		UpdateConfigBean updateConfigBean = new UpdateConfigBean();
		updateConfigBean.setChangeSetKey(diffNode.get("change-set-key").asText());
		updateConfigBean.setConfigPatch(diffNode.get("config-patch").asText());
		updateConfigBean.setDataKey(diffNode.get("data-key").asText());
		updateConfigBean.setRevision(diffNode.get("revision").asText());
		try {
			NotificationUtil.sendEvent("CmaasSelfConfigUpdateAttempted",
					updateConfigHelper.getAttemptMgdObjs(updateConfigBean));
			LOG.info(Constants.ACTIVITY + Constants.INIT + "CMaaS config update attempt starts ");
		} catch (Exception e1) {
			LOG.error(e1.getMessage(), e1 + "Error occured while raising CMAAS_CONFIG_UPDATE_ATTEMPTED event");
		}
		LOG.info("Json object recieved from  cim:" + diffNode.toString());
		Boolean done = false;
		if (updateConfigBean.getConfigPatch() != null) {
			done = updateConfigHelper.updateCmaasConfig(updateConfigBean.getConfigPatch());
		} else {
			LOG.error("config-patch is null, check diffnode provided by CIM");
		}
		StatusBean statusBean = new StatusBean();
		if (done) {
			statusBean.setStatus("Success");
			statusBean.setMessage("Successfully Updated Config");
		} else {
			statusBean.setStatus("Failure");
			statusBean.setMessage("Failed to update config");
		}
		updateConfigHelper.publishToCim(updateConfigBean, done);
		LOG.debug("Config update request completed");
		return statusBean;

	}
}
