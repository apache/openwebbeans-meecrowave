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
package org.apache.microwave.junit;

import org.apache.microwave.Microwave;

public class MicrowaveRule extends MicrowaveRuleBase {
    private final Microwave.Builder configuration;
    private final String context;

    public MicrowaveRule() {
        this(new Microwave.Builder().randomHttpPort(), "");
    }

    public MicrowaveRule(final Microwave.Builder configuration, final String context) {
        this.configuration = configuration;
        this.context = context;
    }

    @Override
    public Microwave.Builder getConfiguration() {
        return configuration;
    }

    @Override
    protected AutoCloseable onStart() {
        return new Microwave(configuration).bake(context);
    }
}
