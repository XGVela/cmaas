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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.xgvela.cnf.Constants;
import org.xgvela.cnf.k8s.ConfigMapWatchProcessor;
import org.xgvela.model.ConfModelMetadata;
import org.xgvela.model.ConfigMapMetadata;

public class Utils {

	private static final Logger LOG = LogManager.getLogger(Utils.class);

	// configuration mode
	public static enum ConfigMode {
		DAY_0, DAY_2, DAY_1
	}

	// configuration data type
	public static enum ConfigDatatype {
		JSON, XML
	}

	// yang model root type
	public static enum RootType {
		LIST, CONTAINER, DEFAULT
	}

	// <(configmap/namespace): <yangFile: ConfModelMetadata>>
	public static ConcurrentHashMap<String, ConcurrentHashMap<String, ConfModelMetadata>> confModelPerConfigmap = new ConcurrentHashMap<>();

	// <yang-ns: ConfigmapMetadata>
	public static ConcurrentHashMap<String, ConfigMapMetadata> configmapsPerYangNamespace = new ConcurrentHashMap<>();

	public static String getNfLabel(String nfName) {
		return ConfigMapWatchProcessor.meUserLabel + ",NetworkFunction=" + nfName;
	}

	public static String getUUID(String value) {
		return UUID.nameUUIDFromBytes(value.getBytes()).toString();
	}

	public static void writeYang(String moduleName, String yangModel) {
		writeFile(Constants.YANG_FILE_PATH + moduleName + Constants.YANG, yangModel);
	}

	public static void deleteFxs(String moduleName) {
		deleteFile(Constants.FXS_FILE_PATH + moduleName + Constants.FXS);
	}

	public static void writeFile(String fileName, String data) {
		try {
			Files.write(Paths.get(fileName), data.getBytes());
		} catch (IOException e) {
			LOG.error("Error writing file. " + fileName, e);
		}
	}

	public static String getJsonFromYang(String fileName) {
		if (fileName.contains(Constants.REV)) {
			return fileName.substring(0, fileName.indexOf(Constants.REV)) + Constants.JSON;
		}
		return fileName.substring(0, fileName.indexOf(Constants.YANG)) + Constants.JSON;
	}

	public static boolean isValidJsonDataKey(String fileName) {
		return (fileName.endsWith(Constants.JSON) && !fileName.equals(Constants.UPDATE_POLICY_KEY)
				&& !fileName.equals(Constants.DEPENDENCY_KEY));
	}

	public static String getXmlFromJson(String fileName) {
		return fileName.substring(0, fileName.indexOf(Constants.JSON)) + Constants.XML;
	}

	public static boolean isYang(String fileName) {
		return fileName.endsWith(Constants.YANG);
	}

	public static boolean exec(String cmd) {
		try {
			String[] commands = { "bash", "-c", cmd };
			Process proc = Runtime.getRuntime().exec(commands);
			proc.waitFor();

			if (proc.exitValue() != 0) {
				BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
				String s = null, out = "";
				while ((s = stdError.readLine()) != null) {
					out += s;
				}
				LOG.error("ExitCode:" + proc.exitValue() + ", " + out);
			} else {
				return true;
			}
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		} catch (InterruptedException e) {
			LOG.error(e.getMessage(), e);
		}
		return false;
	}

	public static boolean deleteFile(String file) {
		LOG.info("Deleting file: " + file);
		boolean status = false;
		try {
			LOG.info("Deletion status: " + Files.deleteIfExists(Paths.get(file)));
		} catch (IOException e) {
			LOG.error("Invalid permissions.");
		}
		return status;
	}

	// update yang models with nf-id appended metadata
	public static String updateYangModel(String modelData, ConfModelMetadata confModel, String nfName) {
		StringBuilder result = new StringBuilder();
		Scanner scanner = new Scanner(modelData);

		for (int i = 0; scanner.hasNextLine();) {
			String line = scanner.nextLine();
			if (i != 3)
				line = line.trim();

			if (line.startsWith("module")) {
				line = "module " + confModel.getModuleName() + " {";
				i++;
			} else if (line.startsWith("submodule")) {
				line = "submodule " + confModel.getModuleName() + " {";
				i++;
			} else if (line.startsWith("namespace")) {
				line = "namespace \"" + confModel.getYangNamespace() + "\";";
				i++;
			} else if (line.startsWith("prefix")) {
				line = "prefix " + confModel.getPrefix() + ";";
				i++;
			}
			result.append(line);
			result.append("\n");
		}
		scanner.close();

		String prefix = confModel.getPrefix();
		if (prefix.equals(Constants.NONE))
			return result.toString();

		String prefixWithoutNfId = prefix.substring(0, prefix.lastIndexOf("-" + nfName));

		return result.toString().replaceAll("/" + prefixWithoutNfId + ":", "/" + prefix + ":")
				.replaceAll(" " + prefixWithoutNfId + ":", " " + prefix + ":")
				.replaceAll("\"" + prefixWithoutNfId + ":", "\"" + prefix + ":");
	}

	public static void findAndReplaceModuleNames(ConcurrentHashMap<String, String> compileYangs, String nfName) {

		compileYangs.forEach((yangFile, newModule) -> {

			String oldModule = newModule.substring(0, newModule.lastIndexOf("-" + nfName));
			LOG.info("Module/Sub-Module Transformation: [" + oldModule + ", " + newModule + "]");

			String cmd_module = "find /confd/apps/config/model -type f -name \\*yang | xargs sed -i 's/import "
					+ oldModule + " {/import " + newModule + " {/g'";

			String cmd_submodule = "find /confd/apps/config/model -type f -name \\*yang | xargs sed -i 's/belongs-to "
					+ oldModule + " {/belongs-to " + newModule + " {/g'";

			String cmd_include = "find /confd/apps/config/model -type f -name \\*yang | xargs sed -i 's/include "
					+ oldModule + ";/include " + newModule + ";/g'";

			String[] cmd = { cmd_include, cmd_module, cmd_submodule };
			exec(String.join(";", cmd));
		});
	}

	public static String removeModuleName(String configData,
			ConcurrentHashMap<String, ConfModelMetadata> mapOfConfModels) {

		Iterator<ConfModelMetadata> iterator = mapOfConfModels.values().iterator();
		while (iterator.hasNext()) {

			ConfModelMetadata model = iterator.next();
			if (configData.contains(model.getModuleName())) {
				LOG.debug("Removing module name: " + model.getModuleName());
				configData = configData.replaceAll(model.getModuleName() + ":", Constants.EMPTY_STRING);
			}
		}
		return configData;
	}

	public static String addNfIdToXmlns(String configData,
			ConcurrentHashMap<String, ConfModelMetadata> mapOfConfModels) {

		Iterator<ConfModelMetadata> iterator = mapOfConfModels.values().iterator();
		while (iterator.hasNext()) {

			ConfModelMetadata model = iterator.next();
			String nsWithNfId = model.getYangNamespace();

			// not a submodule
			if (!nsWithNfId.equals(Constants.NONE)) {

				String nsWithoutNfId = nsWithNfId.substring(0, nsWithNfId.lastIndexOf(":"));
				if (configData.contains("\"" + nsWithoutNfId + "\"")) {
					LOG.debug("Replacing namespace: \"" + nsWithoutNfId + "\" with new namespace (added nfId): \""
							+ nsWithNfId + "\"");
					configData = configData.replaceAll("\"" + nsWithoutNfId + "\"", "\"" + nsWithNfId + "\"");
				}
			}
		}
		return configData;
	}
}
