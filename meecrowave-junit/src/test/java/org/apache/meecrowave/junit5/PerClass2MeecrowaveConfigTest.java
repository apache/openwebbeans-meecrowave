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


import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit5.bean.SomeCommonInterface;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
@MeecrowaveConfig(scanningPackageIncludes = "org.apache.meecrowave.junit5.PerClass2MeecrowaveConfigTest")
class PerClass2MeecrowaveConfigTest {
    @ConfigurationInject
    private Meecrowave.Builder config;


    private @Inject SomeCommonInterface bigOtherOracle;

    @Test
    public void testBeanPickup() throws Exception {
        assertEquals(43, bigOtherOracle.meaningOfLife());
        Thread.sleep(50L);
    }

    @Test
    public void testBeanPickup2() throws Exception {
        assertEquals(43, bigOtherOracle.meaningOfLife());
        Thread.sleep(50L);
    }

    @Test
    public void testBeanPickup3() throws Exception {
        assertEquals(43, bigOtherOracle.meaningOfLife());
        Thread.sleep(50L);
    }

    @Test
    public void testBeanPickup4() throws Exception {
        assertEquals(43, bigOtherOracle.meaningOfLife());
        Thread.sleep(50L);
    }



    @ApplicationScoped
    public static class MyCommonImpl2 implements SomeCommonInterface {

        @Override
        public int meaningOfLife() {
            return 43;
        }
    }
}
