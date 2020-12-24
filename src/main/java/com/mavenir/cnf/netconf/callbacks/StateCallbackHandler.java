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

package org.xgvela.cnf.netconf.callbacks;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xgvela.cnf.Constants;
import org.xgvela.cnf.enums.State;
import org.xgvela.cnf.tmaas.model.ExtendedAttr;
import org.xgvela.cnf.tmaas.model.ManagedElement;
import org.xgvela.cnf.tmaas.model.NFService;
import org.xgvela.cnf.tmaas.model.NFServiceInstance;
import org.xgvela.cnf.tmaas.model.NetworkFunction;
import org.xgvela.cnf.tmaas.model.PodNetworksStatus;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfInt8;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfList;
import com.tailf.conf.ConfNoExists;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamStart;
import com.tailf.conf.ConfXMLParamStop;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.annotations.DataCallback;
import com.tailf.dp.proto.DataCBType;

@Component
public class StateCallbackHandler {

	private static final Logger LOG = LogManager.getLogger(StateCallbackHandler.class);
	private static final String STATE_CP = "tmaascp";

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static ManagedElement managedElement;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private CallbackUtil cbUtil;

	@DataCallback(callPoint = STATE_CP, callType = { DataCBType.ITERATOR })
	public Iterator<Object> iterator(DpTrans trans, ConfObject[] kp) throws DpCallbackException {

		LOG.debug("######################## ITERATOR");
		cbUtil.logKp(kp);
		cbUtil.logTxn(trans);

		List<Object> list = new ArrayList<>();

		ConfTag confTag = (ConfTag) kp[0];
		LOG.debug("Tag: " + confTag.getTag());

		if (managedElement == null) {
			getManagedElement();
		}

		if (confTag.getTag().equalsIgnoreCase("ManagedElement")) {
			getManagedElement();
			if (managedElement != null) {
				list.add(managedElement);
				LOG.debug("Added ME to list");
			}

		} else if (confTag.getTag().equalsIgnoreCase("NetworkFunction")) {
			list.addAll(managedElement.getElemList());
			LOG.debug("Added NFs to list");

		} else if (confTag.getTag().equalsIgnoreCase("NFService")) {

			String nfId = ((ConfKey) kp[1]).elementAt(0).toString();
			LOG.debug("NF Id: " + nfId);

			NetworkFunction nf = managedElement.getElemList().stream()
					.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);
			if (nf != null) {
				list.addAll(nf.getElemList());
				LOG.debug("Added NFSvcs to list");
			}

		} else if (confTag.getTag().equalsIgnoreCase("NFServiceInstance")) {

			String nfId = ((ConfKey) kp[3]).elementAt(0).toString();
			LOG.debug("NF Id: " + nfId);

			String nfSvcId = ((ConfKey) kp[1]).elementAt(0).toString();
			LOG.debug("NF Service Id: " + nfSvcId);

			NetworkFunction nf = managedElement.getElemList().stream()
					.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);
			if (nf != null) {
				NFService nfSvc = nf.getElemList().stream()
						.filter(nfSvcObj -> nfSvcObj.getId().equalsIgnoreCase(nfSvcId)).findAny().orElse(null);

				if (nfSvc != null) {
					list.addAll(nfSvc.getElemList());
					LOG.debug("Added NFSIs to list");
				}
			}
		} else if (confTag.getTag().equalsIgnoreCase("managedBy")) {

			if (managedElement.getManagedBy() != null) {

				if (managedElement.getManagedBy().getPrimary() != null
						&& !(managedElement.getManagedBy().getPrimary().equals("")))
					list.add(managedElement.getManagedBy().getPrimary());

				if (managedElement.getManagedBy().getSecondary() != null
						&& !(managedElement.getManagedBy().getSecondary().equals("")))
					list.add(managedElement.getManagedBy().getSecondary());
			}

		} else if (confTag.getTag().equalsIgnoreCase("networkList")) {
			String nfId = ((ConfKey) kp[6]).elementAt(0).toString();
			LOG.debug("NF Id: " + nfId);

			String nfSvcId = ((ConfKey) kp[4]).elementAt(0).toString();
			LOG.debug("NF Service Id: " + nfSvcId);

			String nfSvcInstanceId = ((ConfKey) kp[2]).elementAt(0).toString();
			LOG.debug("NF Service instance Id: " + nfSvcInstanceId);

			NetworkFunction nf = managedElement.getElemList().stream()
					.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

			if (nf != null) {
				NFService nfSvc = nf.getElemList().stream()
						.filter(nfSvcObj -> nfSvcObj.getId().equalsIgnoreCase(nfSvcId)).findAny().orElse(null);

				if (nfSvc != null) {
					NFServiceInstance nfsi = nfSvc.getElemList().stream()
							.filter(nfsins -> nfsins.getId().equals(nfSvcInstanceId)).findAny().orElse(null);
					list.addAll(nfsi.getNws());
					LOG.debug("Added to list");
				}
			}
		} else if (confTag.getTag().equalsIgnoreCase("extendedAttributes")) {

			// nf service instance attributes
			if (kp.length == 9) {

				String nfId = ((ConfKey) kp[5]).elementAt(0).toString();
				LOG.debug("NF Id: " + nfId);

				String nfSvcId = ((ConfKey) kp[3]).elementAt(0).toString();
				LOG.debug("NF Service Id: " + nfSvcId);

				String nfSvcInstanceId = ((ConfKey) kp[1]).elementAt(0).toString();
				LOG.debug("NF Service instance Id: " + nfSvcInstanceId);

				NetworkFunction nf = managedElement.getElemList().stream()
						.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

				if (nf != null) {
					NFService nfSvc = nf.getElemList().stream()
							.filter(nfSvcObj -> nfSvcObj.getId().equalsIgnoreCase(nfSvcId)).findAny().orElse(null);

					if (nfSvc != null) {
						NFServiceInstance nfsi = nfSvc.getElemList().stream()
								.filter(nfsins -> nfsins.getId().equals(nfSvcInstanceId)).findAny().orElse(null);

						list.addAll(getAttrs(nfsi.getExtendedAttrs()));
						LOG.debug("Added NFSI extended attributes to list");
					}
				}
			} // nf service attributes
			else if (kp.length == 7) {

				String nfId = ((ConfKey) kp[3]).elementAt(0).toString();
				LOG.debug("NF Id: " + nfId);

				String nfSvcId = ((ConfKey) kp[1]).elementAt(0).toString();
				LOG.debug("NF Service Id: " + nfSvcId);

				NetworkFunction nf = managedElement.getElemList().stream()
						.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

				if (nf != null) {
					NFService nfSvc = nf.getElemList().stream()
							.filter(nfSvcObj -> nfSvcObj.getId().equalsIgnoreCase(nfSvcId)).findAny().orElse(null);

					list.addAll(getAttrs(nfSvc.getExtendedAttrs()));
					LOG.debug("Added NFS extended attributes to list");
				}

			} // nf attributes
			else {

				String nfId = ((ConfKey) kp[1]).elementAt(0).toString();
				LOG.debug("NF Id: " + nfId);

				NetworkFunction nf = managedElement.getElemList().stream()
						.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

				list.addAll(getAttrs(nf.getExtendedAttrs()));
				LOG.debug("Added NF extended attributes to list");

			}
		}
		LOG.debug("List Size:" + list.size());
		return list.iterator();
	}

	private List<ExtendedAttr> getAttrs(Map<String, String> attrs) {
		List<ExtendedAttr> attrsList = new ArrayList<>();
		if (attrs != null) {
			attrs.forEach((attrKey, attrValue) -> {
				attrsList.add(new ExtendedAttr(attrKey, attrValue));
			});
		}
		return attrsList;
	}

	@DataCallback(callPoint = STATE_CP, callType = { DataCBType.GET_NEXT })
	public ConfKey getKey(DpTrans trans, ConfObject[] kp, Object obj) {

		ConfKey key = null;
		try {
			LOG.debug("######################## GET_NEXT");
			cbUtil.logKp(kp);
			cbUtil.logTxn(trans);

			if (obj instanceof ManagedElement) {
				ManagedElement me = (ManagedElement) obj;
				key = new ConfKey(new ConfObject[] { new ConfBuf(me.getId()) });

			} else if (obj instanceof NetworkFunction) {
				NetworkFunction nf = (NetworkFunction) obj;
				key = new ConfKey(new ConfObject[] { new ConfBuf(nf.getId()) });

			} else if (obj instanceof NFService) {
				NFService nfService = (NFService) obj;
				key = new ConfKey(new ConfObject[] { new ConfBuf(nfService.getId()) });

			} else if (obj instanceof NFServiceInstance) {
				NFServiceInstance nfServiceInstance = (NFServiceInstance) obj;
				key = new ConfKey(new ConfObject[] { new ConfBuf(nfServiceInstance.getId()) });

			} else if (obj instanceof PodNetworksStatus) {
				PodNetworksStatus podNetworksStatus = (PodNetworksStatus) obj;
				key = new ConfKey(new ConfObject[] { new ConfBuf(podNetworksStatus.getName()) });

			} else if (obj instanceof ExtendedAttr) {
				ExtendedAttr extendedAttr = (ExtendedAttr) obj;
				key = new ConfKey(new ConfObject[] { new ConfBuf(extendedAttr.getName()) });

			} else if (obj instanceof String) {
				String objStr = (String) obj;
				key = new ConfKey(new ConfObject[] { new ConfBuf(objStr) });
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return key;
	}

	@DataCallback(callPoint = STATE_CP, callType = DataCBType.GET_OBJECT)
	public ConfObject[] getObject(DpTrans trans, ConfObject[] kp)
			throws UnknownHostException, ConfException, JsonProcessingException {

		LOG.debug("######################## GET_OBJECT");
		cbUtil.logKp(kp);
		cbUtil.logTxn(trans);

		ConfTag cTag = (ConfTag) kp[1];
		ConfXMLParam[] retVal = null;
		String prefix = "meXgvela";

		if (managedElement == null) {
			getManagedElement();
		}

		if (cTag.getTag().equalsIgnoreCase("ManagedElement")) {

			LOG.debug("Getting Managed Element");
			retVal = new ConfXMLParam[] { new ConfXMLParamStart(prefix, "attributes"),
					new ConfXMLParamValue(prefix, "dnPrefix", new ConfBuf(managedElement.getDnPrefix())),
					new ConfXMLParamValue(prefix, "userLabel", new ConfBuf(managedElement.getUserLabel())),
					new ConfXMLParamValue(prefix, "locationName", new ConfBuf(managedElement.getLocationName())),
					new ConfXMLParamValue(prefix, "managedElementTypeList", new ConfNoExists()),
					new ConfXMLParamValue(prefix, "vendorName", new ConfBuf(managedElement.getVendorName())),
					new ConfXMLParamValue(prefix, "userDefinedState",
							new ConfBuf(managedElement.getUserDefinedState())),
					new ConfXMLParamValue(prefix, "swVersion", new ConfBuf(managedElement.getSwVersion())),
					new ConfXMLParamValue(prefix, "priorityLabel", new ConfNoExists()),
					new ConfXMLParamStop(prefix, "attributes") };

		} else if (cTag.getTag().equalsIgnoreCase("NetworkFunction")) {

			LOG.debug("Getting Network Function");
			String nfId = ((ConfKey) kp[0]).elementAt(0).toString();
			LOG.debug("NF Id: " + nfId);

			NetworkFunction nf = managedElement.getElemList().stream()
					.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

			if (nf != null) {

				LOG.debug("Found NF: " + nf.getId());
				int stateValue = getStateValue(nf.getState());

				prefix = "nfXgvela";

				JsonNode spec = nf.getOperation().getSpecification();
				ConfObject openApi = new ConfNoExists();

				// TODO: confirm this check
				if (spec != null && spec.get("servers") != null && spec.get("servers").size() != 0) {
					LOG.debug("Specification found for NF");
					openApi = new ConfBuf(cbUtil.encodeBase64(MAPPER.writeValueAsString(spec)));
				}

				retVal = new ConfXMLParam[] { new ConfXMLParamStart(prefix, "attributes"),
						new ConfXMLParamValue(prefix, "name", new ConfBuf(nf.getName())),
						new ConfXMLParamValue(prefix, "userLabel", new ConfBuf(nf.getUserLabel())),
						new ConfXMLParamValue(prefix, "swVersion", new ConfBuf(nf.getSwVersion())),
						new ConfXMLParamValue(prefix, "nfType", new ConfBuf(nf.getNfType())),
						new ConfXMLParamValue(prefix, "state", new ConfEnumeration(stateValue)),
						new ConfXMLParamValue(prefix, "administrativeState", new ConfEnumeration(1)),
						new ConfXMLParamValue(prefix, "operationalState", new ConfEnumeration(1)),
						new ConfXMLParamValue(prefix, "usageState", new ConfEnumeration(1)),
						new ConfXMLParamStop(prefix, "attributes"), new ConfXMLParamStart(prefix, "operations"),
						new ConfXMLParamStart(prefix, "specification"),
						new ConfXMLParamValue(prefix, "openapi", openApi),
						new ConfXMLParamStop(prefix, "specification"), new ConfXMLParamStop(prefix, "operations"), };
			}

		} else if (cTag.getTag().equalsIgnoreCase("NFService")) {

			LOG.debug("Getting NF Service");
			String nfId = ((ConfKey) kp[2]).elementAt(0).toString();
			LOG.debug("NF Id: " + nfId);

			NetworkFunction nf = managedElement.getElemList().stream()
					.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

			if (nf != null) {

				String nfSvcId = ((ConfKey) kp[0]).elementAt(0).toString();
				LOG.debug("NF Service Id: " + nfSvcId);
				NFService nfService = nf.getElemList().stream()
						.filter(nfServiceObj -> nfServiceObj.getId().equals(nfSvcId)).findAny().orElse(null);

				if (nfService != null) {

					LOG.debug("Found NF Service: " + nfService.getId());
					int stateValue = getStateValue(nfService.getState());
					prefix = "nfXgvela";

					JsonNode spec = nfService.getServiceOperation().getSpecification();
					ConfObject openApi = new ConfNoExists();

					// TODO: confirm this check
					if (spec != null && spec.get("servers") != null && spec.get("servers").size() != 0) {
						LOG.debug("Specification found for NFService");
						openApi = new ConfBuf(cbUtil.encodeBase64(MAPPER.writeValueAsString(spec)));
					}

					if (nfService.isHaEnabled()) {

						LOG.debug("HA is enabled");
						retVal = new ConfXMLParam[] { new ConfXMLParamStart(prefix, "attributes"),
								new ConfXMLParamValue(prefix, "name", new ConfBuf(nfService.getName())),
								new ConfXMLParamValue(prefix, "userLabel", new ConfBuf(nfService.getUserLabel())),
								new ConfXMLParamValue(prefix, "nfServiceType",
										new ConfBuf(nfService.getNfServiceType())),
								new ConfXMLParamValue(prefix, "swVersion", new ConfBuf(nfService.getSwVersion())),
								new ConfXMLParamValue(prefix, "state", new ConfEnumeration(stateValue)),
								new ConfXMLParamValue(prefix, "administrativeState", new ConfEnumeration(1)),
								new ConfXMLParamValue(prefix, "operationalState", new ConfEnumeration(1)),
								new ConfXMLParamValue(prefix, "usageState", new ConfEnumeration(1)),
								new ConfXMLParamValue(prefix, "registrationState", new ConfNoExists()),
								new ConfXMLParamStart(prefix, "ha"),
								new ConfXMLParamValue(prefix, "monitoringMode",
										new ConfEnumeration(getMonitoringMode(nfService.getMonitoringMode()))),
								new ConfXMLParamValue(prefix, "mode",
										new ConfEnumeration(getMode(nfService.getMode()))),
								new ConfXMLParamValue(prefix, "numStandby", new ConfInt8(nfService.getNumStandby())),
								new ConfXMLParamStop(prefix, "ha"), new ConfXMLParamStop(prefix, "attributes"),
								new ConfXMLParamStart(prefix, "operations"),
								new ConfXMLParamStart(prefix, "specification"),
								new ConfXMLParamValue(prefix, "openapi", openApi),
								new ConfXMLParamStop(prefix, "specification"),
								new ConfXMLParamStop(prefix, "operations"), };

					} else {

						LOG.debug("HA is disabled");
						retVal = new ConfXMLParam[] { new ConfXMLParamStart(prefix, "attributes"),
								new ConfXMLParamValue(prefix, "name", new ConfBuf(nfService.getName())),
								new ConfXMLParamValue(prefix, "userLabel", new ConfBuf(nfService.getUserLabel())),
								new ConfXMLParamValue(prefix, "swVersion", new ConfBuf(nfService.getSwVersion())),
								new ConfXMLParamValue(prefix, "nfServiceType",
										new ConfBuf(nfService.getNfServiceType())),
								new ConfXMLParamValue(prefix, "state", new ConfEnumeration(stateValue)),
								new ConfXMLParamValue(prefix, "administrativeState", new ConfEnumeration(1)),
								new ConfXMLParamValue(prefix, "operationalState", new ConfEnumeration(1)),
								new ConfXMLParamValue(prefix, "usageState", new ConfEnumeration(1)),
								new ConfXMLParamValue(prefix, "registrationState", new ConfNoExists()),
								new ConfXMLParamValue(prefix, "ha", new ConfNoExists()),
								new ConfXMLParamStop(prefix, "attributes"), new ConfXMLParamStart(prefix, "operations"),
								new ConfXMLParamStart(prefix, "specification"),
								new ConfXMLParamValue(prefix, "openapi", openApi),
								new ConfXMLParamStop(prefix, "specification"),
								new ConfXMLParamStop(prefix, "operations"), };
					}
				}
			}
		} else if (cTag.getTag().equalsIgnoreCase("NFServiceInstance")) {

			LOG.debug("Getting NF Service Instance");
			String nfId = ((ConfKey) kp[4]).elementAt(0).toString();
			LOG.debug("NF Id: " + nfId);

			NetworkFunction nf = managedElement.getElemList().stream()
					.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

			if (nf != null) {

				String nfSvcId = ((ConfKey) kp[2]).elementAt(0).toString();
				LOG.debug("Nf Service Id: " + nfSvcId);
				NFService nfService = nf.getElemList().stream()
						.filter(nfServiceObj -> nfServiceObj.getId().equals(nfSvcId)).findAny().orElse(null);

				if (nfService != null) {

					String nfSvcInstanceId = ((ConfKey) kp[0]).elementAt(0).toString();
					LOG.debug("NF Service Instance Id: " + nfSvcInstanceId);
					NFServiceInstance nfSvcInstance = nfService.getElemList().stream()
							.filter(nfSvcInstanceObj -> nfSvcInstanceObj.getId().equalsIgnoreCase(nfSvcInstanceId))
							.findAny().orElse(null);

					if (nfSvcInstance != null) {

						LOG.debug("NF Service Instance found: " + nfSvcInstanceId);
						int stateValue = getStateValue(nfSvcInstance.getState());
						prefix = "nfXgvela";

						retVal = new ConfXMLParam[] { new ConfXMLParamStart(prefix, "attributes"),
								new ConfXMLParamValue(prefix, "name", new ConfBuf(nfSvcInstance.getName())),
								new ConfXMLParamValue(prefix, "userLabel", new ConfBuf(nfSvcInstance.getUserLabel())),
								new ConfXMLParamValue(prefix, "state", new ConfEnumeration(stateValue)),
								new ConfXMLParamValue(prefix, "haRole",
										new ConfEnumeration(getHaRole(nfSvcInstance.getHaRole()))),
								new ConfXMLParamValue(prefix, "msuid", new ConfBuf(nfSvcInstance.getMsUid())),
								new ConfXMLParamStop(prefix, "attributes") };
					}
				}
			}
		} else if (cTag.getTag().equalsIgnoreCase("networkList")) {
			LOG.debug("Getting interface list");

			String nfId = ((ConfKey) kp[7]).elementAt(0).toString();
			LOG.debug("NF Id: " + nfId);

			NetworkFunction nf = managedElement.getElemList().stream()
					.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

			if (nf != null) {

				String nfSvcId = ((ConfKey) kp[5]).elementAt(0).toString();
				LOG.debug("Nf Service Id: " + nfSvcId);
				NFService nfService = nf.getElemList().stream()
						.filter(nfServiceObj -> nfServiceObj.getId().equals(nfSvcId)).findAny().orElse(null);

				if (nfService != null) {

					String nfSvcInstanceId = ((ConfKey) kp[3]).elementAt(0).toString();
					LOG.debug("NF Service Instance Id: " + nfSvcInstanceId);
					NFServiceInstance nfSvcInstance = nfService.getElemList().stream()
							.filter(nfSvcInstanceObj -> nfSvcInstanceObj.getId().equalsIgnoreCase(nfSvcInstanceId))
							.findAny().orElse(null);

					if (nfSvcInstance != null) {

						String name = ((ConfKey) kp[0]).elementAt(0).toString();
						PodNetworksStatus podNws = nfSvcInstance.getNws().stream()
								.filter(pns -> pns.getName().equals(name)).findAny().orElse(null);

						if (podNws != null) {
							LOG.debug("PodNetworkStatus found: " + name);
							LOG.debug("Adding ips to list");
							ConfList ips = new ConfList();
							podNws.getIps().forEach(ip -> {
								ips.addElem(new ConfBuf(ip));
							});
							LOG.debug("Adding vips to list");
							ConfList vips = new ConfList();
							podNws.getVips().forEach(vip -> {
								vips.addElem(new ConfBuf(vip));
							});
							return new ConfValue[] { new ConfBuf(name), new ConfBuf(podNws.getIntf()),
									new ConfNoExists(), new ConfBool(podNws.is_default()), ips, vips,
									new ConfNoExists() };
						}
					}
				}
			}
		} else if (cTag.getTag().equals("extendedAttributes")) {
			if (kp.length == 10) {
				LOG.debug("Getting extended attributes for NFServiceInstance");

				String nfId = ((ConfKey) kp[6]).elementAt(0).toString();
				LOG.debug("NF Id: " + nfId);

				NetworkFunction nf = managedElement.getElemList().stream()
						.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

				if (nf != null) {

					String nfSvcId = ((ConfKey) kp[4]).elementAt(0).toString();
					LOG.debug("Nf Service Id: " + nfSvcId);
					NFService nfService = nf.getElemList().stream()
							.filter(nfServiceObj -> nfServiceObj.getId().equals(nfSvcId)).findAny().orElse(null);

					if (nfService != null) {

						String nfSvcInstanceId = ((ConfKey) kp[2]).elementAt(0).toString();
						LOG.debug("NF Service Instance Id: " + nfSvcInstanceId);
						NFServiceInstance nfSvcInstance = nfService.getElemList().stream()
								.filter(nfSvcInstanceObj -> nfSvcInstanceObj.getId().equalsIgnoreCase(nfSvcInstanceId))
								.findAny().orElse(null);

						if (nfSvcInstance != null) {
							String name = ((ConfKey) kp[0]).elementAt(0).toString();
							return new ConfValue[] { new ConfBuf(name),
									new ConfBuf(nfSvcInstance.getExtendedAttrs().get(name)) };
						}
					}
				}
			} else if (kp.length == 8) {
				LOG.debug("Getting extended attributes for NFService");

				String nfId = ((ConfKey) kp[4]).elementAt(0).toString();
				LOG.debug("NF Id: " + nfId);

				NetworkFunction nf = managedElement.getElemList().stream()
						.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

				if (nf != null) {

					String nfSvcId = ((ConfKey) kp[2]).elementAt(0).toString();
					LOG.debug("Nf Service Id: " + nfSvcId);
					NFService nfService = nf.getElemList().stream()
							.filter(nfServiceObj -> nfServiceObj.getId().equals(nfSvcId)).findAny().orElse(null);

					if (nfService != null) {
						String name = ((ConfKey) kp[0]).elementAt(0).toString();
						return new ConfValue[] { new ConfBuf(name),
								new ConfBuf(nfService.getExtendedAttrs().get(name)) };
					}
				}
			} else {
				LOG.debug("Getting extended attributes for NF");

				String nfId = ((ConfKey) kp[2]).elementAt(0).toString();
				LOG.debug("NF Id: " + nfId);

				NetworkFunction nf = managedElement.getElemList().stream()
						.filter(nfObj -> nfObj.getId().equalsIgnoreCase(nfId)).findAny().orElse(null);

				if (nf != null) {
					String name = ((ConfKey) kp[0]).elementAt(0).toString();
					return new ConfValue[] { new ConfBuf(name), new ConfBuf(nf.getExtendedAttrs().get(name)) };
				}
			}
		}
		return retVal;
	}

	private int getMode(String mode) {
		if (mode.equals("hotStandby"))
			return 0;
		else if (mode.equals("coldStandby"))
			return 1;
		// all active
		return 2;
	}

	private int getMonitoringMode(String monitoringMode) {
		if (monitoringMode.equals("k8s"))
			return 0;
		else if (monitoringMode.equals("hb"))
			return 1;
		return 0;
	}

	private int getHaRole(String haRole) {
		if (haRole.equals("active"))
			return 1;
		else if (haRole.equals("standby"))
			return 2;
		return 0;
	}

	private int getStateValue(State state) {
		int stateValue = 0;

		switch (state) {
		case INSTANTIATED_CONFIGURED_ACTIVE:
			stateValue = 3;
			break;
		case INSTANTIATED_CONFIGURED_INACTIVE:
			stateValue = 2;
			break;
		case INSTANTIATED_NOT_CONFIGURED:
			stateValue = 1;
			break;
		case NOT_READY:
			stateValue = 0;
			break;
		case NULL:
			stateValue = 0;
			break;
		case READY:
			stateValue = 1;
			break;
		case TERMINATED:
			stateValue = 4;
			break;
		default:
			break;
		}
		LOG.debug("State: " + state.value() + ", Value: " + stateValue);
		return stateValue;
	}

	private void getManagedElement() throws DpCallbackException {
		try {
			String meJson = restTemplate.exchange(cbUtil.getTopoFqdn() + "/api/v1/tmaas/topo", HttpMethod.GET,
					new HttpEntity<String>(Constants.EMPTY_STRING), String.class).getBody();

			managedElement = MAPPER.readValue(meJson, ManagedElement.class);
			LOG.debug(MAPPER.writeValueAsString(managedElement));

		} catch (HttpClientErrorException | HttpServerErrorException e) {

			// throw error in case tmaas returns 4xx/5xx
			LOG.error("Request failed with code: " + e.getStatusCode() + ", body: " + e.getResponseBodyAsString());
			throw new DpCallbackException(
					"Topo-Engine returned: " + e.getResponseBodyAsString() + ", Code: " + e.getStatusCode());

		} catch (ResourceAccessException e) {

			// throw IO errors
			LOG.error(e.getMessage(), e);
			throw new DpCallbackException("Unable to reach TMaaS");

		} catch (Exception e) {

			// throw error
			LOG.error(e.getMessage(), e);
			throw new DpCallbackException("Exception: " + e.getMessage());
		}
	}
}
