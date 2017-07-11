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
package org.apache.meecrowave.api;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.connector.Connector;

public abstract class ListeningBase {
    private final Connector connector;
    private final Context context;
    private final Host host;

    ListeningBase(final Connector connector, final Host host, final Context context) {
        this.connector = connector;
        this.host = host;
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public Connector getConnector() {
        return connector;
    }

    public Host getHost() {
        return host;
    }

    public String getFirstBase() {
        return (connector.getSecure() ? "https" : "http") + "://" +
                host.getName() + (connector.getPort() > 0 ? ":" + connector.getPort() : "") +
                context.getPath();
    }
}
