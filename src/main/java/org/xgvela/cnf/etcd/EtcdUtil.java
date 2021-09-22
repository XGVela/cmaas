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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.google.common.base.Charsets;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.notification.KeyValueBean;
import org.xgvela.cnf.notification.NotificationUtil;
import org.xgvela.cnf.updateconfig.UpdateConfigHelper;
import org.xgvela.cnf.util.MetricsUtil;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.common.exception.ClosedClientException;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.prometheus.client.Counter;

@Component
public class EtcdUtil {
	private static final Logger LOG = LogManager.getLogger(EtcdUtil.class);

	private static final Counter changeSetPushAttempts = MetricsUtil
			.addCounter("cmaas_audit_changeset_pushed_attempted_total", "No. of change-set pushes attempted");
	private static final Counter changeSetPushFailed = MetricsUtil
			.addCounter("cmaas_audit_changeset_pushed_failure_total", "No. of change-set pushes failed");

	private static String endPoints = "http://" + String.valueOf(System.getenv("ETCD_SVC_FQDN"));

	private static Client etcdClient;

	public void initClient() {
		LOG.debug("Etcd client is null, initializing...");
		try {
			etcdClient = Client.builder().endpoints(endPoints.split(",")).build();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	public Client getClient() {
		if (etcdClient == null) {
			initClient();
		}
		return etcdClient;
	}

	// TODO: consider namespace
	public boolean isNfActive(String nfId) {
		Client client = getClient();
		ByteSequence key = ByteSequence.from(("stateActive/" + nfId).getBytes());

		try {
			GetResponse getResponse = client.getKVClient().get(key).get();
			if (getResponse.getKvs().isEmpty()) {
				LOG.info("Etcd entry not found: stateActive/" + nfId);
				return false;
			}
		} catch (InterruptedException | ExecutionException e) {
			LOG.error(e.getMessage(), e);
		}
		LOG.info("Etcd entry found: stateActive/" + nfId);
		SubscriptionManager.readyNfs.add(nfId);
		return true;
	}

	public void putNfActiveKey(String nfId) {
		Client client = getClient();
		ByteSequence key = ByteSequence.from(("stateActive/" + nfId).getBytes());
		ByteSequence value = ByteSequence.from(("true").getBytes());

		try {
			client.getKVClient().put(key, value).get();
			LOG.info("Network Function state active entry made successfully for key: stateActive/" + nfId);
		} catch (InterruptedException | ExecutionException e) {
			LOG.error(e.getMessage(), e);
		}
	}

	public void putChangeSet(String keyString, String diffNode) {

		Client client = getClient();
		if (client != null) {

			ByteSequence key = ByteSequence.from(keyString.getBytes());
			ByteSequence value = ByteSequence.from(diffNode.getBytes());
			long ttl = 180;
			try {
				ttl = Long.parseLong((String) UpdateConfigHelper.configJson.get("changeSetLease"));
			} catch (ClassCastException e) {
				ttl = ((int) UpdateConfigHelper.configJson.get("changeSetLease"));
			}
			LOG.debug("TTL lease value: " + ttl);
			CompletableFuture<LeaseGrantResponse> lease = client.getLeaseClient().grant(ttl);

			PutOption putOption = null;
			try {
				putOption = PutOption.newBuilder().withLeaseId(lease.get().getID()).build();
				changeSetPushAttempts.inc();
				client.getKVClient().put(key, value, putOption).get();
				LOG.info(Constants.ACTIVITY + Constants.UPDATE + "Successfully pushed change-set: " + keyString
						+ ", Diff:" + diffNode);
				NotificationUtil.sendEvent("CmaasChangeSetPushSuccess", getManagedObjects(keyString, diffNode));

			} catch (ClosedClientException | InterruptedException | ExecutionException e) {
				LOG.error(e.getMessage(), e);
				changeSetPushFailed.inc();
				NotificationUtil.sendEvent("CmaasChangeSetPushFailure", getManagedObjects(keyString, diffNode));
				LOG.info(Constants.ACTIVITY + Constants.UPDATE + "Failed to push change-set: " + keyString + ", Diff:"
						+ diffNode);
			}
		}
	}

	private ArrayList<KeyValueBean> getManagedObjects(String keyString, String diffNode) {
		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		mgdObjs.add(new KeyValueBean("change-set", keyString));
		mgdObjs.add(new KeyValueBean("diff", diffNode));
		return mgdObjs;
	}

	public void removeKey(String key) {
		removeKey(ByteSequence.from(key.getBytes()));
	}

	public void removeKey(ByteSequence key) {
		LOG.info("Removing ETCD key: " + key.toString(Charsets.UTF_8));
		Client etcdClient = getClient();
		KV kvClient = etcdClient.getKVClient();
		DeleteResponse delResp = null;
		try {
			delResp = kvClient.delete(key).get();
		} catch (InterruptedException | ExecutionException e) {
			LOG.error(e.getMessage() + "\n" + e.getCause());
		}
		if (delResp != null && delResp.getDeleted() > 0) {
			LOG.debug("Key deleted successfully.");
		} else if (delResp != null && delResp.getDeleted() == 0)
			LOG.error("Unable to delete key: " + key.toString(Charsets.UTF_8));
		kvClient.close();
	}

	public void putDay1Config(String namespace, String microservice, String config, String... revision) {
		Client etcdClient = getClient();
		String keyString = "config/" + namespace + "/" + microservice;

		if (revision.length > 0) {
			keyString += "/" + revision[0];
		}

		LOG.debug("Putting Day 1 config at: " + keyString);
		ByteSequence key = ByteSequence.from(keyString.getBytes());
		ByteSequence value = ByteSequence.from(config.getBytes());

		try {
			etcdClient.getKVClient().put(key, value).get();
			LOG.info("Successfully put Day 1 config at: " + keyString + ", config:" + config);
		} catch (InterruptedException | ExecutionException e) {
			LOG.error(e.getMessage(), e);
		}
	}

	public void removeDay1Config(String prefix) {
		LOG.info("Removing Day 1 config with prefix: " + prefix);
		Client etcdClient = getClient();
		ByteSequence prefixBs = ByteSequence.from(prefix.getBytes());
		GetOption getOption = GetOption.newBuilder().withPrefix(prefixBs).build();

		try {
			GetResponse getResponse = etcdClient.getKVClient().get(prefixBs, getOption).get();
			if (getResponse.getKvs().size() == 0) {
				LOG.info("Keys already removed or not applicable for NF");
			} else {
				getResponse.getKvs().forEach(kv -> {
					removeKey(kv.getKey());
				});
			}
		} catch (InterruptedException | ExecutionException e) {
			LOG.error(e.getMessage(), e);
		}
	}
}
