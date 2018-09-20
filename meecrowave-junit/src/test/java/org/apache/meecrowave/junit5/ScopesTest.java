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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.spi.Context;

import org.apache.webbeans.config.WebBeansContext;
import org.junit.jupiter.api.Test;

@MeecrowaveConfig
@Scopes(scopes = RequestScoped.class)
class ScopesTest {
    @Test
    @Scopes
    void overrided() {
        assertNull(getContext());
    }

    @Test
    void clazz() {
        assertTrue(getContext().isActive());
    }

    private Context getContext() {
        return WebBeansContext.currentInstance()
                              .getContextsService()
                              .getCurrentContext(RequestScoped.class, false);
    }
}
