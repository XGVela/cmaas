#!/bin/bash
# Copyright 2020 Mavenir
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

echo "======== Starting script ========"
ctrlen=`cat /proc/self/cgroup | head -1 | cut -d '/' -f 5 | wc -c`
if [ "$ctrlen" == "65" ];
then
  export K8S_CONTAINER_ID=`cat /proc/self/cgroup | head -1 | cut -d '/' -f 5`
else
  export K8S_CONTAINER_ID=`cat /proc/self/cgroup | head -1 | cut -d '/' -f 4`
fi

mkdir -p /confd/apps/config/model
mkdir -p /confd/apps/logs

echo "CID $K8S_CONTAINER_ID"
echo "======== Starting ConfD ========"
/confd/bin/confd

cd /opt/cmaas/conf

echo "======== Generating server.key ========"
openssl genrsa -out server.key 2048

echo "======== Generating server.csr ========"
if [[ -z "${APP_NAME}" ]]; then
  openssl req -new -key server.key -out server.csr -subj "/CN=cmaas.$K8S_NAMESPACE.svc" -config <(cat /etc/ssl/openssl.cnf | sed "s/RANDFILE\s*=\s*\$ENV::HOME\/\.rnd/#/")
  sed -i -e "s|SERVICE_NAME|cmaas|g" /opt/cmaas/conf/mutating-webhook.yaml
else
  echo "======== RCP platform ========"
  openssl req -new -key server.key -out server.csr -subj "/CN=$APP_NAME-cmaas-np-0.$K8S_NAMESPACE.svc" -config <(cat /etc/ssl/openssl.cnf | sed "s/RANDFILE\s*=\s*\$ENV::HOME\/\.rnd/#/")
  sed -i -e "s|SERVICE_NAME|$APP_NAME-cmaas-np-0|g" /opt/cmaas/conf/mutating-webhook.yaml
fi

echo "======== Generating server.crt ========"
openssl x509 -req -days 3650 -in server.csr -signkey server.key -out server.crt

echo "======== Generating keystore.p12 ========"
openssl pkcs12 -export -in server.crt -inkey server.key -out keystore.p12 -name cmaas -password pass:cmaas

pwd
ls -lart

echo "======== Replacing namespace in webhook file ========"
sed -i -e "s/K8S_NAMESPACE/$K8S_NAMESPACE/g" /opt/cmaas/conf/mutating-webhook.yaml

echo "======== Replacing caBundle in webhook file ========"
export CA_BUNDLE=$(cat server.crt | base64 | tr -d '\n')
sed -i -e "s|CA_BUNDLE|$CA_BUNDLE|g" /opt/cmaas/conf/mutating-webhook.yaml

echo "=================================================================================="
cat /opt/cmaas/conf/mutating-webhook.yaml

echo "======== Starting CMaaS ========"
echo "$JAVA_OPTIONS"
java -XX:+PrintFlagsFinal $JAVA_OPTIONS -jar /opt/cmaas/cmaas-v1.0.jar

echo "======== Exiting container ======="
