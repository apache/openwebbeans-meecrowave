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
package org.apache.meecrowave.proxy.servlet.service;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.spi.JsonProvider;

import org.apache.meecrowave.proxy.servlet.configuration.Routes;

public class ConfigurationLoader {
    private final String path;

    private Routes routes;

    public ConfigurationLoader(final String path) {
        this.path = path;
    }

    public Optional<Routes> load() {
        final SimpleSubstitutor simpleSubstitutor = new SimpleSubstitutor(
                System.getProperties().stringPropertyNames().stream().collect(toMap(identity(), System::getProperty)));
        final Path routeFile = Paths.get(simpleSubstitutor.replace(path));
        if (!Files.exists(routeFile)) {
            throw new IllegalArgumentException("No routes configuration for the proxy servlet");
        }

        try (final InputStream stream = Files.newInputStream(routeFile);
             final Jsonb jsonb = JsonbBuilder.newBuilder()
                     .withProvider(loadJsonpProvider())
                     .withConfig(new JsonbConfig().setProperty("org.apache.johnzon.supports-comments", true))
                     .build()) {
            routes = jsonb.fromJson(stream, Routes.class);
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
        final boolean hasRoutes = routes.routes != null && !routes.routes.isEmpty();
        if (routes.defaultRoute == null && !hasRoutes) {
            return Optional.empty();
        }
        if (routes.defaultRoute != null) {
            onLoad(simpleSubstitutor, routes.defaultRoute);
            if (routes.routes == null) { // no route were defined, consider it is the default route, /!\ empty means no route, don't default
                routes.routes = singletonList(routes.defaultRoute);
            }
            if (hasRoutes) {
                routes.routes.forEach(r -> merge(routes.defaultRoute, r));
            }
        }
        if (hasRoutes) {
            routes.routes.forEach(it -> onLoad(simpleSubstitutor, it));
        }
        return Optional.of(routes);
    }

    private void merge(final Routes.Route defaultRoute, final Routes.Route route) {
        if (route.requestConfiguration == null) {
            route.requestConfiguration = defaultRoute.requestConfiguration;
        } else if (defaultRoute.requestConfiguration != null) {
            if (route.requestConfiguration.method == null) {
                route.requestConfiguration.method = defaultRoute.requestConfiguration.method;
            }
            if (route.requestConfiguration.prefix == null) {
                route.requestConfiguration.prefix = defaultRoute.requestConfiguration.prefix;
            }
            if (route.requestConfiguration.skippedCookies == null) {
                route.requestConfiguration.skippedCookies = defaultRoute.requestConfiguration.skippedCookies;
            }
            if (route.requestConfiguration.skippedHeaders == null) {
                route.requestConfiguration.skippedHeaders = defaultRoute.requestConfiguration.skippedHeaders;
            }
        }

        if (route.responseConfiguration == null) {
            route.responseConfiguration = defaultRoute.responseConfiguration;
        } else if (defaultRoute.responseConfiguration != null) {
            if (route.responseConfiguration.target == null) {
                route.responseConfiguration.target = defaultRoute.responseConfiguration.target;
            }
            if (route.responseConfiguration.skippedCookies == null) {
                route.responseConfiguration.skippedCookies = defaultRoute.responseConfiguration.skippedCookies;
            }
            if (route.responseConfiguration.skippedHeaders == null) {
                route.responseConfiguration.skippedHeaders = defaultRoute.responseConfiguration.skippedHeaders;
            }
        }
    }

    private JsonProvider loadJsonpProvider() {
        try { // prefer johnzon to support comments
            return JsonProvider.class.cast(Thread.currentThread().getContextClassLoader()
                    .loadClass("org.apache.johnzon.core.JsonProviderImpl")
                    .getConstructor().newInstance());
        } catch (final InvocationTargetException | NoClassDefFoundError | InstantiationException | ClassNotFoundException | NoSuchMethodException | IllegalAccessException err) {
            return JsonProvider.provider();
        }
    }

    // filter
    private void onLoad(final SimpleSubstitutor simpleSubstitutor, final Routes.Route route) {
        if (route.requestConfiguration != null && route.requestConfiguration.prefix != null) {
            route.requestConfiguration.prefix = simpleSubstitutor.replace(route.requestConfiguration.prefix);
        }
        if (route.requestConfiguration != null && route.requestConfiguration.method != null) {
            route.requestConfiguration.method = simpleSubstitutor.replace(route.requestConfiguration.method);
        }
        if (route.responseConfiguration != null && route.responseConfiguration.target != null) {
            route.responseConfiguration.target = simpleSubstitutor.replace(route.responseConfiguration.target);
        }
    }
}
