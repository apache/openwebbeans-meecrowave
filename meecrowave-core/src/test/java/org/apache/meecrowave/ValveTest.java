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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.ServletException;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.inject.OWBInjector;
import org.junit.Test;

public class ValveTest {
    @Test
    public void inject() {
        try (final Meecrowave meecrowave = new Meecrowave(
                new Meecrowave.Builder()
                        .randomHttpPort()
                        .property("valves.test2._className", TestValve.class.getName())
                        .property("valves.test2._order", "2")
                        .property("valves.test2.config", "configured too")
                        .property("valves.test._className", TestValve.class.getName())
                        .property("valves.test._order", "1")
                        .property("valves.test.config", "configured :)")
                        .includePackages(ValveTest.class.getName())).bake()) {
            final Valve[] valves = meecrowave.getTomcat()
                                       .getHost()
                                       .getPipeline()
                                       .getValves();
            assertEquals(4 /*test1 test2 error standardhost*/, valves.length);
            assertEquals("configured :)", TestValve.class.cast(valves[0]).config);
            assertEquals("configured too", TestValve.class.cast(valves[1]).config);
        }
    }

    public static class TestValve extends ValveBase {
        private String config;

        @Override
        public void invoke(final Request request, final Response response) throws IOException, ServletException {
            getNext().invoke(request, response);
        }
    }
}
