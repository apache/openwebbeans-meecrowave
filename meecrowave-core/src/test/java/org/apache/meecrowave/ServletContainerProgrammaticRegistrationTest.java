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
package org.apache.meecrowave;

import org.junit.Test;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.junit.Assert.assertEquals;

// just a sample using multiparts
public class ServletContainerProgrammaticRegistrationTest {
    @Inject
    private ServletContext context;

    @Test
    public void configBinding() {
        try (final Meecrowave meecrowave = new Meecrowave(
                new Meecrowave.Builder()
                        .randomHttpPort()
                        .includePackages(ServletContainerProgrammaticRegistrationTest.class.getName()))
                .bake(c -> c.addServletContainerInitializer(new ProgrammaticInit(), emptySet()))) {
            meecrowave.inject(this);
            assertEquals("> yeah", String.valueOf(context.getAttribute("ServletContainerProgrammaticRegistrationTest")));
        }
    }

    @Dependent
    public static class ProgrammaticInit implements ServletContainerInitializer {
        @Inject
        private AService bean;

        @Override
        public void onStartup(final Set<Class<?>> set, final ServletContext servletContext) throws ServletException {
            servletContext.setAttribute(
                    "ServletContainerProgrammaticRegistrationTest", ">" + (bean != null ? bean.isOk() : "no"));
        }
    }

    @ApplicationScoped
    public static class AService {
        public String isOk() {
            return " yeah";
        }
    }
}
