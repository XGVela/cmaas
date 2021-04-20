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

import org.xgvela.cnf.netconf.DataProvider;
import org.xgvela.cnf.etcd.ConfigAuditor;
import org.xgvela.cnf.etcd.EtcdUtil;
import org.xgvela.cnf.k8s.ConfigMapWatchClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.CountDownLatch;

@Component
public class InitializationBean {

	private static final Logger LOG = LogManager.getLogger(InitializationBean.class);

	public static final CountDownLatch registrationLatch = new CountDownLatch(1);

	@Autowired
	private SubscriptionManager subscription;

	@Autowired
	private DataProvider provider;

	@Autowired
	private ConfigAuditor auditor;

	@Autowired
	private ConfigMapWatchClient watcher;

	@Autowired
	private EtcdUtil etcdUtil;

	@Autowired
	private MetricsUtil metrics;

	@PostConstruct
	public void init() throws InterruptedException {

		registrationLatch.await();

		LOG.info("Initializing cmaas...");

		// sync
		etcdUtil.initClient();

		// sync
		LOG.info("Executing subscription services");
		subscription.init();

		// async
		LOG.info("Starting HTTP exporter");
		metrics.startHTTPExporter();

		// async
		LOG.info("Starting Netconf Data Provider");
		provider.init();

		// async
		LOG.info("Starting Auditor thread");
		auditor.init();

		// async
		LOG.info("Starting ConfigMap watcher");
		watcher.init();

		LOG.info("Successfully initialized cmaas");
	}
}