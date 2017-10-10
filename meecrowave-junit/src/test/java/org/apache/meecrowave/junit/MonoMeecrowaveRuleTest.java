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
package org.apache.meecrowave.junit;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.io.IO;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.app.MyAppClass;
import org.app.MyReqClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(MonoMeecrowave.Runner.class)
public class MonoMeecrowaveRuleTest {
    /* or
    @ClassRule
    public static final MonoMeecrowave.Rule RULE = new MonoMeecrowave.Rule();
    */

    private static int count = 0;

    @ConfigurationInject
    private Meecrowave.Builder config;

    private @Inject MyAppClass appClass;
    private @Inject MyReqClass reqClass;


    @Test
    public void test() throws IOException {
        assertEquals("simple", slurp(new URL("http://localhost:" + config.getHttpPort() + "/api/test")));

        testScopes();
    }

    @Test
    public void anotherTest() throws IOException {
        assertEquals("simple", slurp(new URL("http://localhost:" + config.getHttpPort() + "/api/test")));

        testScopes();
    }

    private void testScopes() {
        count++;

        if (count == 2) {
            assertEquals("beenhere", appClass.getX());
            assertEquals("init", reqClass.getX());
        }
        else {
            reqClass.setX("beenhere");
            appClass.setX("beenhere");
        }
    }


    private String slurp(final URL url) {
        try (final InputStream is = url.openStream()) {
            return IO.toString(is);
        } catch (final IOException e) {
            fail(e.getMessage());
        }
        return null;
    }

}
