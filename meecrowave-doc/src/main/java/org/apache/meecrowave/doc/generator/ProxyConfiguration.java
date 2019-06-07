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
package org.apache.meecrowave.doc.generator;

import static java.util.stream.Collectors.joining;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.stream.Stream;

import org.apache.meecrowave.proxy.servlet.meecrowave.ProxyServletSetup;
import org.apache.meecrowave.runner.cli.CliOption;

public class ProxyConfiguration extends BaseGenerator {
    @Override
    protected String generate() {
        return super.tableConfig() + "|===\n|Name|Description\n" +
                Stream.of(ProxyServletSetup.Configuration.class.getDeclaredFields())
                        .filter(f -> f.isAnnotationPresent(CliOption.class))
                        .sorted(Comparator.comparing(Field::getName))
                        .map(f -> f.getAnnotation(CliOption.class))
                        .map(opt -> "|--" + opt.name() + "|" + opt.description())
                        .collect(joining("\n")) + "\n|===\n";
    }
}
