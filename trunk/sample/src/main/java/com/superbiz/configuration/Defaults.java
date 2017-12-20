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
package com.superbiz.configuration;

import org.apache.meecrowave.runner.Cli;
import org.apache.meecrowave.runner.cli.CliOption;

public class Defaults implements Cli.Options {
    @CliOption(name = "app-default-name", description = "The name used to say hello if not set")
    private String name = "default value";

    public String getName() {
        return name;
    }
}
