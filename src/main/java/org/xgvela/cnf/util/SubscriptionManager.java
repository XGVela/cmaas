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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.xgvela.cnf.enums.State;
import org.xgvela.cnf.etcd.EtcdUtil;

import io.nats.client.Dispatcher;

@Component
public class SubscriptionManager {

	private static final Logger LOG = LogManager.getLogger(SubscriptionManager.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();

	// cache the names of NFs which have become ready atleast once
	public static Set<String> readyNfs = new HashSet<>();

	@Autowired
	EtcdUtil etcdUtil;

	public void init() {
		final String NF_STATE_CHANGED = "NetworkFunctionStateChanged";
		// subscribe to NATS subject
		subscribe();

		// create request body for REST subscription request to CIM
		ObjectNode reqNode = JsonNodeFactory.instance.objectNode();
		reqNode.putArray("localEventName").add(NF_STATE_CHANGED);

		String requestBody = null;
		try {
			requestBody = MAPPER.writeValueAsString(reqNode);
		} catch (JsonProcessingException e) {
			LOG.error(e.getMessage(), e);
		}

		// set header and make request
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> entity = new HttpEntity<String>(requestBody, headers);

		String url = "http://localhost:6060/api/v1/_operations/event/subscriptions";
		LOG.info("Sending Subscription Request to URL: " + url + " with body: " + requestBody);

		try {
			RestTemplate restTemplate = new RestTemplate();
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
			LOG.debug("Request succeeded with CIM response: " + response.getBody());
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			LOG.error("Request failed with code: " + e.getStatusCode() + ", body: " + e.getResponseBodyAsString());
		}
	}

	private void subscribe() {
		final String subject = "EVENT-NOTIFICATION";

		// start dispatcher
		Dispatcher dispatcher = NatsUtil.getConnection().createDispatcher(message -> {
			String data = new String(message.getData());
			LOG.debug("Message received from NATS: " + data);
			try {
				JsonNode notificationFields = MAPPER.readTree(data).get("event").get("notificationFields");

				String nfName = notificationFields.get("additionalFields").get("nfName").asText();
				State newState = State.fromValue(notificationFields.get("newState").asText());
				LOG.info("NetworkFunctionName: " + nfName + ", State: " + newState);

				if (State.INSTANTIATED_CONFIGURED_ACTIVE.equals(newState) && !readyNfs.contains(nfName)) {
					LOG.debug("Caching Id of ready NF: " + nfName);
					readyNfs.add(nfName);
					etcdUtil.putNfActiveKey(nfName);
				}
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			} catch (NullPointerException e) {
				LOG.error(e.getMessage(), e);
			}
		});
		dispatcher.subscribe(subject);
		LOG.info("Subscribed to NATS subject: " + subject);
	}
}
