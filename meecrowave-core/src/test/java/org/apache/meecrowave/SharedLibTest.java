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

import org.apache.meecrowave.io.IO;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;

public class SharedLibTest {
    @Test
    public void run() throws IOException {
        try (final Meecrowave container = new Meecrowave(new Meecrowave.Builder()
                .randomHttpPort()
                .includePackages("org.superbiz.app,org.apache.deltaspike")
                .sharedLibraries("target/shared-test"))
                .bake()) {
            assertEquals(
                    "org.apache.deltaspike.core.api.config.ConfigProperty",
                    slurp(new URL("http://localhost:" + container.getConfiguration().getHttpPort() + "/api/test/load/true")));
        }
    }

    private String slurp(final URL url) throws IOException {
        return IO.toString(HttpURLConnection.class.cast(url.openConnection()).getInputStream());
    }
}
