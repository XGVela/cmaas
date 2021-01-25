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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xgvela.cnf.Constants;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

@Component
public class K8sUtil {

	private static final Logger LOG = LogManager.getLogger(K8sUtil.class);

	private static final String NAMESPACE = String.valueOf(System.getenv("K8S_NAMESPACE"));
	private static final String POD_ID = String.valueOf(System.getenv("K8S_POD_ID"));
	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static Set<String> editableConfigMaps = new HashSet<>();

	@Autowired
	private K8sClient k8sClient;

	public boolean isEditable(ConfigMap cmap) {

		String cmapNamespace = cmap.getMetadata().getNamespace(), cmapName = cmap.getMetadata().getName();

		// ignore kube namespaces
		if (cmapNamespace.startsWith("kube"))
			return false;

		// check in cache
		if (editableConfigMaps.contains(cmapName + "/" + cmapNamespace))
			return true;

		String configMgmt = "disabled";
		try {
			Map<String, String> annotations = cmap.getMetadata().getAnnotations();
			if (annotations.containsKey(Constants.CONFIG_MGMT) && annotations.containsKey(Constants.ANN_TMAAS)) {

				configMgmt = String.valueOf(annotations.getOrDefault(Constants.CONFIG_MGMT, "disabled"));

				if (configMgmt.equals("enabled")) {
					if (cmap.getData() != null && !cmap.getData().isEmpty()) {
						editableConfigMaps.add(cmapName + "/" + cmapNamespace);
						return true;
					}
					LOG.debug("Data in configmap is null.");
					return false;
				}
			}
		} catch (Exception e) {
			LOG.debug("ns: " + cmapNamespace + ", cm: " + cmapName
					+ " - map does not have annotations as part of its metadata or key is incorrect. ");
		}
		return false;
	}

	public void scaleDeployment(String namespace, String microSvcName) {
		LOG.info(Constants.ACTIVITY + Constants.K8S + "Scaling deployment with microservice name labelled : "
				+ microSvcName + ", in namespace: " + namespace);

		KubernetesClient client = k8sClient.getClient();

		List<Deployment> deployList = client.apps().deployments().inNamespace(namespace)
				.withLabel(Constants.MICROSERVICE_LABEL, microSvcName).list().getItems();
		int size = deployList.size();
		if (size > 0) {
			int replicaCount = deployList.get(0).getSpec().getReplicas();

			// scale down to zero replicas
			client.apps().deployments().inNamespace(namespace).withName(microSvcName).scale(0);

			// scale up to initial replicaCount
			client.apps().deployments().inNamespace(namespace).withName(microSvcName).scale(replicaCount);
		} else {
			LOG.info(Constants.ACTIVITY + Constants.K8S + "No deployment found.");
		}
	}

	public void rolloutDeployment(String namespace, String microSvcName, String svcVersion) {
		LOG.info(Constants.ACTIVITY + Constants.K8S + "Rolling out deployment with microservice name labelled: "
				+ microSvcName + " with version " + svcVersion + ", in namespace : " + namespace);

		KubernetesClient client = k8sClient.getClient();

		List<Deployment> deployList = client.apps().deployments().inNamespace(namespace)
				.withLabel(Constants.MICROSERVICE_LABEL, microSvcName).list().getItems();
		int size = deployList.size();
		String svc = "v0";
		if (size > 0) {
			for (int i = 0; i < size; i++) {
				Deployment deploy = deployList.get(i);
				svc = deploy.getMetadata().getAnnotations().getOrDefault(Constants.SVC_VERSION, "v0");

				if (svc.equalsIgnoreCase(svcVersion)) {

					try {
						LOG.info("Triggering rolling restart of deployment: [" + deploy.getMetadata().getName() + "]");
						client.apps().deployments().inNamespace(namespace).withName(deploy.getMetadata().getName())
								.rolling().restart();
					} catch (Exception e) {
						LOG.error(e.getMessage());
						LOG.info("Adding MGMT_TOKEN env to deployment app container to trigger rolling update");
						client.apps().deployments().inNamespace(namespace).withName(deploy.getMetadata().getName())
								.edit().editOrNewSpec().editTemplate().editOrNewSpec().editContainer(0).addNewEnv()
								.withNewName("MGMT_TOKEN").withValue(String.valueOf(System.currentTimeMillis()))
								.endEnv().endContainer().endSpec().endTemplate().endSpec().done();
					}
				}
			}
		} else {
			LOG.info(Constants.ACTIVITY + Constants.K8S + "No deployment found, killing pods instead...");
			getPodNames(namespace, microSvcName, svcVersion).forEach(podName -> killPod(namespace, podName));
		}
	}

	public boolean isCimConfigMapExists(String namespace, String microservice, String svcVersion) {
		String name = microservice + "-cim-" + svcVersion + "-mgmt-cfg";
		String nameExcludingVersion = microservice + "-cim" + "-mgmt-cfg";

		if (k8sClient.getClient().configMaps().inNamespace(namespace).withName(name).get() != null) {
			LOG.debug("ConfigMap: [" + name + "] exists in Namespace: [" + namespace + "]");
			return true;

		} else if (k8sClient.getClient().configMaps().inNamespace(namespace).withName(nameExcludingVersion)
				.get() != null) {
			LOG.debug("ConfigMap: [" + nameExcludingVersion + "] exists in Namespace: [" + namespace + "]");
			return true;

		}
		LOG.debug("ConfigMap: [" + name + "] does not exist in Namespace: [" + namespace + "]");
		return false;
	}

	public void killPod(String namespace, String podName) {

		LOG.info(Constants.ACTIVITY + Constants.K8S + "Killing pod named: " + podName);
		boolean delete = k8sClient.getClient().pods().inNamespace(namespace).withName(podName).delete();

		if (delete) {
			LOG.info(Constants.ACTIVITY + Constants.K8S + "Deletion successful: The pod - " + podName
					+ " - was removed.");
		} else {
			LOG.info(Constants.ACTIVITY + Constants.K8S + "Deletion unsuccessful: The pod - " + podName
					+ " - was not found.");
		}
	}

	private List<Pod> getPods(String namespace, String microSvcName, String svcVersion) {

		try {
			return k8sClient.getClient().pods().inNamespace(namespace)
					.withLabel(Constants.MICROSERVICE_LABEL, microSvcName).list().getItems().stream()
					.filter(pod -> pod.getMetadata().getAnnotations().getOrDefault(Constants.SVC_VERSION, "v0")
							.equals(svcVersion))
					.collect(Collectors.toList());
		} catch (Exception e) {
			LOG.error(e.getMessage());
		}
		return new ArrayList<>();
	}

	public List<String> getAuditablePodNames(String namespace, String microSvcName, String svcVersion) {
		return getPods(namespace, microSvcName, svcVersion).stream().filter(pod -> isReady(pod))
				.map(pod -> pod.getMetadata().getName()).collect(Collectors.toList());
	}

	public List<String> getPodNames(String namespace, String microSvcName, String svcVersion) {
		return getPods(namespace, microSvcName, svcVersion).stream().map(pod -> pod.getMetadata().getName())
				.collect(Collectors.toList());
	}

	private boolean isReady(Pod pod) {
		boolean isReady = false;
		if (pod.getStatus().getContainerStatuses().size() > 0) {
			isReady = true;
			for (ContainerStatus containerStatus : pod.getStatus().getContainerStatuses()) {
				if (!containerStatus.getReady()) {
					isReady = false;
					break;
				}
			}
		}
		return isReady;
	}

	public String getUpdatePolicy(Map<String, String> cmapData, String dataFile) {

		String updatePolicy = Constants.DEFAULT_UPDATE_POLICY;

		// get update policy json list
		String updatePolicyList = null;
		if (cmapData.containsKey(Constants.UPDATE_POLICY_KEY))
			updatePolicyList = cmapData.get(Constants.UPDATE_POLICY_KEY);
		else {
			LOG.debug("Update Policy not found in ConfigMap, defaulting to \"" + Constants.DEFAULT_UPDATE_POLICY
					+ "\" policy");
			return updatePolicy;
		}

		LOG.debug(Constants.UPDATE_POLICY_KEY + " : " + updatePolicyList);

		try {
			JSONObject updatePolicies = new JSONObject(updatePolicyList);

			// get update policy for that data file
			updatePolicy = updatePolicies.getString(dataFile.substring(0, dataFile.indexOf(Constants.JSON)));

		} catch (NullPointerException | JSONException e) {
			LOG.error("Unable to get update policy for " + (dataFile.substring(0, dataFile.indexOf(Constants.JSON)))
					+ ", defaulting to \"" + Constants.DEFAULT_UPDATE_POLICY + "\" policy");
		}
		return updatePolicy;
	}

	public JsonNode getSelfTMaaSDetails() {
		LOG.info("Getting self-tmaaas details-");
		try {
			return MAPPER.readTree(k8sClient.getClient().pods().inNamespace(NAMESPACE).withName(POD_ID).get()
					.getMetadata().getAnnotations().get(Constants.ANN_TMAAS));
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
		return null;
	}

	public String getNfSwVersion(String name) {
		Namespace namespace = getNamespace(name);
		if (namespace != null && namespace.getMetadata() != null && namespace.getMetadata().getAnnotations() != null)
			return namespace.getMetadata().getAnnotations().getOrDefault(Constants.SVC_VERSION, "v0");
		return "v0";
	}

	private Namespace getNamespace(String name) {
		LOG.debug("Getting Namespace: " + name);
		Namespace namespace = null;
		try {
			namespace = k8sClient.getClient().namespaces().withName(name).get();
		} catch (KubernetesClientException e) {
			LOG.error(e.getMessage(), e);
		}
		return namespace;
	}

	public String getServiceName(String microservice) {
		try {
			return k8sClient.getClient().services().inNamespace(NAMESPACE)
					.withLabel(Constants.MICROSERVICE_LABEL, microservice).list().getItems().get(0).getMetadata()
					.getName();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return microservice;
	}

	// remove annotation from configmap
	public void removeAnnotation(String name, String namespace, String key) {
		try {
			LOG.debug("Removing annotation: " + key + " from ConfigMap: " + name + " in namespace: " + namespace);
			k8sClient.getClient().configMaps().inNamespace(namespace).withName(name).edit().editMetadata()
					.removeFromAnnotations(key).endMetadata().done();

		} catch (KubernetesClientException e) {
			LOG.error(e.getMessage(), e);
		}
	}
}
