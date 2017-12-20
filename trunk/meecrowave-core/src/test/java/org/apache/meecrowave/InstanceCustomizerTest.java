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

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.junit.Test;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.junit.Assert.assertEquals;

public class InstanceCustomizerTest {
    @Test
    public void instanceCustomizer() throws IOException {
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder()
                .randomHttpPort()
                .instanceCustomizer(t -> t.getHost().getPipeline().addValve(new ValveBase() {
                    @Override
                    public void invoke(final Request request, final Response response) throws IOException, ServletException {
                        response.getWriter().write("custom");
                    }
                }))
                .includePackages(InstanceCustomizerTest.class.getName())).bake()) {
            try (final InputStream stream = new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/whatever").openStream()) {
                assertEquals("custom", Streams.asString(stream, "UTF-8"));
            }
        }
    }
}
