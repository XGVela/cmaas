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

package org.xgvela.model;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.Watcher;

public class ConfigMapEvent {

    private Watcher.Action action;
    private ConfigMap configMap;

    public ConfigMapEvent(Watcher.Action action, ConfigMap configMap) {
        this.action = action;
        this.configMap = configMap;
    }

    public Watcher.Action getAction() {
        return action;
    }

    public void setAction(Watcher.Action action) {
        this.action = action;
    }

    public ConfigMap getConfigMap() {
        return configMap;
    }

    public void setConfigMap(ConfigMap configMap) {
        this.configMap = configMap;
    }
}