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

package org.xgvela.mutator;

import org.xgvela.cnf.k8s.ConfigMapWatchProcessor;
import org.xgvela.cnf.k8s.K8sClient;
import org.xgvela.cnf.util.Utils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.util.List;

@Component
public class MutatingWebhookInit {

	private static final Logger LOG = LogManager.getLogger(MutatingWebhookInit.class);

	@Autowired
	private K8sClient k8s;

	@Async
	public void init() {
		final String webhookPath = "/opt/cmaas/conf/mutating-webhook.yaml";
		final String replace_xgvelaId = "sed -i \"s|XGVELA_ID|" + ConfigMapWatchProcessor.selfXGVelaId
				+ "|g\" /opt/cmaas/conf/mutating-webhook.yaml";

		LOG.info("Replacing xgvelaId: " + replace_xgvelaId);
		Utils.exec(replace_xgvelaId);

		KubernetesClient client = k8s.getClient();
		try {
			List<HasMetadata> result = client.load(new FileInputStream(webhookPath)).get();
			client.resourceList(result).createOrReplace();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}
}
