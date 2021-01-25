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

import io.nats.client.Connection;
import io.nats.client.Nats;

public class NatsUtil {

	static Connection natsConnection;

	public static Connection getConnection() {
		if (natsConnection == null) {
			System.out.println("Registering with XGVELA...");
			RegisterTask.init();
			initConnection();
			InitializationBean.registrationLatch.countDown();
		}
		return natsConnection;
	}

	public static void initConnection() {
		try {
			natsConnection = Nats.connect();
			System.out.println("NATS Connection is created.");
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}
