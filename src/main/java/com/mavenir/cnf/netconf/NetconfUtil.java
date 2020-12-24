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

package org.xgvela.cnf.netconf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import org.xgvela.cnf.Constants;
import org.xgvela.cnf.k8s.ConfigMapWatchProcessor;
import org.xgvela.cnf.notification.KeyValueBean;
import org.xgvela.cnf.notification.NotificationUtil;
import org.xgvela.cnf.util.MetricsUtil;
import org.xgvela.cnf.util.Utils;
import org.xgvela.cnf.util.Utils.ConfigDatatype;
import org.xgvela.cnf.util.Utils.RootType;
import org.xgvela.model.ConfModelMetadata;
import com.tailf.conf.ConfException;
import com.tailf.jnc.Device;
import com.tailf.jnc.DeviceUser;
import com.tailf.jnc.JNCException;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;

import io.prometheus.client.Counter;

@Component
public class NetconfUtil {

	private static final Logger LOG = LogManager.getLogger(NetconfUtil.class);
	private static final String url = "http://localhost:8008/api/running";
	private static final int DEFAULT_JNC_READ_TIMEOUT = 2 * 60 * 1000; // 2 mins

	private static final Counter configDataLoadAttemptsTotal = MetricsUtil.addCounter(
			"cmaas_configdata_load_attempts_total",
			"The number of times REST call has been made to load configuration data into a Netconf namespace");
	private static final Counter configDataLoadFailureTotal = MetricsUtil.addCounter(
			"cmaas_configdata_load_failure_total",
			"The number of times REST call has been made to load configuration data into a Netconf namespace and failed to do so successfully");

	@Value("${netconf.host}")
	private String netconfHost;

	@Value("${netconf.port}")
	private int netconfPort;

	@Value("${netconf.username}")
	private String netconfUsername;

	@Value("${netconf.password}")
	private String netconfPassword;

	@Autowired
	private RestTemplate restTemplate;

	public Maapi getMaapi() {
		final String NETCONF_CONTEXT_NAME = "cfg-svc";
		Socket maapiSock = null;
		Maapi maapi = null;
		try {
			maapiSock = new Socket(netconfHost, netconfPort);
			maapi = new Maapi(maapiSock);
			maapi.startUserSession(netconfUsername, InetAddress.getLocalHost(), NETCONF_CONTEXT_NAME,
					new String[] { netconfUsername }, MaapiUserSessionFlag.PROTO_TCP);
			maapi.reloadSchemas();

		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return maapi;
	}

	private static void closeSocket(Socket maapiSock) {
		if (null != maapiSock) {
			try {
				maapiSock.close();
			} catch (IOException ioExp) {
				LOG.error(ioExp.getMessage(), ioExp);
			}
		}
	}

	public boolean loadSchemas(String cmapName, String cmapNamespace, String nfId, int count) {
		LOG.debug("Loading schemas, ConfigMap: " + cmapName + ", Namespace: " + cmapNamespace);

		// limit to 2 recursive calls
		if (count > 2)
			return false;

		boolean status = false;
		Maapi maapi = null;
		try {
			maapi = getMaapi();
			List<String> fxsPaths = new ArrayList<String>();
			fxsPaths.add(Constants.FXS_FILE_PATH);
			maapi.initUpgrade(10, Maapi.MAAPI_UPGRADE_KILL_ON_TIMEOUT);
			maapi.performUpgrade(fxsPaths.toArray(new String[0]));
			maapi.commitUpgrade();
			maapi.reloadSchemas();
			status = true;
		} catch (ConfException exp) {

			// mark NF as not ready
			ConfigMapWatchProcessor.nfMgmtIntfFlag = false;
			LOG.error(exp.getMessage(), exp);

			try {
				if (maapi != null)
					maapi.abortUpgrade();
			} catch (ConfException | IOException exp1) {
				LOG.error(exp1.getMessage(), exp1);
			}

			NotificationUtil.sendEvent("CmaasSchemasLoadFailure", getMgdObjs(cmapName, cmapNamespace, nfId));
			LOG.error("Schema load failed, please check yangs: Deleting all schema files (.fxs) of yangs in ConfigMap: "
					+ cmapName + ", Namespace: " + cmapNamespace);

			// delete all fxs
			Utils.confModelPerConfigmap.get(cmapName + "/" + cmapNamespace).forEach((yangKey, yangModel) -> {
				LOG.debug("Deleting schema for: " + yangKey);
				Utils.deleteFxs(yangModel.getModuleName());
			});

			// recursive call
			status = loadSchemas(cmapName, cmapNamespace, nfId, count + 1);
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		} finally {
			if (maapi != null)
				closeSocket(maapi.getSocket());
		}
		return status;
	}

	private static ArrayList<KeyValueBean> getMgdObjs(String cmapName, String namespace, String nfId) {
		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		mgdObjs.add(new KeyValueBean("config-map", cmapName));
		mgdObjs.add(new KeyValueBean("namespace", namespace));
		mgdObjs.add(new KeyValueBean("nf-id", nfId));
		return mgdObjs;
	}

	public boolean compileYang(String yangFile, String fxsFile) {
		String cmd = "/netconf/bin/netconfc -c " + yangFile + " -o " + fxsFile + " --yangpath /netconf/apps/config/model/";
		return Utils.exec(cmd);
	}

	public static String getAuthString(String userName, String password) {
		String plainCreds = userName + ":" + password;
		byte[] plainCredsBytes = plainCreds.getBytes();
		byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
		String base64Creds = new String(base64CredsBytes);
		return base64Creds;
	}

	public String get(String yangPrefix, String rootName, RootType rootType, ConfigDatatype datatype) {

		ResponseEntity<String> response = null;
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set("Accept", getHdr(rootType, datatype));
			headers.add("Authorization", "Basic " + getAuthString(netconfUsername, netconfPassword));
			String netconfUrl = url + "/" + yangPrefix + ":" + rootName + "?deep";

			// in case schema load is ongoing, wait
			while (ConfigMapWatchProcessor.schemaLoadFlag.get()) {
				LOG.info("Waiting for schema load to finish...");
				Thread.sleep(500);
			}

			LOG.debug("Connecting to URL: [" + netconfUrl + "]");
			response = restTemplate.exchange(netconfUrl, HttpMethod.GET, new HttpEntity<String>(headers), String.class);
			return response.getBody();

		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return Constants.EMPTY_STRING;
	}

	private static String getHdr(RootType rootType, ConfigDatatype datatype) {
		String hdr = "application/vnd.yang.";

		if (rootType.equals(RootType.CONTAINER))
			hdr += "data+";
		else
			hdr += "collection+";

		if (datatype.equals(ConfigDatatype.JSON))
			hdr += "json";
		else
			hdr += "xml";
		return hdr;
	}

	public ResponseEntity<String> push(String cmapName, String cmapNamespace, String yangFile, String configData,
			ConfigDatatype datatype) {

		configDataLoadAttemptsTotal.inc();
		initL("Loading data for yang file: " + yangFile + ", in ConfigMap: " + cmapName);

		ConfModelMetadata confModelMeta = Utils.confModelPerConfigmap.get(cmapName + "/" + cmapNamespace).get(yangFile);

		String prefix = confModelMeta.getPrefix();
		String root = confModelMeta.getRootName();

		ResponseEntity<String> response = null;
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.add("Authorization", "Basic " + getAuthString(netconfUsername, netconfPassword));
			headers.add("Content-Type", getHdr(datatype));

			HttpMethod method;
			String netconfUrl = url + "/" + prefix + ":" + root;

			if (confModelMeta.getRootType().equals(RootType.CONTAINER)) {
				method = HttpMethod.PUT;
			} else {
				method = HttpMethod.PATCH;
			}

			LOG.debug("Connecting to URL: [" + netconfUrl + "], Method: [" + method + "], ConfigData:\n" + configData);
			response = restTemplate.exchange(netconfUrl, method, new HttpEntity<String>(configData, headers),
					String.class);
			NotificationUtil.sendEvent("CmaasConfigDataLoadSuccess", getMgdObjs(prefix, root));

		} catch (HttpClientErrorException e) {

			configDataLoadFailureTotal.inc();
			initL("Failed to load data for Yang file: " + yangFile + ", Prefix: " + prefix + ", Container: " + root
					+ ", ConfigMap: " + cmapName + ", Status code: " + e.getRawStatusCode() + ", Response body: "
					+ e.getResponseBodyAsString());

			LOG.error(e.getMessage(), e);
			NotificationUtil.sendEvent("CmaasConfigDataLoadFailure", getMgdObjs(prefix, root),
					getAddInfo(e.getRawStatusCode(), e.getResponseBodyAsString()));

		} catch (Exception e) {

			configDataLoadFailureTotal.inc();
			initL("Failed to load data for Yang file: " + yangFile + ", Prefix: " + prefix + ", Container: " + root
					+ ", ConfigMap: " + cmapName);

			LOG.error(e.getMessage(), e);
			NotificationUtil.sendEvent("CmaasConfigDataLoadFailure", getMgdObjs(prefix, root));
		}
		return response;
	}

	private static String getHdr(ConfigDatatype datatype) {
		String hdr = "application/vnd.yang.data+";
		if (datatype.equals(ConfigDatatype.JSON))
			hdr += "json";
		else
			hdr += "xml";
		return hdr;
	}

	private static void initL(String msg) {
		LOG.info(Constants.ACTIVITY + Constants.INIT + msg);
	}

	private ArrayList<KeyValueBean> getMgdObjs(String yangPrefix, String confContainer) {
		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		mgdObjs.add(new KeyValueBean("host", netconfHost));
		mgdObjs.add(new KeyValueBean("port", Integer.toString(netconfPort)));
		mgdObjs.add(new KeyValueBean("yang-prefix", yangPrefix));
		mgdObjs.add(new KeyValueBean("conf-container", confContainer));
		return mgdObjs;
	}

	private static ArrayList<KeyValueBean> getAddInfo(int statusCode, String responseBody) {
		ArrayList<KeyValueBean> addInfo = new ArrayList<>();
		addInfo.add(new KeyValueBean("status-code", String.valueOf(statusCode)));
		addInfo.add(new KeyValueBean("http-response-body", responseBody));
		return addInfo;
	}

	private static final String cmaasSession = "cmaas_rpc";

	public void push(String data) {
		Device device = null;
		DeviceUser deviceUser = null;
		String topXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<rpc xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"1\">" + "<edit-config>"
				+ "<target>" + "<running/>" + "</target>" + "<config>";
		String endXml = " </config>" + "</edit-config>" + "</rpc>";

		deviceUser = new DeviceUser("admin", "admin", "admin");
		try {
			device = new Device("cmaas", deviceUser, netconfHost, 2022);
			device.setDefaultReadTimeout(DEFAULT_JNC_READ_TIMEOUT);
			device.connect("admin");
			device.newSession(cmaasSession);

			String rpc = topXml + data + endXml;
			rpc = rpc.replaceAll("\\s*xmlns\\s*=\"\"", Constants.EMPTY_STRING);
			LOG.info("RPC-REQ : " + rpc);
			String reply = device.getSession(cmaasSession).rpc(rpc).toXMLString();
			LOG.info("RPC-RESP : " + reply);
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		} catch (JNCException e) {
			LOG.error(e.getMessage(), e);
		} finally {
			if (device != null) {
				closeDevice(netconfHost, device);
			}
		}
	}

	public static void closeDevice(String ip, Device device) {
		try {
			if (device != null) {
				LOG.info("Closing the device: " + ip);
				Thread thread = new Thread(() -> {
					try {
						if (device.getSession(cmaasSession) != null) {
							device.closeSession(cmaasSession);
						}
						device.close();
					} catch (Exception e) {
						LOG.error("Error while closing the device : " + ip);
					}
				});
				thread.start();
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), "Error occured while deleting the device");
		}
	}
}
