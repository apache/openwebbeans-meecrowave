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
package org.apache.meecrowave.jpa.internal;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.app.JPADao;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

import javax.inject.Inject;

import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;

public class JpaExtensionTest {
    @Rule
    public final TestRule rule = outerRule((base, description) -> new Statement() {
        @Override
        public void evaluate() throws Throwable {
            final String jVersion = System.getProperty("java.version");
            Assume.assumeFalse(jVersion.startsWith("9-") || jVersion.startsWith("1.9")); // openjpa loops on java 9 bytecode
            base.evaluate();
        }
    }).around(new MeecrowaveRule(
            new Meecrowave.Builder().randomHttpPort()
                    .property("jpa.property.openjpa.RuntimeUnenhancedClasses", "supported")
                    .property("jpa.property.openjpa.jdbc.SynchronizeMappings", "buildSchema"),
            "")
            .inject(this));

    @Inject
    private JPADao service;

    @Test
    public void run() {
        final JPADao.User u = new JPADao.User();
        u.setName("test");
        assertEquals("test", service.find(service.save(u).getId()).getName());
    }
}
