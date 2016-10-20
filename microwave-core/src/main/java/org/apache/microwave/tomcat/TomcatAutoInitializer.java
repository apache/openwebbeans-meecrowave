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
package org.apache.microwave.tomcat;

import org.apache.catalina.servlets.DefaultServlet;
import org.apache.microwave.Microwave;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import java.util.Set;

public class TomcatAutoInitializer implements ServletContainerInitializer {
    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) throws ServletException {
        final Microwave.Builder builder = Microwave.Builder.class.cast(ctx.getAttribute("microwave.configuration"));
        if (!builder.isTomcatAutoSetup()) {
            return;
        }

        final ServletRegistration.Dynamic def = ctx.addServlet("default", DefaultServlet.class);
        def.setInitParameter("listings", "false");
        def.setInitParameter("debug", "0");
        def.setLoadOnStartup(1);
        def.addMapping("/");

        // TODO: mimetypes
    }
}
