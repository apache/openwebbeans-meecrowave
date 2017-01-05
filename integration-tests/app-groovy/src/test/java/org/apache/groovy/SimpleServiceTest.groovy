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
package org.apache.groovy

import org.apache.meecrowave.Meecrowave
import org.junit.Test

import javax.ws.rs.client.ClientBuilder

import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE
import static org.junit.Assert.assertEquals

class SimpleServiceTest {
    @Test
    void groovyCp() {
        def container = new Meecrowave(new Meecrowave.Builder().randomHttpPort()).bake()
        try {
            def client = ClientBuilder.newClient()
            assertEquals("simple", client.target("http://localhost:" + container.configuration.httpPort + "/simple")
                    .request(TEXT_PLAIN_TYPE)
                    .get(String))
            client.close()
        } finally {
            container.close()
        }
    }

    @Test
    void groovyLoader() {
        def loader = new GroovyClassLoader()
        loader.parseClass(new File("src/test/parsed/SimpleService2.groovy"))

        def thread = Thread.currentThread()
        def old = thread.contextClassLoader
        thread.setContextClassLoader(loader)
        def container = new Meecrowave(new Meecrowave.Builder().randomHttpPort()).bake()
        try {
            def client = ClientBuilder.newClient()
            assertEquals("simple2", client.target("http://localhost:" + container.configuration.httpPort + "/simple2")
                    .request(TEXT_PLAIN_TYPE)
                    .get(String))
            client.close()
        } finally {
            container.close()
            loader.close()
            thread.setContextClassLoader(old)
        }
    }
}
