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

package org.xgvela.cnf.etcd;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.google.common.base.Charsets;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.k8s.K8sUtil;
import org.xgvela.cnf.notification.KeyValueBean;
import org.xgvela.cnf.notification.NotificationUtil;
import org.xgvela.cnf.util.MetricsUtil;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.Watch.Listener;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import io.prometheus.client.Counter;

/*
 * Change-set key format:
 * change-set/<namespace>/<microsvcname>/<svc-version>/<maplevel>/<datakey>/<revision>
 * Class audits if all the changes from netconf are committed correctly.
 */

@Component
public class ConfigAuditor {

	private static final Logger LOG = LogManager.getLogger(ConfigAuditor.class);

	private static final Counter commitConfigSuccessTotal = MetricsUtil
			.addCounter("cmaas_appln_commit_config_success_total", "The number of audits that succeeded");
	private static final Counter commitConfigFailureTotal = MetricsUtil
			.addCounter("cmaas_appln_commit_config_failure_total", "The number of audits that failed");

	@Autowired
	EtcdUtil etcdUtil;

	@Autowired
	K8sUtil k8sUtil;

	/**
	 * @return Method to keep watch on change-set prefix in Etcd and performs
	 *         auditing operation depending on event type
	 */

	@Async
	public void init() {
		Client etcdClient = etcdUtil.getClient();
		LOG.info(Constants.ACTIVITY + Constants.AUDIT
				+ "Auditor set-up, watching on \"change-set\"  and \"commit-config\" prefix");

		ByteSequence prefix = ByteSequence.from(("change-set/").getBytes());
		WatchOption watchOp = WatchOption.newBuilder().withPrefix(prefix).build();
		Watch watchClient = etcdClient.getWatchClient();

		watchCommitKey(watchClient);

		try {
			watchClient.watch(prefix, watchOp, new Listener() {

				@Override
				public void onNext(WatchResponse response) {

					for (WatchEvent event : response.getEvents()) {
						try {
							String type = event.getEventType().toString();
							String key = Optional.ofNullable(event.getKeyValue().getKey())
									.map(bs -> bs.toString(Charsets.UTF_8)).orElse("");

							LOG.debug("Event executed- Type: " + type + ", Key: " + key + ", Revision: "
									+ (key.substring(key.lastIndexOf("/") + 1)));

							if (type.equals("DELETE")) {
								auditL("Event executed- Type: " + type + ", Key: " + key + ", Revision: "
										+ (key.substring(key.lastIndexOf("/") + 1)));
								audit(etcdClient, key);
							}
						} catch (Exception e) {
							LOG.error(e.getMessage(), e);
						}
					}
				}

				@Override
				public void onError(Throwable throwable) {
					LOG.error("Watcher error - " + throwable.getMessage() + " " + throwable.getCause());
				}

				@Override
				public void onCompleted() {
					auditL("Watcher Completed.");
				}
			});
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	private void watchCommitKey(Watch watchClient) {
		ByteSequence prefixCommit = ByteSequence.from(("commit-config/").getBytes());
		WatchOption watchOpCommit = WatchOption.newBuilder().withPrefix(prefixCommit).build();
		try {
			watchClient.watch(prefixCommit, watchOpCommit, new Listener() {

				@Override
				public void onNext(WatchResponse response) {
					for (WatchEvent event : response.getEvents()) {

						try {
							String type = event.getEventType().toString();
							String key = Optional.ofNullable(event.getKeyValue().getKey())
									.map(bs -> bs.toString(Charsets.UTF_8)).orElse("");
							String revision = Optional.ofNullable(event.getKeyValue().getValue())
									.map(bs -> bs.toString(Charsets.UTF_8)).orElse("");
							LOG.debug("Event executed- Type: " + type + ", Key: " + key + ", Revision: " + revision);

							if (type.equals("PUT") && revision.equalsIgnoreCase("-1")) {
								auditL("Event executed- Type: " + type + ", Key: " + key + ", Revision: " + revision);

								String changeSetKeyList[] = key.split("/");
								String namespace = changeSetKeyList[1];
								String microSvcName = changeSetKeyList[2];
								String svcVersion = changeSetKeyList[3];
								String podName = key.substring(key.lastIndexOf("/") + 1);

								commitConfigFailureTotal.inc();
								auditL("Audit failed for Pod: " + podName + ", version " + svcVersion + ", Namespace: "
										+ namespace);
								NotificationUtil.sendEvent("CmaasAuditFailure",
										getManagedObj(namespace, microSvcName, podName, "-1", revision));

								k8sUtil.killPod(namespace, podName);
							}
						} catch (Exception e) {
							LOG.error(e.getMessage(), e);
						}
					}
				}

				@Override
				public void onError(Throwable throwable) {
					LOG.error("Watcher error - " + throwable.getMessage() + " " + throwable.getCause());
				}

				@Override
				public void onCompleted() {
					auditL("Watcher Completed.");
				}
			});

		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	/**
	 * @param client etcd client
	 * @param key    [change-set/namespace/microservice/svc-version/map-level/data-key/revision]
	 * @return Method to check if commit config is successful for all pods, if not
	 *         then deleting them
	 */

	private void audit(Client client, String key) {

		String changeSetKeyList[] = key.split("/");
		String namespace = changeSetKeyList[1];
		String microSvcName = changeSetKeyList[2];
		String svcVersion = changeSetKeyList[3];
		String mapLevel = changeSetKeyList[4];
		String dataKey = changeSetKeyList[5];
		String cmapRevision = changeSetKeyList[6];

		String commitConfig = Constants.COMMIT_CONFIG_PREFIX + namespace + "/" + microSvcName + "/" + svcVersion + "/"
				+ mapLevel;

		boolean cimConfigMapExists = k8sUtil.isCimConfigMapExists(namespace, microSvcName, svcVersion);

		if (cimConfigMapExists && dataKey.equals("cim.json")) {
			commitConfig += "/cim";
		}

		// get auditable pods
		List<String> auditablePodNames = k8sUtil.getAuditablePodNames(namespace, microSvcName, svcVersion);
		LOG.info("Auditable pods are - " + auditablePodNames.toString());

		// get all pods of microservice
		List<String> allPodNames = k8sUtil.getPodNames(namespace, microSvcName, svcVersion);

		// prepare prefix
		ByteSequence prefix = ByteSequence.from(commitConfig.getBytes());

		// max retry count = 5
		for (int i = 1; i <= 5; i++) {

			try {
				auditL("Retrieving commit-config keys with prefix: " + commitConfig + ", DataKey: " + dataKey
						+ ", TryCounter: " + i);

				GetResponse getResponse = client.getKVClient()
						.get(prefix, GetOption.newBuilder().withPrefix(prefix).build()).get();

				getResponse.getKvs().forEach(kv -> {

					String commitKey = kv.getKey().toString(Charsets.UTF_8);

					// skip if cim is creating its own configmap and its not a cim config update
					if (cimConfigMapExists && !dataKey.equals("cim.json") && commitKey.contains("/cim/")) {
						LOG.debug("Skipping audit for key: [" + commitKey + "]");
						return;
					}

					String podName = commitKey.substring(commitKey.lastIndexOf("/") + 1);
					String commitRevision = Optional.ofNullable(kv.getValue()).map(val -> val.toString(Charsets.UTF_8))
							.orElse(Constants.EMPTY_STRING);

					if (auditablePodNames.contains(podName)) {

						auditL("Found Commit-Config Key: " + commitKey + ", Revision: " + commitRevision);

						if (commitRevision.equals("-1")) {

							// immediate failure case
							auditL("Found commit key with commit revision as -1, already handled this audit failure for Pod: "
									+ podName + " with version: " + svcVersion + ", Namespace: " + namespace);

						} else if (commitRevision.equals("-2")) {

							// bypass/ignore/unused config case
							auditL("Found commit key with commit revision as -2, config marked as unused/ignored by application Pod: "
									+ podName + " with version: " + svcVersion + ", Namespace: " + namespace);

						} else if (Integer.parseInt(commitRevision) < Integer.parseInt(cmapRevision)) {

							// failure case
							commitConfigFailureTotal.inc();
							auditL("Audit failed for Pod: " + podName + ", version: " + svcVersion + ", Namespace: "
									+ namespace);
							NotificationUtil.sendEvent("CmaasAuditFailure",
									getManagedObj(namespace, microSvcName, podName, cmapRevision, commitRevision));

							k8sUtil.killPod(namespace, podName);

						} else {

							// success case
							commitConfigSuccessTotal.inc();
							auditL("Audit succeeded for Pod: " + podName + " with version: " + svcVersion
									+ ", Namespace: " + namespace);
						}

						// remove from list of live pods, i.e. pod is up to date.
						auditablePodNames.remove(podName);

						// remove commit-config key of that pod-id from Etcd, only if revision matches
						if (Integer.parseInt(commitRevision) == Integer.parseInt(cmapRevision)) {
							etcdUtil.removeKey(kv.getKey());
						}

					} else if (!allPodNames.contains(podName)) {

						// old pod entry
						LOG.debug("Removing stale commit-config entry from Etcd: " + commitKey + ", Revision: "
								+ commitRevision);
						etcdUtil.removeKey(kv.getKey());
					}
				});

				deleteIfRevisionNotFound(namespace, auditablePodNames, microSvcName, cmapRevision, "not-found");
				auditablePodNames.clear();
				allPodNames.clear();
				break;

			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}
		}
	}

	/**
	 * @param namespace      namespace
	 * @param podNames       list of pods in that namespace
	 * @param microSvcName   label value
	 * @param cmapRevision   Cmap revision
	 * @param commitRevision Commit revision
	 *
	 * @return Method to kill pods for whom change-set key was not found
	 */

	private void deleteIfRevisionNotFound(String namespace, List<String> podNames, String microSvcName,
			String cmapRevision, String commitRevision) {

		LOG.info("Deleting active pods for which no commit-config was found");
		podNames.forEach(podName -> {

			commitConfigFailureTotal.inc();
			auditL("Audit failed for Pod: " + podName + ", Namespace: " + namespace);
			NotificationUtil.sendEvent("CmaasAuditFailure",
					getManagedObj(namespace, microSvcName, podName, cmapRevision, commitRevision));

			k8sUtil.killPod(namespace, podName);
		});
	}

	private ArrayList<KeyValueBean> getManagedObj(String namespace, String microSvcName, String podName,
			String cmapRevision, String commitRevision) {

		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		mgdObjs.add(new KeyValueBean("namespace", namespace));
		mgdObjs.add(new KeyValueBean("microservice", microSvcName));
		mgdObjs.add(new KeyValueBean("pod", podName));
		mgdObjs.add(new KeyValueBean("cmap-revision", cmapRevision));
		mgdObjs.add(new KeyValueBean("commit-revision", commitRevision));
		return mgdObjs;
	}

	private void auditL(String log) {
		LOG.info(Constants.ACTIVITY + Constants.AUDIT + log);
	}
}
