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

import org.apache.catalina.realm.RealmBase;
import org.apache.meecrowave.io.IO;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;

import static org.junit.Assert.assertEquals;

public class PrincipalTest {
    @Test
    public void run() throws IOException {
        try (final Meecrowave container = new Meecrowave(new Meecrowave.Builder()
                .randomHttpPort()
                .includePackages("org.superbiz.app")
                .realm(new RealmBase() {
                    @Override
                    protected String getPassword(final String username) {
                        return "foo".equals(username) ? "pwd" : null;
                    }

                    @Override
                    protected Principal getPrincipal(final String username) {
                        return new MyPrincipal(username);
                    }
                }).loginConfig(new Meecrowave.LoginConfigBuilder()
                        .basic()
                        .realmName("basic realm"))
                .securityConstraints(new Meecrowave.SecurityConstaintBuilder()
                        .authConstraint(true)
                        .addAuthRole("**")
                        .addCollection("secured", "/*")))
                .bake()) {
            assertEquals(
                    "org.apache.meecrowave.PrincipalTest$MyPrincipal_foo  org.apache.webbeans.custom.Principal_foo",
                    slurp(new URL("http://localhost:" + container.getConfiguration().getHttpPort() + "/api/test/principal")));
        }
    }

    private String slurp(final URL url) throws IOException {
        final URLConnection is = HttpURLConnection.class.cast(url.openConnection());
        is.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString("foo:pwd".getBytes(StandardCharsets.UTF_8)));
        return IO.toString(is.getInputStream());
    }

    private static class MyPrincipal implements Principal {
        private final String name;

        private MyPrincipal(final String username) {
            this.name = username;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
