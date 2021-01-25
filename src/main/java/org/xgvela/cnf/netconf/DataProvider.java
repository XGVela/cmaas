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

import java.net.Socket;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import org.xgvela.cnf.Constants;
import org.xgvela.cnf.netconf.callbacks.ActionCallbackHandler;
import org.xgvela.cnf.netconf.callbacks.ConfigUpdateCallback;
import org.xgvela.cnf.netconf.callbacks.StateCallbackHandler;
import org.xgvela.cnf.notification.KeyValueBean;
import org.xgvela.cnf.notification.NotificationUtil;
import com.tailf.conf.ConfException;
import com.tailf.dp.Dp;

/**
 * Class Creates Data Provider for netconf Data
 */

@Component
@PropertySource("classpath:application.properties")
public class DataProvider {

	private static final Logger LOG = LogManager.getLogger(DataProvider.class);
	private static final String DP = "cem-server1";

	@Value("${netconf.host}")
	private String netconfHost;

	@Value("${netconf.port}")
	private int netconfPort;

	@Autowired
	private ConfigUpdateCallback endIndicatorCallback;

	@Autowired
	private StateCallbackHandler stateIndicatorCallback;

	@Autowired
	private ActionCallbackHandler actionIndicatorCallback;

	@Async
	public void init() {
		try {
			LOG.info(Constants.ACTIVITY + Constants.DP + DP + " Creating DataProvider connection to Netconf, Host:"
					+ netconfHost);

			Socket dpSocket = new Socket(netconfHost, netconfPort);
			final Dp dp = new Dp(DP, dpSocket);
//			dp.setErrorVerbosity(ErrorVerbosity.TRACE);
			dp.registerAnnotatedCallbacks(endIndicatorCallback);
			dp.registerAnnotatedCallbacks(stateIndicatorCallback);
			dp.registerAnnotatedCallbacks(actionIndicatorCallback);
			dp.registerDone();

			Thread dpTh = new Thread(new Runnable() {
				public void run() {
					try {
						while (true)
							dp.read();
					} catch (ConfException e) {
						LOG.error(e.getErrorCode() + ", " + e.getCause() + ", " + e.getMessage() + ", " + e);
					} catch (Exception exp) {
						LOG.error(exp.getMessage(), exp);
						LOG.error(Constants.ACTIVITY + Constants.DP + DP
								+ " Netconf connection error, raising alarm and exiting application");
						NotificationUtil.sendEvent("CmaasDataProviderFailure", getMgdObjs());
					}
					System.exit(1);
				}
			});
			dpTh.start();
			LOG.info(Constants.ACTIVITY + Constants.DP + DP + " Created DataProvider connection to Netconf, Host:"
					+ netconfHost);

		} catch (Exception exp) {
			LOG.error(exp.getMessage(), exp);
			System.exit(1);
		}
	}

	private ArrayList<KeyValueBean> getMgdObjs() {
		ArrayList<KeyValueBean> mgdObjs = new ArrayList<>();
		mgdObjs.add(new KeyValueBean("host", netconfHost));
		mgdObjs.add(new KeyValueBean("port", String.valueOf(netconfPort)));
		mgdObjs.add(new KeyValueBean("data-provider", DP));
		return mgdObjs;
	}
}
