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
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import java.io.IOException;
import java.net.Socket;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class NoConnectorTest {
    @Test
    public void run() {
        final Meecrowave.Builder config = new Meecrowave.Builder();
        config.setSkipHttp(true);
        try (final Meecrowave meecrowave = new Meecrowave(config.includePackages(NoConnectorTest.class.getName())).bake()) {
            final BeanManager beanManager = CDI.current().getBeanManager();
            assertEquals("yeah", SomeBean.class.cast(
                    beanManager.getReference(
                            beanManager.resolve(beanManager.getBeans(SomeBean.class)),
                            SomeBean.class,
                            beanManager.createCreationalContext(null)))
                    .get());
            IntStream.of(config.getHttpPort(), config.getHttpsPort()).forEach(port -> {
                try (final Socket socket = new Socket("localhost", port)) {
                    fail("port " + port + " is opened");
                } catch (final IOException e) {
                    // ok
                }
            });
            assertEquals(0, meecrowave.getTomcat().getService().findConnectors().length);
        }
    }

    @ApplicationScoped
    public static class SomeBean {
        String get() {
            return "yeah";
        }
    }
}
