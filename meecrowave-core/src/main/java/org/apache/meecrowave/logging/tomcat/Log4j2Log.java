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
package org.apache.meecrowave.logging.tomcat;

import org.apache.juli.logging.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Log4j2Log implements Log {
    private final Logger delegate;

    public Log4j2Log(final String name) {
        delegate = LogManager.getLogger(name);
    }

    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    @Override
    public boolean isFatalEnabled() {
        return delegate.isFatalEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    @Override
    public void trace(final Object message) {
        delegate.trace(message);
    }

    @Override
    public void trace(final Object message, final Throwable t) {
        delegate.trace(message, t);
    }

    @Override
    public void debug(final Object message) {
        delegate.debug(message);
    }

    @Override
    public void debug(final Object message, final Throwable t) {
        delegate.debug(message, t);
    }

    @Override
    public void info(final Object message) {
        delegate.info(message);
    }

    @Override
    public void info(final Object message, final Throwable t) {
        delegate.info(message, t);
    }

    @Override
    public void warn(final Object message) {
        delegate.warn(message);
    }

    @Override
    public void warn(final Object message, final Throwable t) {
        delegate.warn(message, t);
    }

    @Override
    public void error(final Object message) {
        delegate.error(message);
    }

    @Override
    public void error(final Object message, final Throwable t) {
        delegate.error(message, t);
    }

    @Override
    public void fatal(final Object message) {
        delegate.fatal(message);
    }

    @Override
    public void fatal(final Object message, final Throwable t) {
        delegate.fatal(message, t);
    }
}
