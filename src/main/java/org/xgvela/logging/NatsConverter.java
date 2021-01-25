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

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;

@Plugin(name = "NatsConverter", category = "Converter")
@ConverterKeys({ "corrId" })
public final class NatsConverter extends LogEventPatternConverter {

	protected NatsConverter(String name, String style) {
		super(name, style);
	}

	public static NatsConverter newInstance(final String[] options) {
		return new NatsConverter("corrId", "corrId");
	}

	@Override
	public void format(LogEvent event, StringBuilder toAppendTo) {
		toAppendTo.append(getCorrId());
	}

	private String getCorrId() {
		return "7717";
	}

}
