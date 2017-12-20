/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.meecrowave.tomcat;

import org.apache.catalina.valves.AbstractAccessLogValve;
import org.apache.meecrowave.logging.tomcat.LogFacade;

import java.io.CharArrayWriter;

public class LoggingAccessLogPattern extends AbstractAccessLogValve {
    private final LogFacade logger;

    public LoggingAccessLogPattern(final String pattern) {
        logger = new LogFacade(LoggingAccessLogPattern.class.getName());
        setAsyncSupported(true);
        setPattern(pattern);
    }

    @Override
    protected void log(final CharArrayWriter message) {
        logger.info(message);
    }
}
