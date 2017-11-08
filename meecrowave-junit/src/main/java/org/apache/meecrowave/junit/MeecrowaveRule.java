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

import java.net.URL;
import java.net.URLClassLoader;

import org.apache.meecrowave.Meecrowave;

public class MeecrowaveRule extends MeecrowaveRuleBase<MeecrowaveRule> {
    private final Meecrowave.Builder configuration;
    private final String context;

    private ClassLoader meecrowaveCL;

    public MeecrowaveRule() {
        this(new Meecrowave.Builder().randomHttpPort(), "");
    }

    public MeecrowaveRule(final Meecrowave.Builder configuration, final String context) {
        this.configuration = configuration;
        this.context = context;
    }

    @Override
    public Meecrowave.Builder getConfiguration() {
        return configuration;
    }

    @Override
    protected AutoCloseable onStart() {
        return new Meecrowave(configuration).bake(context);
    }

    @Override
    protected ClassLoader getClassLoader() {
        if (meecrowaveCL == null) {
            ClassLoader currentCL = Thread.currentThread().getContextClassLoader();
            if (currentCL == null) {
                this.getClass().getClassLoader();
            }

            meecrowaveCL = new URLClassLoader(new URL[0], currentCL);
        }
        return meecrowaveCL;
    }
}
