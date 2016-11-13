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
package org.apache.meecrowave.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

public class MeecrowavePlugin implements Plugin<Project> {
    public static final String NAME = "meecrowave";

    @Override
    public void apply(final Project project) {
        project.getExtensions().create(NAME, MeecrowaveExtension.class);

        project.afterEvaluate(actionProject -> {
            final MeecrowaveExtension extension = MeecrowaveExtension.class.cast(actionProject.getExtensions().findByName(NAME));
            if (extension != null && extension.isSkipMavenCentral()) {
                return;
            }
            actionProject.getRepositories().mavenCentral();
        });

        final Configuration configuration = project.getConfigurations().maybeCreate(NAME);
        configuration.getIncoming().beforeResolve(resolvableDependencies -> {
            String version;
            try {
                final String resource = "META-INF/maven/org.apache.meecrowave/meecrowave-gradle-plugin/pom.properties";
                try (final InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
                    final Properties p = new Properties();
                    p.load(is);
                    version = p.getProperty("version");
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }

            final DependencyHandler dependencyHandler = project.getDependencies();
            final DependencySet dependencies = configuration.getDependencies();
            dependencies.add(dependencyHandler.create("org.apache.meecrowave:meecrowave-core:" + version));
        });

        project.task(new HashMap<String, Object>() {{
            put("type", MeecrowaveTask.class);
            put("group", "Embedded Application Server");
            put("description", "Starts a meecrowave!");
        }}, NAME);
    }
}
