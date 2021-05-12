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

import org.apache.catalina.Context;
import org.apache.meecrowave.Meecrowave;

import java.io.File;
import java.util.function.Consumer;

public class MeecrowaveRule extends MeecrowaveRuleBase<MeecrowaveRule> {
    private final Meecrowave.Builder configuration;
    private final String context;
    private File docBase;
    private Consumer<Context> customizer;

    public MeecrowaveRule() {
        this(new Meecrowave.Builder().randomHttpPort(), "");
    }

    public MeecrowaveRule(final Meecrowave.Builder configuration, final String context) {
        this.configuration = configuration;
        this.context = context;
    }

    public MeecrowaveRule setDocBase(File docBase) {
        this.docBase = docBase;
        return this;
    }

    public MeecrowaveRule setCustomizer(Consumer<Context> customizer) {
        this.customizer = customizer;
        return this;
    }

    @Override
    public Meecrowave.Builder getConfiguration() {
        return configuration;
    }

    @Override
    protected AutoCloseable onStart() {
        final Meecrowave meecrowave = new Meecrowave(configuration);
        meecrowave.start();
        meecrowave.deployClasspath(new Meecrowave.DeploymentMeta(context, docBase, customizer, null));
        return meecrowave;
    }
}
