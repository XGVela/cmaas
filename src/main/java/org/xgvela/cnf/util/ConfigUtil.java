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


package org.xgvela.cnf.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.netconf.models.CallbackModel;
import org.xgvela.cnf.etcd.EtcdUtil;
import org.xgvela.cnf.k8s.ConfigMapWatchProcessor;
import org.xgvela.cnf.k8s.K8sClient;
import org.xgvela.cnf.k8s.K8sUtil;
import org.xgvela.cnf.notification.KeyValueBean;
import org.xgvela.cnf.notification.NotificationUtil;
import org.xgvela.cnf.util.Utils.ConfigDatatype;
import org.xgvela.cnf.util.Utils.ConfigMode;
import org.xgvela.cnf.util.Utils.RootType;
import org.xgvela.model.ConfModelMetadata;
import org.xgvela.model.ConfigMapMetadata;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.prometheus.client.Counter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ConfigUtil {
	private static final Logger LOG = LogManager.getLogger(ConfigUtil.class);

	private static final Counter configMapUpdateAttempts = MetricsUtil
			.addCounter("cmaas_configmap_update_attempts_total", "Updating ConfigMap on Netconf callback- attempted");
	private static final Counter configMapUpdateFailed = MetricsUtil.addCounter("cmaas_configmap_update_failure_total",
			"Updating ConfigMap on Netconf callback- failed");

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Autowired
	private K8sClient k8sClient;

	@Autowired
	private EtcdUtil etcd;

	@Autowired
	private K8sUtil k8s;

	@Autowired
	private JsonUtil jsonUtil;

	public static void loadMetadata(ConfigMap cmap) {

		String cmapName = cmap.getMetadata().getName();
		String cmapNamespace = cmap.getMetadata().getNamespace();
		try {
			String nfName = MAPPER.readTree(cmap.getMetadata().getAnnotations().get(Constants.ANN_TMAAS))
					.get(Constants.NF_ID).asText();

			Map<String, String> cmapData = cmap.getData();
			LOG.debug("NF ID: " + nfName + ", Cmap Key: " + cmapName + "/" + cmapNamespace + ", Keys: "
					+ cmapData.keySet().toString());

			// stream through only yangs, read model, populate map
			cmapData.keySet().stream().filter(key -> key.endsWith(Constants.YANG))
					.forEach(yangKey -> onboardYang(cmapName, cmapNamespace, cmapData, yangKey, nfName));

			// for easier debugging, printing internal map as pretty json
			LOG.debug("--- ConfModel: \n" + MAPPER.writerWithDefaultPrettyPrinter()
					.writeValueAsString(Utils.confModelPerConfigmap.get(cmapName + "/" + cmapNamespace)));
		} catch (IOException e) {
			LOG.error(e.getMessage());
		}
	}

	public static void onboardYang(String cmapName, String cmapNamespace, Map<String, String> cmapData, String yangKey,
			String nfName) {

		String cmapKey = cmapName + "/" + cmapNamespace;
		LOG.debug("Reading model for Yang key: " + yangKey);

		String configModel = cmapData.get(yangKey);

		boolean isRootYang = false;
		String dataKey = Utils.getJsonFromYang(yangKey);
		if (cmapData.containsKey(dataKey)) {
			LOG.info("Root Yang found: " + yangKey);
			isRootYang = true;
		}

		try {
			// yang_file_name-nfId
			String defaultModuleName = yangKey.substring(0, yangKey.indexOf(Constants.YANG)) + "-" + nfName;

			ConfModelMetadata confModel = parseYang(configModel, nfName, isRootYang, defaultModuleName);
			ConfigMapMetadata cmapMetadata = new ConfigMapMetadata(cmapNamespace, cmapName, nfName, yangKey);

			// set data key if root yang (else, DEFAULT)
			if (isRootYang) {
				cmapMetadata.setDataKey(dataKey);
				confModel.setDataKey(dataKey);
			}

			if (Utils.confModelPerConfigmap.containsKey(cmapKey)) {
				Utils.confModelPerConfigmap.get(cmapKey).put(yangKey, confModel);
			} else {
				ConcurrentHashMap<String, ConfModelMetadata> tempMap = new ConcurrentHashMap<>();
				tempMap.put(yangKey, confModel);
				Utils.confModelPerConfigmap.put(cmapKey, tempMap);
			}

			if (!confModel.getYangNamespace().equals(Constants.NONE))
				Utils.configmapsPerYangNamespace.put(confModel.getYangNamespace(), cmapMetadata);

		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	private static ConfModelMetadata parseYang(String configModel, String nfName, boolean isRootYang,
			String defaultModuleName) {

		ConfModelMetadata confModel = new ConfModelMetadata();

		// for submodule case (no module will be found)
		confModel.setModuleName(defaultModuleName);

		// if not root yang, break after finding namespace/prefix/module
		int limit = 3;
		if (isRootYang)
			limit = 4;

		Scanner scanner = new Scanner(configModel);
		for (int i = 0; i != limit && scanner.hasNextLine();) {
			String line = scanner.nextLine();
			line = line.trim();

			if (line.startsWith("module")) {
				confModel.setModuleName(line.substring(6, line.indexOf("{")).trim() + "-" + nfName);
				i++;
			} else if (line.startsWith("submodule")) {
				confModel.setModuleName(line.substring(9, line.indexOf("{")).trim() + "-" + nfName);
				i++;
			} else if (line.startsWith("namespace")) {
				confModel.setYangNamespace(
						line.substring(9, line.indexOf(";")).trim().replaceAll("\"", "") + ":" + nfName);
				i++;
			} else if (line.startsWith("prefix")) {
				confModel.setPrefix(line.substring(6, line.indexOf(";")).trim().replaceAll("\"", "") + "-" + nfName);
				i++;
			} else if (i == 3 && line.startsWith("list")) {
				LOG.debug("Found root list");
				confModel.setRootName(line.substring(4, line.indexOf("{")).trim());
				confModel.setRootType(RootType.LIST);
				i++;
			} else if (i == 3 && line.startsWith("container")) {
				LOG.debug("Found root container");
				confModel.setRootName(line.substring(9, line.indexOf("{")).trim());
				confModel.setRootType(RootType.CONTAINER);
				i++;
			}
		}
		scanner.close();
		return confModel;
	}

	public void resolveConfigType(List<CallbackModel> updateModels) {

		// <nfId: [config-1, config-2, ...]>
		HashMap<String, List<CallbackModel>> configPerNf = getConfigPerNf(updateModels);

		// iterate through each NF and evaluate whether its day 1 or day 2
		configPerNf.forEach((nfId, cbModels) -> {
			LOG.debug("Evaluating state for NF: " + nfId);

			if (nfId.equals(ConfigMapWatchProcessor.selfNfId) || SubscriptionManager.readyNfs.contains(nfId)
					|| etcd.isNfActive(nfId)) {

				// mark as day 2 config
				LOG.debug("NF is marked active, proceeding with DAY_2 flow");

				cbModels.forEach(cbModel -> {
					try {
						update(cbModel, ConfigMode.DAY_2);
					} catch (JsonProcessingException e) {
						LOG.error(e.getMessage());
					}
				});

			} else {

				// mark as day 1 config
				LOG.debug("NF is not marked active, proceeding with DAY_1 flow");

				// update configmaps with new configuration
				cbModels.forEach(cbModel -> {
					try {
						update(cbModel, ConfigMode.DAY_1);
					} catch (JsonProcessingException e) {
						LOG.error(e.getMessage());
					}
				});

				// put configs in etcd for cim to pick up
				try {
					day1Evaluations(nfId);
				} catch (JsonProcessingException e) {
					LOG.error(e.getMessage());
				}
			}
		});
	}

	private static HashMap<String, List<CallbackModel>> getConfigPerNf(List<CallbackModel> updateModels) {

		// <nfId: [config-1, config-2, ...]>
		HashMap<String, List<CallbackModel>> configPerNf = new HashMap<>();

		// group configurations per NF
		updateModels.forEach(cbModel -> {

			String yangNs = cbModel.getYangNamespace();
			String yangPrefix = cbModel.getYangPrefix();
			LOG.info("Evaluating NF Id for YANG namespace: " + yangNs + ", Prefix: " + yangPrefix + ", RestartFlag: "
					+ cbModel.isRestart());

			String nfId = Utils.configmapsPerYangNamespace.get(yangNs).getNfId();

			List<CallbackModel> tempList;
			if (configPerNf.containsKey(nfId)) {
				tempList = configPerNf.get(nfId);
			} else {
				tempList = new ArrayList<>();
			}
			tempList.add(cbModel);
			configPerNf.put(nfId, tempList);
		});

		try {
			LOG.debug("--- Models/NF map: " + MAPPER.writeValueAsString(configPerNf));
		} catch (JsonProcessingException e) {
			LOG.error(e.getMessage(), e);
		}
		return configPerNf;
	}

	private void update(CallbackModel cbModel, ConfigMode cfgType) throws JsonProcessingException {
		LOG.info("Config Type: " + cfgType);
		KubernetesClient client = k8sClient.getClient();
		LOG.debug("--- ConfigCallbackModel: " + MAPPER.writeValueAsString(cbModel));

		// get configmap and file for that config
		ConfigMapMetadata cmapMeta = Utils.configmapsPerYangNamespace.get(cbModel.getYangNamespace());

		String cmapName = cmapMeta.getConfigmapName();
		String cmapNamespace = cmapMeta.getK8sNamespace();
		String yangFile = cmapMeta.getYangFile();
		LOG.debug("--- ConfigMap Metadata: " + MAPPER.writeValueAsString(cmapMeta));

		// get details of config model
		ConfModelMetadata configMeta = Utils.confModelPerConfigmap.get(cmapName + "/" + cmapNamespace).get(yangFile);
		LOG.debug("--- ConfModel Metadata: " + MAPPER.writeValueAsString(configMeta));

		// get updated config
		String updatedJsonConfig = jsonUtil.getUpdatedConfig(configMeta,
				Utils.confModelPerConfigmap.get(cmapName + "/" + cmapNamespace), ConfigDatatype.JSON,
				cmapMeta.getNfId());

		if (updatedJsonConfig == null) {
			LOG.error("--- Updated Json Config is null, stopping further processing");
			return;
		}

		// update configmap
		String jsonKey = Utils.getJsonFromYang(yangFile);
		updateL("Updating ConfigMap: " + cmapName + ", Namespace: " + cmapNamespace + " Data Key: " + jsonKey
				+ " Config Type: " + cfgType);

		if (cfgType.equals(ConfigMode.DAY_1)) {

			// DAY 1
			try {
				LOG.info("Updating JSON key: " + jsonKey);
				ConfigMap cmap = client.configMaps().inNamespace(cmapNamespace).withName(cmapName).edit()
						.addToData(jsonKey, updatedJsonConfig).done();
				LOG.info("Updated JSON key: " + jsonKey);

				// check whether xml also needs to be updated
				String xmlKey = jsonKey.substring(0, jsonKey.indexOf(Constants.JSON)) + Constants.XML;
				if (cmap.getData().containsKey(xmlKey)) {

					LOG.info("Updating XML key: " + xmlKey);

					// get updated xml config
					String updatedXmlConfig = jsonUtil.getUpdatedConfig(configMeta,
							Utils.confModelPerConfigmap.get(cmapName + "/" + cmapNamespace), ConfigDatatype.XML,
							cmapMeta.getNfId());

					// update configmap with new xml
					client.configMaps().inNamespace(cmapNamespace).withName(cmapName).edit()
							.addToData(xmlKey, updatedXmlConfig).done();

					LOG.info("Updated XML key: " + xmlKey);
				}
				updateL("ConfigMap: " + cmapName + ", in Namespace: " + cmapNamespace
						+ " was updated successfully for Data File: " + jsonKey);

			} catch (Exception e) {
				updateL("Failed to update ConfigMap: " + cmapName + " in Namespace: " + cmapNamespace);
				LOG.error(e.getMessage(), e);
			}
		} else {

			// DAY 2
			JsonNode diffNode = jsonUtil.getJsonDiff(
					client.configMaps().inNamespace(cmapNamespace).withName(cmapName).get().getData().get(jsonKey),
					updatedJsonConfig);

			if (diffNode != null && diffNode.size() != 0) {
				day2Evaluations(cmapName, cmapNamespace, diffNode, jsonKey, updatedJsonConfig, cbModel.isRestart(),
						cmapMeta);
			} else
				LOG.debug("Json Diff is NULL, ConfigMap: " + cmapName + " in Namespace: " + cmapNamespace
						+ " remains unchanged.");
		}
	}

	private void day1Evaluations(String nfId) throws JsonProcessingException {

		// update in etcd @ /config/namespace/microservice : config
		ConfigMapMetadata nfCmapMeta = ConfigMapWatchProcessor.cmapPerNfId.get(nfId);
		if (nfCmapMeta != null) {

			String cmapNamespace = nfCmapMeta.getK8sNamespace();
			String cmapName = nfCmapMeta.getConfigmapName();
			ConfigMap nfCmap = k8sClient.getClient().configMaps().inNamespace(cmapNamespace).withName(cmapName).get();

			LOG.info("Got NF ConfigMap: " + cmapName + ", Namespace: " + cmapNamespace
					+ ", calculating config requirements per microservice");

			// get configs/microservice
			Map<String, Map<String, Object>> configsPerMicroservice = getConfigsPerMicroservice(nfId, nfCmap,
					cmapNamespace);

			String revision = nfCmap.getData().get(Constants.REVISION_KEY);

			if (nfCmap.getMetadata().getAnnotations().containsKey(Constants.LOAD_CONFIG)
					&& nfCmap.getMetadata().getAnnotations().get(Constants.LOAD_CONFIG).equalsIgnoreCase("enabled")) {

				LOG.info("Load config required on restart, including revision key in Day 1 update");

				configsPerMicroservice.forEach((microservice, configs) -> {
					try {
						// remove existing key with prefix
						etcd.removeDay1Config("config/" + cmapNamespace + "/" + microservice + "/");

						// put into etcd including revision value
						etcd.putDay1Config(cmapNamespace, microservice, MAPPER.writeValueAsString(configs), revision);

					} catch (JsonProcessingException e) {
						LOG.error(e.getMessage(), e);
					}
				});

			} else {

				// put into etcd
				configsPerMicroservice.forEach((microservice, configs) -> {
					try {
						etcd.putDay1Config(cmapNamespace, microservice, MAPPER.writeValueAsString(configs));
					} catch (JsonProcessingException e) {
						LOG.error(e.getMessage(), e);
					}
				});
			}
		} else {
			LOG.error("NF level ConfigMap not found for NF Id: " + nfId
					+ ", unable to push NF-level day 1 config for the NF");
		}
	}

	/**
	 *
	 * @param nfId      of NetworkFunction
	 * @param nfCmap    nf-level configmap of the NetworkFunction
	 * @param namespace of NetworkFunction
	 * @return map storing all configs required for each microservice
	 * @throws JsonProcessingException
	 */
	private Map<String, Map<String, Object>> getConfigsPerMicroservice(String nfId, ConfigMap nfCmap, String namespace)
			throws JsonProcessingException {

		String nfCmapName = nfCmap.getMetadata().getName();

		// populate nf-level config
		Map<String, Map<String, Object>> configsPerMicroservice = addNfLevelConfig(nfId, nfCmap);

		// populate ms-level config
		configsPerMicroservice = addMicroserviceLevelConfig(nfId, nfCmapName, namespace, configsPerMicroservice);

		return configsPerMicroservice;
	}

	/**
	 *
	 * @param nfId   of NetworkFunction
	 * @param nfCmap nf-level configmap of the NetworkFunction
	 * @return map storing configs required for each microservice picked from
	 *         nf-level configmap's dependency.json
	 * @throws JsonProcessingException
	 */
	private Map<String, Map<String, Object>> addNfLevelConfig(String nfId, ConfigMap nfCmap)
			throws JsonProcessingException {

		Map<String, Map<String, Object>> configsPerMicroservice = new HashMap<>();
		JSONObject dependencies = new JSONObject(nfCmap.getData().get(Constants.DEPENDENCY_KEY));

		Iterator<String> dependenciesIterator = dependencies.keys();
		while (dependenciesIterator.hasNext()) {
			String config = dependenciesIterator.next();
			LOG.debug("Config Key: " + config);

			JSONArray microservices = dependencies.getJSONArray(config);
			for (int i = 0; i < microservices.length(); i++) {
				String microservice = microservices.getString(i);

				Map<String, Object> configPerDataFile;
				if (configsPerMicroservice.containsKey(microservice)) {
					configPerDataFile = configsPerMicroservice.get(microservice);
				} else {
					configPerDataFile = new HashMap<>();
				}
				try {
					configPerDataFile.put(config + Constants.JSON,
							MAPPER.readTree(nfCmap.getData().get(config + Constants.JSON)));
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
				configsPerMicroservice.put(microservice, configPerDataFile);
			}
		}
		LOG.debug("Calculated config requirements from NF-level configmap:"
				+ MAPPER.writeValueAsString(configsPerMicroservice));

		return configsPerMicroservice;
	}

	/**
	 *
	 * @param nfId                   of NetworkFunction
	 * @param nfCmapName             name of nf-level configmap
	 * @param namespace              of NetworkFunction
	 * @param configsPerMicroservice map storing config derived from nf-level
	 *                               conigmap
	 * @return final configuration for each microservice
	 * @throws JsonProcessingException
	 */
	private Map<String, Map<String, Object>> addMicroserviceLevelConfig(String nfId, String nfCmapName,
			String namespace, Map<String, Map<String, Object>> configsPerMicroservice) throws JsonProcessingException {

		LOG.info("Scanning microservice-level ConfigMaps for namespace: [" + namespace + "], NfId: [" + nfId + "]");
		KubernetesClient client = k8sClient.getClient();

		/*
		 * collect editable configmaps in the namespace for that NF, excluding NF-level
		 * configmap, which is already considered
		 */

		List<ConfigMap> configMaps = client.configMaps().inNamespace(namespace).list().getItems().stream()
				.filter(cm -> !cm.getMetadata().getName().equals(nfCmapName))
				.filter(cm -> K8sUtil.editableConfigMaps.contains(cm.getMetadata().getName() + "/" + namespace))
				.filter(cm -> {
					try {
						JsonNode tmaas = MAPPER.readTree(cm.getMetadata().getAnnotations().get(Constants.ANN_TMAAS));
						if (tmaas.get(Constants.NF_ID).asText().equals(nfId)
								&& tmaas.get(Constants.XGVELA_ID).asText().equals(ConfigMapWatchProcessor.selfXGVelaId))
							return true;
					} catch (JsonProcessingException e) {
						LOG.error(e.getMessage());
					}
					return false;
				}).collect(Collectors.toList());

		if (configMaps.size() == 0) {
			LOG.info("No eligible microservice-level ConfigMaps found for NfId: [" + nfId + "]");
			return configsPerMicroservice;
		}

		/*
		 * add configuration from each microservice to the final map
		 */
		configMaps.forEach(configMap -> {
			String name = configMap.getMetadata().getName();
			String microservice = configMap.getMetadata().getLabels().get(Constants.MICROSERVICE_LABEL);
			LOG.debug("Processing ConfigMap: [" + name + "], Microservice: [" + microservice + "]");

			Map<String, Object> configPerDataFile;
			if (configsPerMicroservice.containsKey(microservice)) {
				configPerDataFile = configsPerMicroservice.get(microservice);
			} else {
				configPerDataFile = new HashMap<>();
			}

			// iterate over data-keys and add to final map
			configMap.getData().keySet().stream().filter(key -> Utils.isValidJsonDataKey(key)).forEach(dataKey -> {
				try {
					configPerDataFile.put(dataKey, MAPPER.readTree(configMap.getData().get(dataKey)));
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
			});
			configsPerMicroservice.put(microservice, configPerDataFile);
		});

		LOG.debug("Calculated final config requirements, making etcd entry for each microservice: "
				+ MAPPER.writeValueAsString(configsPerMicroservice));

		return configsPerMicroservice;
	}

	private void day2Evaluations(String cmapName, String cmapNamespace, JsonNode diffNode, String jsonKey,
			String updatedJsonConfig, boolean restartFlag, ConfigMapMetadata cmapMeta) {

		updateL("ConfigMap: " + cmapName + ", Namespace: " + cmapNamespace + ", NF Id: " + cmapMeta.getNfId());
		KubernetesClient client = k8sClient.getClient();
		try {
			// get configmap
			ConfigMap configMap = client.configMaps().inNamespace(cmapNamespace).withName(cmapName).get();
			Map<String, String> cmapData = configMap.getData();

			String revision = cmapData.get(Constants.REVISION_KEY);
			updateL("Current ConfigMap revision: " + revision);

			// increment revision
			revision = String.valueOf(Integer.parseInt(revision) + 1);
			updateL("Updated ConfigMap revision: " + revision);

			// update configmap
			LOG.info("Updating JSON key: " + jsonKey);
			configMapUpdateAttempts.inc();
			client.configMaps().inNamespace(cmapNamespace).withName(cmapName).edit()
					.addToData(jsonKey, updatedJsonConfig).addToData(Constants.REVISION_KEY, revision).done();
			LOG.info("Updating JSON key: " + jsonKey);

			// check whether xml also needs to be updated
			String xmlKey = jsonKey.substring(0, jsonKey.indexOf(Constants.JSON)) + Constants.XML;
			if (configMap.getData().containsKey(xmlKey)) {

				LOG.info("Updating XML key: " + xmlKey);

				// get updated xml config
				String updatedXmlConfig = jsonUtil.getUpdatedConfig(
						Utils.confModelPerConfigmap.get(cmapName + "/" + cmapNamespace)
								.get(jsonKey.substring(0, jsonKey.indexOf(Constants.JSON)) + Constants.YANG),
						Utils.confModelPerConfigmap.get(cmapName + "/" + cmapNamespace), ConfigDatatype.XML,
						cmapMeta.getNfId());

				// update configmap with new xml
				client.configMaps().inNamespace(cmapNamespace).withName(cmapName).edit()
						.addToData(xmlKey, updatedXmlConfig).done();

				LOG.info("Updated XML key: " + xmlKey);
			}

			// notify and log
			NotificationUtil.sendEvent("CmaasConfigmapUpdateSuccess",
					getMgdObjs(cmapNamespace, cmapName, jsonKey, revision));
			updateL("ConfigMap: " + cmapName + ", in Namespace: " + cmapNamespace + " was updated successfully.");

			// get svcVersion
			String svcVersion = String
					.valueOf(configMap.getMetadata().getAnnotations().getOrDefault(Constants.SVC_VERSION, "v0"));

			// get update policy for data file
			String updatePolicy = resolveUpdatePolicy(restartFlag, cmapData, jsonKey);

			if (cmapData.containsKey(Constants.DEPENDENCY_KEY)) {

				// NF-level map
				JSONArray microservices = jsonUtil.getMicrosvcList(cmapData, jsonKey);
				for (int index = 0; index < microservices.length(); index++) {

					String microservice = String.valueOf(microservices.get(index));
					policyHandler(updatePolicy, cmapNamespace, microservice, diffNode.toString(), revision, jsonKey,
							Constants.MAP_LEVEL_NF, svcVersion);
				}
			} else {

				// Microservice-level map
				String microservice = configMap.getMetadata().getLabels().get(Constants.MICROSERVICE_LABEL);
				policyHandler(updatePolicy, cmapNamespace, microservice, diffNode.toString(), revision, jsonKey,
						Constants.MAP_LEVEL_MS, svcVersion);
			}

			// keep day 1 config up-to-date (for upgrade)
			day1Evaluations(cmapMeta.getNfId());

		} catch (Exception e) {
			configMapUpdateFailed.inc();
			updateL("Failed to update ConfigMap: " + cmapName + " in Namespace: " + cmapNamespace);
			NotificationUtil.sendEvent("CmaasConfigmapUpdateFailure", getMgdObjs(cmapNamespace, cmapName, jsonKey));
			LOG.error(e.getMessage(), e);
		}
	}

	private String resolveUpdatePolicy(boolean restartFlag, Map<String, String> cmapData, String dataFile) {
		if (restartFlag) {
			return Constants.RESTART;
		}
		return k8s.getUpdatePolicy(cmapData, dataFile);
	}

	private void policyHandler(String updatePolicy, String namespace, String microservice, String diffString,
			String updatedRevision, String dataKey, String mapLevel, String svcVersion) {

		updateL("Executing policy for microservice named: " + microservice + ", svcVersion:" + svcVersion);

		if (updatePolicy.equalsIgnoreCase(Constants.DYNAMIC))
			notifyChangeSet(namespace, microservice, diffString, updatedRevision, dataKey, mapLevel, svcVersion);
		else
			k8s.rolloutDeployment(namespace, microservice, svcVersion);
	}

	private void notifyChangeSet(String namespace, String microservice, String diffNode, String revision,
			String dataKey, String mapLevel, String svcVersion) {

		String path = Constants.CHANGE_SET_PREFIX + namespace + "/" + microservice + "/" + svcVersion + "/" + mapLevel
				+ "/" + dataKey + "/" + revision;

		etcd.putChangeSet(path, diffNode);
	}

	private ArrayList<KeyValueBean> getMgdObjs(String k8sNamespace, String configMapName, String dataKey,
			String... revision) {

		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		mgdObjs.add(new KeyValueBean("namespace", k8sNamespace));
		mgdObjs.add(new KeyValueBean("config-map", configMapName));
		mgdObjs.add(new KeyValueBean("data-key", dataKey));
		if (revision.length > 0)
			mgdObjs.add(new KeyValueBean("revision", revision[0]));

		return mgdObjs;
	}

	private void updateL(String msg) {
		LOG.info(Constants.ACTIVITY + Constants.UPDATE + msg);
	}
}