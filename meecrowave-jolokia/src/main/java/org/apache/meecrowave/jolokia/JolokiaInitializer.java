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
import org.jolokia.server.core.http.AgentServlet;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import java.util.Set;

import static java.util.Optional.ofNullable;

public class JolokiaInitializer implements ServletContainerInitializer {
    @Override
    public void onStartup(final Set<Class<?>> set, final ServletContext servletContext) throws ServletException {
        final Meecrowave.Builder builder = Meecrowave.Builder.class.cast(servletContext.getAttribute("meecrowave.configuration"));
        final ServletRegistration.Dynamic jolokia = servletContext.addServlet("jolokia", AgentServlet.class);
        jolokia.setLoadOnStartup(1);
        final String mapping = ofNullable(builder.getProperties())
                .map(p -> p.getProperty("jolokia.web.mapping"))
                .orElse("/jolokia/*");
        jolokia.addMapping(mapping);
        servletContext.log("Installed Jolokia on " + mapping);
    }
}
