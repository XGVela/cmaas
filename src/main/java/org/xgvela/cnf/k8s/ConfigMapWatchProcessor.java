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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.netconf.NetconfUtil;
import org.xgvela.cnf.etcd.EtcdUtil;
import org.xgvela.cnf.notification.KeyValueBean;
import org.xgvela.cnf.notification.NotificationUtil;
import org.xgvela.cnf.util.ConfigUtil;
import org.xgvela.cnf.util.MetricsUtil;
import org.xgvela.cnf.util.SubscriptionManager;
import org.xgvela.cnf.util.Utils;
import org.xgvela.cnf.util.Utils.ConfigDatatype;
import org.xgvela.cnf.util.Utils.RootType;
import org.xgvela.model.ConfModelMetadata;
import org.xgvela.model.ConfigMapMetadata;
import org.xgvela.model.ConfigMapEvent;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.Watcher;
import io.prometheus.client.Counter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class ConfigMapWatchProcessor {

	private static final Logger LOG = LogManager.getLogger(ConfigMapWatchProcessor.class);

	private static final Counter onboardAttemptsTotal = MetricsUtil
			.addCounter("cmaas_configmodel_onboard_attempts_total", "Total number of models found");
	private static final Counter onboardFailureTotal = MetricsUtil.addCounter("cmaas_configmodel_onboard_failure_total",
			"Total number of models found");

	private static final String DN_PREFIX = String.valueOf(System.getenv("DN_PREFIX"));
	private static final String NF_READY = "NFMgmtIntfReady";
	private static final String NF_CHANGED = "NFMgmtIntfChanged";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static Map<String, ConfigMapMetadata> cmapPerNfId = new HashMap<>();
	public static AtomicBoolean schemaLoadFlag = new AtomicBoolean();

	public static List<String> configMapNamespace = new ArrayList<>();

	public static BlockingQueue<ConfigMapEvent> queue = new ArrayBlockingQueue<>(500);

	public static boolean nfMgmtIntfFlag = true;
	public static JsonNode selfTmaaSDetails = null;
	public static String selfNfId = "xgvela1";
	public static String selfXGVelaId = "xgvela1";

	public static String meUserLabel;

	@Autowired
	private EtcdUtil etcd;

	@Autowired
	private K8sClient k8sClient;

	@Autowired
	private NetconfUtil netconf;

	@Autowired
	private K8sUtil k8s;

	@Async
	public void start() {
		//consuming messages until exit message is received
		selfTmaaSDetails = k8s.getSelfTMaaSDetails();
		selfNfId = selfTmaaSDetails.get("nfId").asText();
		selfXGVelaId = selfTmaaSDetails.get("xgvelaId").asText();
		meUserLabel = DN_PREFIX + ",ManagedElement=me-" + selfXGVelaId;

		LOG.info("ManagedElement Label: " + meUserLabel + ", Self Nf Id: " + selfNfId);
		while (true) {
			try {
				ConfigMapEvent msg = queue.take();
				LOG.debug("dequeuing: the config map watcher : "+ msg.getConfigMap().getMetadata().getName());
				processEvent(msg.getAction(), msg.getConfigMap());
			} catch (Exception e) {
				LOG.error(e.getMessage(), e.getCause());
			}
		}
	}


	// write yang files and make of list of yangs to compile
	private ConcurrentHashMap<String, String> writeModels(Map<String, String> cmapData, String cmapName,
														  String cmapNamespace, String nfName) {

		ConcurrentHashMap<String, String> compileYangs = new ConcurrentHashMap<>();

		ConcurrentHashMap<String, ConfModelMetadata> mapOfConfModels = Utils.confModelPerConfigmap
				.get(cmapName + "/" + cmapNamespace);

		// filter out yangs
		Predicate<Entry<String, String>> isYang = entry -> entry.getKey().endsWith(Constants.YANG);

		// write yangs
		cmapData.entrySet().stream().filter(isYang).forEach(entry -> {

			String yangFile = entry.getKey();
			String yangModel = entry.getValue();

			LOG.debug("Writing Yang: " + yangFile);
			ConfModelMetadata confModel = mapOfConfModels.get(yangFile);

			String moduleName = confModel.getModuleName();
			yangModel = Utils.updateYangModel(yangModel, confModel, nfName);

			// write file with moduleName
			Utils.writeYang(moduleName, yangModel);
			initLog("Written yang file: " + yangFile + ", ConfigMap: " + cmapName + ", Namespace: "
					+ cmapNamespace);

			compileYangs.put(yangFile, moduleName);
		});

		Utils.findAndReplaceModuleNames(compileYangs, nfName);
		LOG.debug("Yangs To Be Compiled: " + compileYangs.toString());

		return compileYangs;
	}


	// compile the yangs in compiledYangs list
	private void compileModels(ConcurrentHashMap<String, String> compiledYangs, String cmapName,
							   String cmapNamespace, String nfName) {

		ConcurrentHashMap<String, ConfModelMetadata> mapOfConfModels = Utils.confModelPerConfigmap
				.get(cmapName + "/" + cmapNamespace);

		// filter out modules
		Predicate<String> isModule = key -> !mapOfConfModels.get(key).getYangNamespace().equals(Constants.NONE);

		// compile modules
		compiledYangs.entrySet().stream().map(entry -> entry.getKey()).filter(isModule).forEach(yangKey -> {

			LOG.debug("Compiling Yang: " + yangKey);

			String yangFile = Constants.YANG_FILE_PATH + mapOfConfModels.get(yangKey).getModuleName()
					+ Constants.YANG;

			String fxsFile = Constants.FXS_FILE_PATH + mapOfConfModels.get(yangKey).getModuleName()
					+ Constants.FXS;

			onboardAttemptsTotal.inc();
			if (netconf.compileYang(yangFile, fxsFile)) {

				// compile success
				initLog("Successfully Compiled yang file: " + yangFile + " to fxs: " + fxsFile
						+ " in ConfigMap: " + cmapName + " within Namespace: " + cmapNamespace);

			} else {

				// compile failure
				onboardFailureTotal.inc();
				initLog("Error compiling yang file: " + yangKey + " in ConfigMap: " + cmapName
						+ " within Namespace: " + cmapNamespace);

				NotificationUtil.sendEvent("CmaasConfigModelCompileFailure",
						getMgdObjs(yangKey, cmapName, cmapNamespace, nfName));

				compiledYangs.remove(yangKey);
				nfMgmtIntfFlag = false;
			}
		});
		loadSchemas(cmapName, cmapNamespace, nfName);
	}



	private void removeCompiledModels(String cmapName, String cmapNamespace, String nfName) {

		ConcurrentHashMap<String, ConfModelMetadata> mapOfConfModels = Utils.confModelPerConfigmap
				.get(cmapName + "/" + cmapNamespace);

		if (mapOfConfModels != null) {

			List<String> deletedYangNamespaces = new ArrayList<>();
			mapOfConfModels.entrySet().stream()
					.filter(entry -> !entry.getValue().getYangNamespace().equals(Constants.NONE))
					.forEach(entry -> {
						LOG.info("Deleting compiled yang models (fxs) for yang file: " + entry.getKey()
								+ " in ConfigMap: " + cmapName + ", Namespace: " + cmapNamespace);

						Utils.deleteFxs(entry.getValue().getModuleName());

						// add to deletion list for internal map update
						deletedYangNamespaces.add(entry.getValue().getYangNamespace());
					});

			// load schemas
			loadSchemas(cmapName, cmapNamespace, nfName);

			// update internal maps
			Utils.confModelPerConfigmap.remove(cmapName + "/" + cmapNamespace);

			deletedYangNamespaces.forEach(namespace -> {
				LOG.debug("Removing Cmap for Yang namespace: " + namespace);

				// remove yang namespace entry
				Utils.configmapsPerYangNamespace.remove(namespace);
			});
		}
	}


	// loading fxs file
	private void loadSchemas(String cmapName, String cmapNamespace, String nfName) {
		schemaLoadFlag.set(true);
		if (netconf.loadSchemas(cmapName, cmapNamespace, nfName, 1)) {

			// success
			initLog("Schemas loaded successfully.");
		} else {

			// failure
			initLog("Failed to load schemas.");
			NotificationUtil.sendEvent("CmaasSchemasLoadFailure", getMgdObjs(cmapName, cmapNamespace, nfName));
			nfMgmtIntfFlag = false;
		}
		schemaLoadFlag.set(false);
	}

	private static void sleep(long secs) {
		try {
			Thread.sleep(secs * 1000L);
		} catch (InterruptedException e) {
			LOG.error(e.getMessage(), e);
		}
	}

	private static void upgradeLog(String msg) {
		LOG.info(Constants.ACTIVITY + Constants.UPGRADE + msg);
	}

	private void logResponse(ResponseEntity<String> response, String jsonKey, String cmapName) {
		if (response != null) {
			if (response.getStatusCodeValue() == 204) {
				initLog("Config data loaded successfully for key: " + jsonKey + ", ConfigMap: " + cmapName);
			}
		} else {
			initLog("Unable to load for key: " + jsonKey + ", ConfigMap: " + cmapName);
			nfMgmtIntfFlag = false;
		}
	}
	public static void initLog(String msg) {
		LOG.info(Constants.ACTIVITY + Constants.INIT + msg);
	}

	private static ArrayList<KeyValueBean> getMgdObjs(String yangFile, String cmapName, String cmapNamespace,
			String nfId) {

		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		mgdObjs.add(new KeyValueBean("yang-file", yangFile));
		mgdObjs.add(new KeyValueBean("config-map", cmapName));
		mgdObjs.add(new KeyValueBean("namespace", cmapNamespace));
		mgdObjs.add(new KeyValueBean("nf-id", nfId));
		return mgdObjs;
	}

	private static ArrayList<KeyValueBean> getNfAddInfo(String nfName, String nfType, String nfLabel,
			String nfSwVersion, String nfId) {
		ArrayList<KeyValueBean> addInfo = new ArrayList<>();
		addInfo.add(new KeyValueBean("nfName", nfName));
		addInfo.add(new KeyValueBean(Constants.NF_TYPE, nfType));
		addInfo.add(new KeyValueBean("nfLabel", nfLabel));
		addInfo.add(new KeyValueBean("nfSwVersion", nfSwVersion));
		addInfo.add(new KeyValueBean(Constants.NF_ID, nfId));
		return addInfo;
	}

	private static ArrayList<KeyValueBean> getNfStateChange(String changeId, String changeType) {
		ArrayList<KeyValueBean> stateChange = new ArrayList<>();
		stateChange.add(new KeyValueBean("changeIdentifier", changeId));
		stateChange.add(new KeyValueBean("changeType", changeType));
		return stateChange;
	}

	private static ArrayList<KeyValueBean> getMgdObjs(String cmapName, String namespace, String nfId) {
		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		mgdObjs.add(new KeyValueBean("config-map", cmapName));
		mgdObjs.add(new KeyValueBean("namespace", namespace));
		mgdObjs.add(new KeyValueBean("nf-id", nfId));
		return mgdObjs;
	}

	private void loadConfig(ConcurrentHashMap<String, String> compiledYangs, String cmapName,
							String cmapNamespace, Map<String, String> cmapData) {

		LOG.info("Loading data for ConfigMap: " + cmapName + ", Namespace: " + cmapNamespace);

		ConcurrentHashMap<String, ConfModelMetadata> mapOfConfModels = Utils.confModelPerConfigmap
				.get(cmapName + "/" + cmapNamespace);

		// is yang compiled successfully
		final Predicate<Entry<String, ConfModelMetadata>> isCompiled = entry -> compiledYangs
				.containsKey(entry.getKey());

		// is yang root container (does it have valid data key)
		final Predicate<Entry<String, ConfModelMetadata>> isRootYang = entry -> !entry.getValue().getDataKey()
				.equals(Constants.NONE);

		// load data into netconf over REST/NetConf
		final Consumer<Entry<String, ConfModelMetadata>> loadData = entry -> {

			String yangKey = entry.getKey();
			String jsonDataKey = entry.getValue().getDataKey();

			// default with json
			ConfigDatatype datatype = ConfigDatatype.JSON;
			String configData = cmapData.get(jsonDataKey);

			// if xml is present, load it
			if (cmapData.containsKey(Utils.getXmlFromJson(jsonDataKey))) {

				datatype = ConfigDatatype.XML;
				configData = cmapData.get(Utils.getXmlFromJson(jsonDataKey));

				if (!configData.isEmpty()) {
					configData = Utils.addNfIdToXmlns(configData, mapOfConfModels);
				} else {
					LOG.info(Utils.getXmlFromJson(jsonDataKey)
							+ ": XML file is empty, expecting data to be part of Day 1 config");
				}
			} // else load json
			else {

				if (!configData.isEmpty()) {
					configData = Utils.removeModuleName(configData, mapOfConfModels);
				} else {
					LOG.info(jsonDataKey + ": JSON file is empty, expecting data to be part of Day 1 config");
				}
			}

			if (configData.isEmpty())
				return;

			if (datatype.equals(ConfigDatatype.XML)) {

				LOG.info("Making NetConf RPC for Key: [" + Utils.getXmlFromJson(jsonDataKey) + "], ConfigMap: ["
						+ cmapName + "], Namespace: [" + cmapNamespace + "]");

				netconf.push(configData);

			} else {

				if (mapOfConfModels.get(yangKey).getRootType().equals(RootType.LIST)) {
					try {
						String rootName = mapOfConfModels.get(yangKey).getRootName();
						LOG.debug("Root Name: [" + rootName + "]");
						JsonNode elements = MAPPER.readTree(configData).get(rootName);

						if (elements.isArray()) {
							elements.forEach(element -> {
								try {
									ObjectNode node = JsonNodeFactory.instance.objectNode();
									node.putArray(rootName).add(element);
									final String data = MAPPER.writeValueAsString(node);

									logResponse(netconf.push(cmapName, cmapNamespace, yangKey, data,
											ConfigDatatype.JSON), jsonDataKey, cmapName);

								} catch (JsonProcessingException e) {
									LOG.error(e.getMessage(), e);
								}
							});
						} else {
							LOG.error(
									"Wrong Json configuration defined for Yang, root object must be of Array type");
						}
					} catch (IOException | NullPointerException e) {
						LOG.error(e.getMessage(), e);
					}
				} else {
					logResponse(netconf.push(cmapName, cmapNamespace, yangKey, configData, datatype), jsonDataKey,
							cmapName);
				}
			}
		};

		// process stream
		mapOfConfModels.entrySet().stream().filter(isRootYang).filter(isCompiled).forEach(loadData);
	}




	private void processEvent(Watcher.Action action, ConfigMap configMap) throws JsonProcessingException {

		String globalCmapName = configMap.getMetadata().getName();
		String namespace = configMap.getMetadata().getNamespace();

		JsonNode tmaasNode = MAPPER.readTree(configMap.getMetadata().getAnnotations().get(Constants.ANN_TMAAS));

		// only consider configmaps with matching xgvelaId
		if (!selfXGVelaId.equals(tmaasNode.get(Constants.XGVELA_ID).asText())) {
			LOG.debug("xgvelaId does not match for ConfigMap: " + globalCmapName + ", Namespace: " + namespace);
			return;
		}

		String nfName = tmaasNode.get(Constants.NF_ID).asText();
		String nfType = tmaasNode.get(Constants.NF_TYPE).asText();
		String nfLabel = Utils.getNfLabel(nfName);
		String nfUid = Utils.getUUID(nfLabel);

		// cache NF level configmap
		if (configMap.getData().containsKey(Constants.DEPENDENCY_KEY)) {
			cmapPerNfId.put(nfName, new ConfigMapMetadata(namespace, globalCmapName, nfName, Constants.NONE));
		}

		// predicate to filter editable configmaps
		final Predicate<ConfigMap> isEditableConfigMap = cmap -> k8s.isEditable(cmap);

		// predicate to filter configmaps for an NfId
		final Predicate<ConfigMap> isValidConfigMap = cmap -> {
			try {
				JsonNode tmaas = MAPPER.readTree(cmap.getMetadata().getAnnotations().get(Constants.ANN_TMAAS));

				return tmaas.get(Constants.NF_ID).asText().equals(nfName)
						&& tmaas.get(Constants.XGVELA_ID).asText().equals(selfXGVelaId);

			} catch (IOException e) {
				LOG.error(e.getMessage());
			}
			return false;
		};

		// consumer to add configmaps
		final Consumer<ConfigMap> addConfigMap = cmap -> {

			String cmapName = cmap.getMetadata().getName();

			initLog("ADDED ConfigMap: " + cmapName + " Namespace: " + namespace + " NF Id: " + nfName);
			ConfigUtil.loadMetadata(cmap);

			Map<String, String> data = cmap.getData();
			ConcurrentHashMap<String, String> compiledYangs = writeModels(data, cmapName, namespace, nfName);

			if (!compiledYangs.isEmpty()) {
				compileModels(compiledYangs, cmapName, namespace, nfName);
				loadConfig(compiledYangs, cmapName, namespace, data);
			}

			k8s.removeAnnotation(cmapName, namespace, Constants.ANN_INIT);
			configMapNamespace.add(cmapName+"/"+namespace);
		};

		// predicate to filter upgraded configmaps
		final Predicate<ConfigMap> isUpgraded = cmap -> cmap.getMetadata().getAnnotations()
				.containsKey(Constants.ANN_MUTATE);

		switch (action) {

			case ADDED:

				// do not process configmaps which have been added already
				if (configMapNamespace.contains(globalCmapName + "/" + namespace)) {
					LOG.debug("Already processed ConfigMap: " + globalCmapName + "/" + namespace);
					return;
				}

				// default -> NF is ready
				nfMgmtIntfFlag = true;

				// add all editable, valid configmaps for an NfId
				k8sClient.getWatcherClient().configMaps().inNamespace(namespace).list().getItems().stream().filter(isEditableConfigMap)
						.filter(isValidConfigMap).forEach(addConfigMap);

				// send notification
				if (nfMgmtIntfFlag) {

					String nfSwVersion = "v0";
					if (configMap.getMetadata().getAnnotations().containsKey(Constants.NF_VERSION)) {
						nfSwVersion = configMap.getMetadata().getAnnotations().get(Constants.NF_VERSION);
					} else {
						nfSwVersion = k8s.getNfSwVersion(namespace);
					}

					initLog("Onboarding successful for NF " + nfName + ", Namespace: " + namespace
							+ ", NFSwVersion: " + nfSwVersion);

					NotificationUtil.notify(NF_READY, getNfAddInfo(nfName, nfType, nfLabel, nfSwVersion, nfUid),
							getNfStateChange(nfUid, NF_READY), nfUid, nfLabel);
				} else {
					initLog("NF was onboarded with errors in compilation and/or data-load, nfId: " + nfName);
				}

				initLog("ADDED ConfigMap: " + globalCmapName + " Namespace: " + namespace + " NF Id: " + nfName);
				return;

			case MODIFIED:

				LOG.debug(">>> MODIFIED ConfigMap: " + globalCmapName + " Namespace: " + namespace + " NF Id: "
						+ nfName);

				if (!configMap.getMetadata().getAnnotations().containsKey(Constants.ANN_MUTATE))
					return;

				upgradeLog("NF was upgraded !!! Reprocessing all mgmt configmaps, nfId: " + nfName);

				/*
				 * This consumer has 5 processes, invoked on top of filtered list of configmaps
				 * (1) update internal map / write new yangs (2) delete fxs for deleted yangs /
				 * update map (3) remove deleted namespaces from map (4) compile yangs, if any
				 * and load data for newly compiled yangs (5) remove mutate annotation
				 */

				final Consumer<ConfigMap> modifiedConfigMap = cmap -> {

					String cmapName = cmap.getMetadata().getName();

					upgradeLog("Upgrading ConfigMap: [" + cmapName + "] Namespace: [" + namespace + "] NF Id: ["
							+ nfName + "]");

					ConcurrentHashMap<String, ConfModelMetadata> mapOfConfModels = Utils.confModelPerConfigmap
							.get(cmapName + "/" + namespace);

					Map<String, String> data = cmap.getData();
					ConcurrentHashMap<String, String> compiledYangs = new ConcurrentHashMap<>();

					// 1. update map and write new yangs
					upgradeLog("1. Updating map and writing new yangs");
					data.entrySet().stream().filter(
							entry -> Utils.isYang(entry.getKey()) && !mapOfConfModels.containsKey(entry.getKey()))
							.forEach(entry -> {

								String yangFile = entry.getKey();
								String yangModel = entry.getValue();

								// update metadata map
								ConfigUtil.onboardYang(cmapName, namespace, data, yangFile, nfName);

								LOG.debug("Writing Yang: " + yangFile);
								ConfModelMetadata confModel = mapOfConfModels.get(yangFile);

								String moduleName = confModel.getModuleName();
								yangModel = Utils.updateYangModel(yangModel, confModel, nfName);

								// write file with moduleName
								Utils.writeYang(moduleName, yangModel);
								upgradeLog("Written yang file: " + yangFile + ", ConfigMap: " + cmapName
										+ ", Namespace: " + namespace);

								compiledYangs.put(yangFile, moduleName);
							});

					// 2. delete fxs for deleted yangs
					List<String> deletedYangNamespaces = new ArrayList<>();
					List<String> deletedYangFiles = new ArrayList<>();

					upgradeLog("2. Deleting fxs for deleted yangs");
					mapOfConfModels.entrySet().stream().filter(entry -> !data.containsKey(entry.getKey()))
							.forEach(entry -> {

								String yangFile = entry.getKey();
								ConfModelMetadata confModel = entry.getValue();

								upgradeLog("Deleting fxs for yang file: " + yangFile);
								Utils.deleteFxs(confModel.getModuleName());

								deletedYangNamespaces.add(confModel.getYangNamespace());
								deletedYangFiles.add(yangFile);
							});

					// 3.a. delete file entry from internal map
					upgradeLog("3a. Deleting yang file entries from map");
					deletedYangFiles.forEach(yangFile -> {
						LOG.debug("Removing mapping for Yang file: " + yangFile);
						Utils.confModelPerConfigmap.get(cmapName + "/" + namespace).remove(yangFile);
					});

					// 3.b. delete namespace entry from internal map
					upgradeLog("3b. Deleting namespaces from internal map");
					deletedYangNamespaces.forEach(yangNamespace -> {
						LOG.debug("Removing mapping for Yang namespace: " + yangNamespace);
						Utils.configmapsPerYangNamespace.remove(yangNamespace);
					});

					// 4. compile yangs and load data
					if (!compiledYangs.isEmpty()) {
						upgradeLog("4. Compiling yangs and loading data");
						compileModels(compiledYangs, cmapName, namespace, nfName);
						loadConfig(compiledYangs, cmapName, namespace, data);
					}

					// 5. remove mutate annotation
					upgradeLog("5. Removing mutate annotation: " + Constants.ANN_MUTATE);
					k8s.removeAnnotation(cmapName, namespace, Constants.ANN_MUTATE);

					upgradeLog("Finished upgrading ConfigMap: [" + cmapName + "] Namespace: [" + namespace
							+ "] NF Id: [" + nfName + "]");
				};

				/*
				 * sleep to make sure all configmaps have passed through mutator; TODO: revisit
				 */
				sleep(5);

				// collect upgraded configmaps
				List<ConfigMap> configMaps = k8sClient.getWatcherClient().configMaps().inNamespace(namespace).list().getItems().stream()
						.filter(isEditableConfigMap).filter(isValidConfigMap).filter(isUpgraded)
						.collect(Collectors.toList());

				if (configMaps.size() == 0) {
					upgradeLog("NF upgrade already processed, ignoring Watcher modified event");
					return;
				}

				upgradeLog(configMaps.size() + " ConfigMaps found eligible for upgrade");

				// default -> NF is ready
				nfMgmtIntfFlag = true;

				// process
				configMaps.stream().forEach(modifiedConfigMap);

				// send changed notification
				if (nfMgmtIntfFlag) {

					String nfSwVersion = "v0";
					if (configMap.getMetadata().getAnnotations().containsKey(Constants.NF_VERSION)) {
						nfSwVersion = configMap.getMetadata().getAnnotations().get(Constants.NF_VERSION);
					} else {
						nfSwVersion = k8s.getNfSwVersion(namespace);
					}

					upgradeLog("Post-Upgrade onboarding successful for NF " + nfName + ", Namespace: " + namespace
							+ ", NFSwVersion: " + nfSwVersion);

					NotificationUtil.notify(NF_CHANGED, getNfAddInfo(nfName, nfType, nfLabel, nfSwVersion, nfUid),
							getNfStateChange(nfUid, NF_CHANGED), nfUid, nfLabel);

				} else {
					upgradeLog("NF was upgraded with errors in compilation and/or data-load, nfId: " + nfName);
				}

				return;

			case DELETED:
				initLog("DELETE ConfigMap: " + globalCmapName + " Namespace: " + namespace + " NF Id: " + nfName
						+ " removing compiled models, updating internal maps and deleting Etcd day 1 and state entries for NF");

				removeCompiledModels(globalCmapName, namespace, nfName);

				// remove active state key
				etcd.removeKey("stateActive/" + nfName);

				// remove day 1 configs with namespace prefix
				etcd.removeDay1Config("config/" + namespace);

				// remove nf from cache
				SubscriptionManager.readyNfs.remove(nfName);
				cmapPerNfId.remove(nfName);

				// remove cmap from cache
				K8sUtil.editableConfigMaps.remove(globalCmapName + "/" + namespace);
				configMapNamespace.remove(globalCmapName+"/"+namespace);
				initLog("DELETE ConfigMap: " + globalCmapName + " Namespace: " + namespace + " NF Id: " + nfName);
				return;

			case ERROR:
				break;

			default:
				break;
		}
	}
}