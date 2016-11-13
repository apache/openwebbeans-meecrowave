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
package org.apache.meecrowave.logging.log4j2;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.DefaultLogEventFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;

import java.util.List;

public class MeecrowaveLogEventFactory extends DefaultLogEventFactory {
    @Override
    public LogEvent createEvent(final String loggerName, final Marker marker, final String fqcn,
                                final Level level, final Message data, final List<Property> properties,
                                final Throwable t) {
        return new MeecrowaveLog4jLogEvent(loggerName, marker, fqcn, level, data, properties, t);
    }

    public static class MeecrowaveLog4jLogEvent extends Log4jLogEvent {
        private StackTraceElement source;

        public MeecrowaveLog4jLogEvent(final String loggerName, final Marker marker, final String fqcn, final Level level,
                                      final Message data, final List<Property> properties, final Throwable t) {
            super(loggerName, marker, fqcn, level, data, properties, t);
        }

        @Override
        public StackTraceElement getSource() { // this doesn't work OOTB and no config available
            if (source != null) {
                return source;
            }
            if (getLoggerFqcn() == null || !isIncludeLocation()) {
                return null;
            }

            final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            for (int i = stackTrace.length - 1; i > 0; i--) {
                final String className = stackTrace[i].getClassName();
                if (getLoggerFqcn().equals(className) && stackTrace.length > i + 1) {
                    source = stackTrace[i + 1];
                    if (i + 3 < stackTrace.length && stackTrace[i + 2].getClassName().equals("org.apache.meecrowave.logging.tomcat.LogFacade")) {
                        source = stackTrace[i + 3];
                    }
                    break;
                }
            }
            return source;
        }
    }
}
