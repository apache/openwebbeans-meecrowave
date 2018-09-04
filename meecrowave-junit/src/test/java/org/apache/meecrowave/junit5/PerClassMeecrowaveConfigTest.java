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
package org.apache.meecrowave.junit5;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.io.IO;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(PER_CLASS)
@MeecrowaveConfig(scanningPackageIncludes = "org.apache.meecrowave.junit5.PerClassMeecrowaveConfigTest")
class PerClassMeecrowaveConfigTest {
    @ConfigurationInject
    private Meecrowave.Builder config;

    @Inject
    private Bean bean;

    private static Bean instance;

    @Test
    void m1() {
        doTest();
    }

    @Test
    void m2() {
        doTest();
    }

    private void doTest() {
        if (instance == null) {
            first();
        } else {
            second();
        }
    }

    private void first() {
        assertEquals("ok", bean.get());
        instance = bean;
    }

    private void second() {
        assertSame(instance, bean);
    }

    private String slurp(final URL url) {
        try (final InputStream is = url.openStream()) {
            return IO.toString(is);
        } catch (final IOException e) {
            fail(e.getMessage());
        }
        return null;
    }

    @ApplicationScoped
    public static class Bean {
        String get() {
            return "ok";
        }
    }
}
