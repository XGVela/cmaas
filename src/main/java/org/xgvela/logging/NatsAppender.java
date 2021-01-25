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

package org.xgvela.logging;

import java.io.Serializable;
import java.nio.charset.Charset;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import com.google.flatbuffers.FlatBufferBuilder;
import org.xgvela.cnf.util.NatsUtil;

import io.nats.client.Connection;

@Plugin(name = "NatsAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class NatsAppender extends AbstractAppender {

	public static String K8sContainer = String.valueOf(System.getenv("K8S_CONTAINER_ID"));
	public static String ConfigService = String.valueOf("config-service");

	private NatsAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
		super(name, filter, layout, ignoreExceptions);
	}

	@PluginFactory
	public static NatsAppender createAppender(@PluginAttribute("name") String name,
			@PluginElement("Filter") final Filter filter, @PluginElement("Layout") Layout<?> layout,
			@PluginAttribute("ignoreExceptions") final boolean ignoreExceptions) {

		if (name == null) {
			LOGGER.error("No name provided for NatsAppender");
			return null;
		}
		if (layout == null) {
			layout = PatternLayout.createDefaultLayout();
		}
		return new NatsAppender(name, filter, layout, ignoreExceptions);
	}

	@Override
	public void append(LogEvent event) {
		final String log = new String(getLayout().toByteArray(event), Charset.defaultCharset());
		publishToNats(log);
	}

	private void publishToNats(String record) {
		Connection natsConnection = NatsUtil.getConnection();
		byte[] buff = buildFlatBuff(record);
		natsConnection.publish("LOG", buff);
	}

	public static byte[] buildFlatBuff(String record) {

		FlatBufferBuilder builder = new FlatBufferBuilder(1024);
		int containerName = builder.createString(ConfigService);
		int containerId = builder.createString(K8sContainer);
		int payload = builder.createString(record);
		int log = Log.createLog(builder, containerId, containerName, payload);
		builder.finish(log);

		byte[] buff = builder.sizedByteArray();
		builder.clear();

		return buff;
	}
}
