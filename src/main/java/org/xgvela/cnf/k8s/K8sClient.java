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

package org.xgvela.cnf.k8s;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

@Component
public class K8sClient {

	private static final Logger LOG = LogManager.getLogger(K8sClient.class);

	private static String URL = "https://" + String.valueOf(System.getenv("K8S_SVC_FQDN"));

	private static KubernetesClient client;
	private static KubernetesClient watcherClient;

	private void newClient() {
		LOG.info("Initializing Kubernetes client with URL: " + URL);
		client = new DefaultKubernetesClient(URL);
	}

	public KubernetesClient getClient() {
		if (client == null) {
			newClient();
		}
		return client;
	}

	private void newWatcherClient() {
		LOG.info("Initializing Kubernetes client with URL: " + URL);
		watcherClient = new DefaultKubernetesClient(URL);
	}

	public KubernetesClient getWatcherClient() {
		if (watcherClient == null) {
			newWatcherClient();
		}
		return watcherClient;
	}
}
