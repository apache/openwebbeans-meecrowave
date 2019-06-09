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
package org.apache.meecrowave.proxy.servlet.configuration;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.enterprise.event.NotificationOptions;
import javax.json.JsonObject;
import javax.json.bind.annotation.JsonbTransient;
import javax.ws.rs.client.Client;

public class Routes {
    public JsonObject extensions; // placeholder for custom metadata usable in observers
    public Route defaultRoute;
    public Collection<Route> routes;

    @Override
    public String toString() {
        return "Routes{routes=" + routes + '}';
    }

    public static class Route {
        public String id;
        public RequestConfiguration requestConfiguration;
        public ResponseConfiguration responseConfiguration;
        public ClientConfiguration clientConfiguration;
        public JsonObject extensions; // placeholder for custom metadata usable in observers

        @JsonbTransient
        public Client client;

        @JsonbTransient
        public ExecutorService executor;

        @JsonbTransient
        public NotificationOptions notificationOptions;

        @Override
        public String toString() {
            return "Route{id='" + id + "', requestConfiguration=" + requestConfiguration + ", responseConfiguration=" + responseConfiguration + '}';
        }
    }

    public static class ExecutorConfiguration {
        public Integer core;
        public Integer max;
        public Long keepAlive;
        public Long shutdownTimeout;

        @Override
        public String toString() {
            return "ExecutorConfiguration{" +
                    "core=" + core +
                    ", max=" + max +
                    ", keepAlive=" + keepAlive +
                    ", shutdownTimeout=" + shutdownTimeout +
                    '}';
        }
    }

    public static class TimeoutConfiguration {
        public Long read;
        public Long connect;
        public Long execution;

        @Override
        public String toString() {
            return "TimeoutConfiguration{" +
                    "read=" + read +
                    ", connect=" + connect +
                    '}';
        }
    }

    public static class ClientConfiguration {
        public TimeoutConfiguration timeouts;
        public ExecutorConfiguration executor;
        public SslConfiguration sslConfiguration;

        @Override
        public String toString() {
            return "ClientConfiguration{" +
                    "timeouts=" + timeouts +
                    ", executor=" + executor +
                    '}';
        }
    }

    public static class SslConfiguration {
        public boolean acceptAnyCertificate;
        public String keystoreLocation;
        public String keystoreType;
        public String keystorePassword;
        public String truststoreType;
        public Collection<String> verifiedHostnames;

        @Override
        public String toString() {
            return "SslConfiguration{" +
                    "acceptAnyCertificate=" + acceptAnyCertificate +
                    ", keystoreLocation='" + keystoreLocation + '\'' +
                    ", keystoreType='" + keystoreType + '\'' +
                    ", keystorePassword='" + keystorePassword + '\'' +
                    ", truststoreType='" + truststoreType + '\'' +
                    ", verifiedHostnames=" + verifiedHostnames +
                    '}';
        }
    }

    public static class ResponseConfiguration {
        public String target;
        public Collection<String> skippedHeaders;
        public Collection<String> skippedCookies;

        @Override
        public String toString() {
            return "ResponseConfiguration{target='" + target + "'}";
        }
    }

    public static class RequestConfiguration {
        public String method;
        public String prefix;
        public Map<String, String> addedHeaders;
        public Collection<String> skippedHeaders;
        public Collection<String> skippedCookies;

        @Override
        public String toString() {
            return "RequestConfiguration{method='" + method + "', prefix='" + prefix + "'}";
        }
    }
}
