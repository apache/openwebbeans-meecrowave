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
package org.apache.meecrowave.proxy.servlet.meecrowave;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletRegistration;

import org.apache.catalina.Context;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.proxy.servlet.front.cdi.CDIProxyServlet;
import org.apache.meecrowave.runner.Cli;
import org.apache.meecrowave.runner.cli.CliOption;

// IMPORTANT: don't make this class depending on meecrowave, cxf or our internals, use setup class
public class ProxyServletSetup implements Meecrowave.MeecrowaveAwareContextCustomizer {
    private Meecrowave instance;

    @Override
    public void accept(final Context context) {
        final Configuration config = instance.getConfiguration().getExtension(Configuration.class);
        if (config.skip) {
            return;
        }
        context.addServletContainerInitializer((c, ctx) -> {
            final ServletRegistration.Dynamic servlet = ctx.addServlet("meecrowave-proxy-servlet", CDIProxyServlet.class);
            servlet.setLoadOnStartup(1);
            servlet.setAsyncSupported(true);
            servlet.addMapping(config.mapping);
            if (config.multipart) {
                servlet.setMultipartConfig(new MultipartConfigElement(
                        config.multipartLocation,
                        config.multipartMaxFileSize,
                        config.multipartMaxRequestSize,
                        config.multipartFileSizeThreshold));
            }
            servlet.setInitParameter("mapping", config.mapping);
            servlet.setInitParameter("configuration", config.configuration);
            servlet.setInitParameter("read.timeout", config.readTimeout);
            servlet.setInitParameter("connect.timeout", config.connectTimeout);
            servlet.setInitParameter("executor.core", config.threadPoolCoreSize);
            servlet.setInitParameter("executor.max", config.threadPoolMaxSize);
            servlet.setInitParameter("executor.keepAlive", config.threadPoolKeepAlive);
            servlet.setInitParameter("shutdown.timeout", config.shutdownTimeout);
            servlet.setInitParameter("async.timeout", config.asyncTimeout);
        }, null);
    }

    @Override
    public void setMeecrowave(final Meecrowave meecrowave) {
        this.instance = meecrowave;
    }

    public static class Configuration implements Cli.Options {
        @CliOption(name = "proxy-skip", description = "Should default setup be ignored")
        private boolean skip = false;

        @CliOption(name = "proxy-mapping", description = "Where to bind the proxy (url pattern).")
        private String mapping = "/*";

        @CliOption(name = "proxy-multipart", description = "Is multipart explicit.")
        private boolean multipart = true;

        @CliOption(name = "proxy-multipart-maxfilesize", description = "Max file size for multipart requests.")
        private long multipartMaxFileSize = -1;

        @CliOption(name = "proxy-multipart-maxrequestsize", description = "Max request size for multipart requests.")
        private long multipartMaxRequestSize = -1;

        @CliOption(name = "proxy-multipart-maxfilesizethreshold", description = "Max file size threshold for multipart requests.")
        private int multipartFileSizeThreshold = 0;

        @CliOption(name = "proxy-multipart-location", description = "The multipart temporary folder.")
        private String multipartLocation = "";

        @CliOption(name = "proxy-configuration", description = "The route file.")
        private String configuration = "conf/proxy.json";

        @CliOption(name = "proxy-shutdown-timeout", description = "How long the shutdown will wait for in progress tasks (executor).")
        private String shutdownTimeout = "30000";

        @CliOption(name = "proxy-executor-core", description = "HTTP client thread pool core size.")
        private String threadPoolCoreSize = "64";

        @CliOption(name = "proxy-executor-max", description = "HTTP client thread pool max size.")
        private String threadPoolMaxSize = "512";

        @CliOption(name = "proxy-executor-max", description = "HTTP client thread pool keep alive duration (in ms).")
        private String threadPoolKeepAlive = "60000";

        @CliOption(name = "proxy-read-timeout", description = "HTTP client read timeout.")
        private String readTimeout = "30000";

        @CliOption(name = "proxy-connect-timeout", description = "HTTP client connect timeout.")
        private String connectTimeout = "30000";

        @CliOption(name = "proxy-async-timeout", description = "Asynchronous execution timeout.")
        private String asyncTimeout = "30000";
    }
}
