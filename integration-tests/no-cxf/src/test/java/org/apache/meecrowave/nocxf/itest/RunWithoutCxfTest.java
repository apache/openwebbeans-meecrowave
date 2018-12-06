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
package org.apache.meecrowave.nocxf.itest;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.meecrowave.Meecrowave;
import org.junit.Test;

public class RunWithoutCxfTest {

    @Test
    public void runServlet() throws IOException {
        try (final Meecrowave container = new Meecrowave(new Meecrowave.Builder() {{
            addServletContextInitializer((c, ctx) -> ctx.addServlet("test", new HttpServlet() {
                @Override
                protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
                    resp.getWriter()
                        .write("servlet :)");
                }
            }).addMapping("/test"));
        }}.randomHttpPort()).bake()) {
            final String url = "http://localhost:" + container.getConfiguration().getHttpPort() + "/test";
            final StringWriter output = new StringWriter();
            try (final BufferedReader stream = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
                output.write(stream.readLine());
            }
            assertEquals("servlet :)", output.toString().trim());
        }
    }
}
