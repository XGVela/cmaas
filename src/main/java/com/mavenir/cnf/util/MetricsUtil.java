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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;

@Component
public class MetricsUtil {

	private static Logger LOG = LogManager.getLogger(MetricsUtil.class);

	// start an HTTP server serving the default Prometheus registry
	@Async
	public void startHTTPExporter() {
		try {
			@SuppressWarnings("unused")
			HTTPServer server = new HTTPServer(8000);
			LOG.info("HTTP exporter started.");
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
	}

	public static Counter addCounter(String name, String help) {
		return Counter.build(name, help).register();
	}

	public static Counter addCounter(String name, String help, String namespace) {
		return Counter.build(name, help).namespace(namespace).register();
	}

	public static Counter addCounter(String name, String help, String... labels) {
		return Counter.build(name, help).labelNames(labels).register();
	}

	public static Counter addCounter(String name, String help, String namespace, String... labels) {
		return Counter.build(name, help).namespace(namespace).labelNames(labels).register();
	}

	public static Gauge addGauge(String name, String help) {
		return Gauge.build(name, help).register();
	}

	public static Gauge addGauge(String name, String help, String namespace) {
		return Gauge.build(name, help).namespace(namespace).register();
	}

	public static Gauge addGauge(String name, String help, String... labels) {
		return Gauge.build(name, help).labelNames(labels).register();
	}

	public static Gauge addGauge(String name, String help, String namespace, String... labels) {
		return Gauge.build(name, help).namespace(namespace).labelNames(labels).register();
	}

	public static Histogram addHistogram(String name, String help) {
		return Histogram.build(name, help).register();
	}

	public static Histogram addHistogram(String name, String help, double... buckets) {
		return Histogram.build(name, help).buckets(buckets).register();
	}

	public static Histogram addHistogram(String name, String help, String namespace) {
		return Histogram.build(name, help).namespace(namespace).register();
	}

	public static Histogram addHistogram(String name, String help, String... labels) {
		return Histogram.build(name, help).labelNames(labels).register();
	}

	public static Histogram addHistogram(String name, String help, String namespace, String... labels) {
		return Histogram.build(name, help).namespace(namespace).labelNames(labels).register();
	}

	public static Histogram addHistogram(String name, String help, String namespace, double... buckets) {
		return Histogram.build(name, help).namespace(namespace).buckets(buckets).register();
	}

	public static Histogram addHistogram(String name, String help, String[] labels, double... buckets) {
		return Histogram.build(name, help).labelNames(labels).register();
	}

	public static Histogram addHistogram(String name, String help, String namespace, String[] labels,
			double... buckets) {
		return Histogram.build(name, help).namespace(namespace).labelNames(labels).register();
	}

	public static Summary addSummary(String name, String help) {
		return Summary.build(name, help).register();
	}

	public static Summary addSummary(String name, String help, String... labels) {
		return Summary.build(name, help).labelNames(labels).register();
	}

	public static Summary addSummary(String name, String help, String namespace) {
		return Summary.build(name, help).namespace(namespace).register();
	}

	public static Summary addSummary(String name, String help, String namespace, String... labels) {
		return Summary.build(name, help).namespace(namespace).labelNames(labels).register();
	}
}
