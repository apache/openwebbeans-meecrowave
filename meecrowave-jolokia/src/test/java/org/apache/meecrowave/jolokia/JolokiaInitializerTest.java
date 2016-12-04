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
package org.apache.meecrowave.jolokia;

import org.apache.commons.io.IOUtils;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class JolokiaInitializerTest {
    @ClassRule
    public static final MeecrowaveRule RULE = new MeecrowaveRule();

    @Test
    public void run() throws IOException {
        final String actual = IOUtils.toString(
                new URL("http://localhost:" + RULE.getConfiguration().getHttpPort() + "/jolokia/read/java.lang:type=Memory/HeapMemoryUsage"),
                StandardCharsets.UTF_8);
        assertTrue(actual, actual.contains("\"status\":200"));
        assertTrue(actual, actual.contains("\"mbean\":\"java.lang:type=Memory\""));
    }
}
