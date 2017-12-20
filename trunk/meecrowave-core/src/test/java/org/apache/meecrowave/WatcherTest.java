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
import org.superbiz.app.Bounced;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.File;
import java.io.IOException;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static org.junit.Assert.fail;

// you can need to mvn test-compile before running this test if it was executed once already
public class WatcherTest {
    @Test
    public void watch() throws IOException {
        final String bouncedFile = "target/test-classes/" + Bounced.class.getName().replace(".", "/") + ".class";
        final Meecrowave.Builder builder = new Meecrowave.Builder() {{
            setWatcherBouncing(250);
        }}.randomHttpPort().includePackages(Bounced.class.getPackage().getName());
        try (final Meecrowave meecrowave = new Meecrowave(builder).bake()) {
            final String endpoint = String.format("http://localhost:%d/api/bounced", builder.getHttpPort());

            final Client client = ClientBuilder.newClient();
            try {
                final String original = client.target(endpoint)
                        .request(TEXT_PLAIN_TYPE)
                        .get(String.class);

                try {
                    Thread.sleep(1000);
                } catch (final InterruptedException e) {
                    fail(e.getMessage());
                }

                // now replace it by "replaced", not we don't reload classes yet so we just check we redeployed, should we integ with fakereplace?
                new File(bouncedFile).setLastModified(System.currentTimeMillis());

                long max = 15000 /* deployment max */ + 250 /* bouncing */ * 4 /* wait in watcher + 3 loops in bouncer */;
                while (max > 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (final InterruptedException e) {
                        fail(e.getMessage());
                    }
                    max -= 1000;

                    try {
                        final String reloaded = client.target(endpoint)
                                .request(TEXT_PLAIN_TYPE)
                                .get(String.class);
                        if (!original.equals(reloaded)) {
                            return;
                        }
                        System.out.println("Got " + reloaded + " instead of something more recent.");
                    } catch (final WebApplicationException | ProcessingException e) {
                        // redeploying pby
                    }
                }
                fail("didn't see Bounced endpoint reloaded");
            } finally {
                client.close();
            }
        }
    }
}
