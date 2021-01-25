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

package org.xgvela.cnf.notification;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.flatbuffers.FlatBufferBuilder;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.util.NatsUtil;

import io.nats.client.Connection;

public class NotificationUtil {

	private static final Logger LOG = LogManager.getLogger(NotificationUtil.class);

	private static final String K8sContainer = String.valueOf(System.getenv("K8S_CONTAINER_ID"));

	public static void notify(String eventName, ArrayList<KeyValueBean> additionalInfo,
			ArrayList<KeyValueBean> stateChange, String sourceId, String sourceName) throws IllegalArgumentException {
		sendEvent(eventName, additionalInfo, null, null, stateChange, null, sourceId, sourceName);
	}

	public static void sendEvent(String eventName, ArrayList<KeyValueBean> managedObjs,
			ArrayList<KeyValueBean> additionalInfo) throws IllegalArgumentException {
		sendEvent(eventName, additionalInfo, managedObjs, null, null, null, null, null);
	}

	public static void sendEvent(String eventName, ArrayList<KeyValueBean> managedObjects)
			throws IllegalArgumentException {
		sendEvent(eventName, null, managedObjects, null, null, null, null, null);
	}

	public static void sendEvent(String eventName, ArrayList<KeyValueBean> additionalInfo,
			ArrayList<KeyValueBean> managedObjects, ArrayList<KeyValueBean> thresholdInfo,
			ArrayList<KeyValueBean> stateChangeDefinition, ArrayList<KeyValueBean> monitoredAttributes, String sourceId,
			String sourceName) throws IllegalArgumentException {

		FlatBufferBuilder builder = new FlatBufferBuilder(1024);
		long eventTime = System.currentTimeMillis();

		if (!isValidEventName(eventName)) {
			LOG.error("Event name cannot be null or empty.");
			throw new IllegalArgumentException("Event name cannot be null or empty.");
		}

		int addInfo = addAdditionalInfoVector(builder, additionalInfo);
		int monAttr = addMonitoredAttributes(builder, monitoredAttributes);
		int mngdObj = addManagedObjects(builder, managedObjects);
		int thrInfo = addThresholdInfoVector(builder, thresholdInfo);
		int stChDef = addStateChangeDefinition(builder, stateChangeDefinition);
		int name = builder.createString(eventName);
		int container = builder.createString(K8sContainer);
		int srcId = 0, srcName = 0;
		if (sourceId != null)
			srcId = builder.createString(sourceId);
		if (sourceName != null)
			srcName = builder.createString(sourceName);

		Event.startEvent(builder);
		Event.addEventName(builder, name);
		Event.addEventTime(builder, eventTime);
		Event.addContainerId(builder, container);

		if (addInfo != 0)
			Event.addAdditionalInfo(builder, addInfo);
		if (monAttr != 0)
			Event.addMonitoredAttributes(builder, monAttr);
		if (mngdObj != 0)
			Event.addManagedObject(builder, mngdObj);
		if (thrInfo != 0)
			Event.addThresholdInfo(builder, thrInfo);
		if (stChDef != 0)
			Event.addStateChangeDefinition(builder, stChDef);
		if (sourceId != null)
			Event.addSourceId(builder, srcId);
		if (sourceName != null)
			Event.addSourceName(builder, srcName);

		int event = Event.endEvent(builder);
		builder.finish(event);
		byte[] buffer = builder.sizedByteArray();
		builder.clear();
		LOG.info(Constants.ACTIVITY + Constants.EVENT + "Publishing: " + eventName);
		publishEvent(buffer);
	}

	private static int addMonitoredAttributes(FlatBufferBuilder builder, ArrayList<KeyValueBean> beanList) {
		int dataVector = 0;
		if (isValidList(beanList)) {
			int data[] = addKeyValue(builder, beanList);
			dataVector = Event.createMonitoredAttributesVector(builder, data);
		}
		return dataVector;
	}

	private static int addStateChangeDefinition(FlatBufferBuilder builder,
			ArrayList<KeyValueBean> stateChangeDefinition) {
		int dataVector = 0;
		if (isValidList(stateChangeDefinition)) {
			int data[] = addKeyValue(builder, stateChangeDefinition);
			dataVector = Event.createStateChangeDefinitionVector(builder, data);
		}
		return dataVector;
	}

	private static int addThresholdInfoVector(FlatBufferBuilder builder, ArrayList<KeyValueBean> thresholdInfo) {
		int dataVector = 0;
		if (isValidList(thresholdInfo)) {
			int data[] = addKeyValue(builder, thresholdInfo);
			dataVector = Event.createThresholdInfoVector(builder, data);
		}
		return dataVector;
	}

	private static int addManagedObjects(FlatBufferBuilder builder, ArrayList<KeyValueBean> managedObjects) {
		int dataVector = 0;
		if (isValidList(managedObjects)) {
			int data[] = addKeyValue(builder, managedObjects);
			dataVector = Event.createManagedObjectVector(builder, data);
		}
		return dataVector;
	}

	private static int addAdditionalInfoVector(FlatBufferBuilder builder, ArrayList<KeyValueBean> additionalInfo) {
		int dataVector = 0;
		if (isValidList(additionalInfo)) {
			int data[] = addKeyValue(builder, additionalInfo);
			dataVector = Event.createAdditionalInfoVector(builder, data);
		}
		return dataVector;
	}

	private static boolean isValidEventName(String eventName) {
		return (eventName != null && !eventName.isEmpty());
	}

	private static boolean isValidList(ArrayList<KeyValueBean> beanList) {
		return (beanList != null && !beanList.isEmpty());
	}

	private static int[] addKeyValue(FlatBufferBuilder builder, ArrayList<KeyValueBean> beanList) {
		int[] beanVec = new int[beanList.size()];
		for (int i = 0; i < beanList.size(); i++) {

			int key = builder.createString(beanList.get(i).getKey());
			int value = builder.createString(beanList.get(i).getValue());

			KeyValue.startKeyValue(builder);
			KeyValue.addKey(builder, key);
			KeyValue.addValue(builder, value);
			int kv = KeyValue.endKeyValue(builder);
			beanVec[i] = kv;
		}
		return beanVec;
	}

	private static void publishEvent(byte[] message) {
		Connection natsConnection = NatsUtil.getConnection();
		natsConnection.publish("EVENT", message);
	}
}
