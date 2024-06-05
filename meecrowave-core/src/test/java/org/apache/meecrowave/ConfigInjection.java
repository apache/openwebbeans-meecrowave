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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import jakarta.inject.Inject;

import org.apache.meecrowave.configuration.Configuration;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.inject.OWBInjector;
import org.junit.Test;

public class ConfigInjection {
    @Inject
    private Configuration configuration;

    @Test
    public void inject() {
        try (final Meecrowave meecrowave = new Meecrowave(
                new Meecrowave.Builder()
                        .randomHttpPort()
                        .skipHttp(true)
                        .includePackages(ConfigInjection.class.getName())).bake()) {
            OWBInjector.inject(WebBeansContext.currentInstance().getBeanManagerImpl(), this, null);
            assertNotNull(configuration);
            assertEquals(ConfigInjection.class.getName(), configuration.getScanningPackageIncludes());
        }
    }
}
