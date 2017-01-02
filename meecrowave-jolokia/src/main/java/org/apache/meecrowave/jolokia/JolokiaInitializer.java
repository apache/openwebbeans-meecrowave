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
package org.apache.meecrowave.jolokia;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.runner.Cli;
import org.apache.meecrowave.runner.cli.CliOption;
import org.jolokia.http.AgentServlet;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import java.util.Set;

import static java.util.Optional.ofNullable;

public class JolokiaInitializer implements ServletContainerInitializer {
    @Override
    public void onStartup(final Set<Class<?>> set, final ServletContext servletContext) throws ServletException {
        final Meecrowave.Builder config = Meecrowave.Builder.class.cast(servletContext.getAttribute("meecrowave.configuration"));
        final JolokiaConfiguration configuration = config.getExtension(JolokiaConfiguration.class);
        if (!configuration.isActive()) {
            return;
        }
        try { // if hawtio is setup skip it
            servletContext.getClassLoader().loadClass("io.hawt.web.UserServlet");
            if (config.getExtension(HawtioInitializer.HawtioConfiguration.class).isActive()) {
                return;
            }
        } catch (final ClassNotFoundException e) {
            // that's what we want
        }

        final ServletRegistration.Dynamic jolokia = servletContext.addServlet("jolokia", AgentServlet.class);
        jolokia.setLoadOnStartup(1);
        final String mapping = ofNullable(configuration.getMapping()).orElse("/jolokia/*");
        jolokia.addMapping(mapping);
        servletContext.log("Installed Jolokia on " + mapping);
    }

    public static class JolokiaConfiguration implements Cli.Options {
        @CliOption(name = "jolokia-mapping", description = "Jolokia endpoint")
        private String mapping;

        @CliOption(name = "jolokia-active", description = "Should Jolokia be deployed (only if hawt.io is not)")
        private boolean active = true;

        public boolean isActive() {
            return active;
        }

        public void setActive(final boolean active) {
            this.active = active;
        }

        public String getMapping() {
            return mapping;
        }

        public void setMapping(final String mapping) {
            this.mapping = mapping;
        }
    }
}
