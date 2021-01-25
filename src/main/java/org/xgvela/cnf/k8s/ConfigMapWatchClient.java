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

import org.xgvela.cnf.Constants;
import org.xgvela.cnf.updateconfig.UpdateConfigHelper;
import org.xgvela.model.ConfigMapEvent;
import org.xgvela.mutator.MutatingWebhookInit;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

@Component
public class ConfigMapWatchClient {

    private static final Logger LOG = LogManager.getLogger(ConfigMapWatchClient.class);

    public static CountDownLatch configMapWatcher = new CountDownLatch(1);

    @Autowired
    private K8sClient k8sClient;

    @Autowired
    private K8sUtil k8s;

    @Autowired
    private MutatingWebhookInit mutator;

    @Autowired
    ConfigMapWatchProcessor cMapProcessor;

    @Async
    public void init()  {
        UpdateConfigHelper.initCmaasConfig();
        // async
        LOG.info("Starting Mutator");
        mutator.init();

        LOG.info(Constants.ACTIVITY + Constants.WATCH + "Initiating discovery of ConfigMaps-");
        cMapProcessor.start();

        while (true) {
            configMapWatcher = new CountDownLatch(1);
            KubernetesClient client = k8sClient.getWatcherClient();
            Watch watch = client.configMaps().inAnyNamespace().watch((new Watcher<ConfigMap>() {

                @Override
                public void eventReceived(Action action, ConfigMap configMap) {
                    try {
                        if (k8s.isEditable(configMap)) {
                            LOG.debug("enqueuing: configmap : "+ configMap.getMetadata().getName());
                            ConfigMapWatchProcessor.queue.add(new ConfigMapEvent(action, configMap));
                        }
                    } catch (Exception e) {
                        ConfigMapWatchProcessor.initLog("Error while processing ConfigMap: " + configMap.getMetadata().getName() + " in Namespace: "
                                + configMap.getMetadata().getNamespace());
                        LOG.error(e.getMessage(), e);
                    }
                }


                @Override
                public void onClose(KubernetesClientException cause) {
                    if (cause != null)
                        LOG.error(cause.getMessage(), cause);

                    client.close();
                    configMapWatcher.countDown();
                }
            }));

            try {
                configMapWatcher.await();
            } catch (InterruptedException e) {
                LOG.error(e.getMessage(), e);
            }

            if (watch != null) {
                try {
                    watch.close();
                } catch (Exception e) {
                    //Need to see the behaviour for closing the watch for now just logging the error
                    LOG.error(e.getMessage(), e);
                }
            }
        }
    }
}
