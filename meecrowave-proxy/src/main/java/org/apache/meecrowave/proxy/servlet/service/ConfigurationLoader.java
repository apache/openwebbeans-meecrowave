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
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.spi.JsonProvider;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.ClientBuilder;

import org.apache.meecrowave.proxy.servlet.configuration.Routes;

public abstract class ConfigurationLoader {
    private final String path;

    private Routes routes;

    public ConfigurationLoader(final String path) {
        this.path = path;
    }

    protected abstract void log(String message);

    public Optional<Routes> load() {
        final SimpleSubstitutor simpleSubstitutor = new SimpleSubstitutor(
                System.getProperties().stringPropertyNames().stream().collect(toMap(identity(), System::getProperty)));
        final String resource = simpleSubstitutor.replace(path);
        final Path routeFile = Paths.get(resource);
        final InputStream stream;
        if (Files.exists(routeFile)) {
            try {
                stream = Files.newInputStream(routeFile);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        }
        if (stream == null) {
            throw new IllegalArgumentException("No routes configuration for the proxy servlet");
        }

        final String content;
        try {
            content = simpleSubstitutor.replace(load(stream));
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }

        try (final Jsonb jsonb = JsonbBuilder.newBuilder()
                     .withProvider(loadJsonpProvider())
                     .withConfig(new JsonbConfig().setProperty("org.apache.johnzon.supports-comments", true))
                     .build()) {
            routes = jsonb.fromJson(content, Routes.class);
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
        final boolean hasRoutes = routes.routes != null && !routes.routes.isEmpty();
        if (routes.defaultRoute == null && !hasRoutes) {
            return Optional.empty();
        }
        if (routes.defaultRoute != null) {
            if (routes.routes == null) { // no route were defined, consider it is the default route, /!\ empty means no route, don't default
                routes.routes = singletonList(routes.defaultRoute);
            }
            if (hasRoutes) {
                routes.routes.forEach(r -> merge(routes.defaultRoute, r));
            }
        }
        routes.routes.forEach(this::loadClient);
        return Optional.of(routes);
    }

    private String load(final InputStream stream) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while (-1 != (count = stream.read(buffer))) {
            baos.write(buffer, 0, count);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    private void loadClient(final Routes.Route route) {
        if (route.clientConfiguration == null) {
            route.clientConfiguration = new Routes.ClientConfiguration();
        }
        if (route.clientConfiguration.executor == null) {
            route.clientConfiguration.executor = new Routes.ExecutorConfiguration();
        }
        if (route.clientConfiguration.timeouts == null) {
            route.clientConfiguration.timeouts = new Routes.TimeoutConfiguration();
        }
        if (route.clientConfiguration.sslConfiguration == null) {
            route.clientConfiguration.sslConfiguration = new Routes.SslConfiguration();
        }

        final ExecutorService executor = new ThreadPoolExecutor(
                route.clientConfiguration.executor.core,
                route.clientConfiguration.executor.max,
                route.clientConfiguration.executor.keepAlive,
                MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private final SecurityManager sm = System.getSecurityManager();
                    private final ThreadGroup group = (sm != null) ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();

                    @Override
                    public Thread newThread(final Runnable r) {
                        final Thread newThread = new Thread(group, r, "meecrowave-proxy#" + ofNullable(route.id).orElse("[noid]"));
                        newThread.setDaemon(false);
                        newThread.setPriority(Thread.NORM_PRIORITY);
                        newThread.setContextClassLoader(getClass().getClassLoader());
                        return newThread;
                    }
                },
                (run, ex) -> log("Proxy rejected task: " + run + ", in " + ex));

        final ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.executorService(executor);
        clientBuilder.readTimeout(route.clientConfiguration.timeouts.read, MILLISECONDS);
        clientBuilder.connectTimeout(route.clientConfiguration.timeouts.connect, MILLISECONDS);
        // clientBuilder.scheduledExecutorService(); // not used by cxf for instance so no need to overkill the conf

        if (route.clientConfiguration.sslConfiguration.acceptAnyCertificate) {
            clientBuilder.hostnameVerifier((host, session) -> true);
            clientBuilder.sslContext(createUnsafeSSLContext());
        } else if (route.clientConfiguration.sslConfiguration.keystoreLocation != null) {
            if (route.clientConfiguration.sslConfiguration.verifiedHostnames != null) {
                clientBuilder.hostnameVerifier((host, session) -> route.clientConfiguration.sslConfiguration.verifiedHostnames.contains(host));
            }
            clientBuilder.sslContext(createSSLContext(
                    route.clientConfiguration.sslConfiguration.keystoreLocation,
                    route.clientConfiguration.sslConfiguration.keystoreType,
                    route.clientConfiguration.sslConfiguration.keystorePassword,
                    route.clientConfiguration.sslConfiguration.truststoreType));
        }

        route.client = clientBuilder.build();
    }

    private void merge(final Routes.Route defaultRoute, final Routes.Route route) {
        // request matching
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

        // response processing
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

        // client setup
        if (route.clientConfiguration == null) {
            route.clientConfiguration = defaultRoute.clientConfiguration;
        } else if (defaultRoute.clientConfiguration != null) {
            if (route.clientConfiguration.sslConfiguration == null) {
                route.clientConfiguration.sslConfiguration = defaultRoute.clientConfiguration.sslConfiguration;
            } else if (defaultRoute.clientConfiguration.sslConfiguration != null) {
                if (route.clientConfiguration.sslConfiguration.verifiedHostnames == null) {
                    route.clientConfiguration.sslConfiguration.verifiedHostnames = defaultRoute.clientConfiguration.sslConfiguration.verifiedHostnames;
                }
                if (route.clientConfiguration.sslConfiguration.keystoreLocation == null) {
                    route.clientConfiguration.sslConfiguration.keystoreLocation = defaultRoute.clientConfiguration.sslConfiguration.keystoreLocation;
                }
                if (route.clientConfiguration.sslConfiguration.keystorePassword == null) {
                    route.clientConfiguration.sslConfiguration.keystorePassword = defaultRoute.clientConfiguration.sslConfiguration.keystorePassword;
                }
                if (route.clientConfiguration.sslConfiguration.keystoreType == null) {
                    route.clientConfiguration.sslConfiguration.keystoreType = defaultRoute.clientConfiguration.sslConfiguration.keystoreType;
                }
                if (route.clientConfiguration.sslConfiguration.truststoreType == null) {
                    route.clientConfiguration.sslConfiguration.truststoreType = defaultRoute.clientConfiguration.sslConfiguration.truststoreType;
                }
            }
            if (route.clientConfiguration.executor == null) {
                route.clientConfiguration.executor = defaultRoute.clientConfiguration.executor;
            }
            if (route.clientConfiguration.timeouts == null) {
                route.clientConfiguration.timeouts = defaultRoute.clientConfiguration.timeouts;
            }
        }
    }

    private SSLContext createSSLContext(final String keystoreLocation, final String keystoreType,
                                        final String keystorePassword, final String truststoreType) {
        final File source = new File(keystoreLocation);
        if (!source.exists()) {
            throw new IllegalArgumentException(source + " does not exist");
        }
        final KeyStore keyStore;
        try (final FileInputStream stream = new FileInputStream(source)) {
            keyStore = KeyStore.getInstance(keystoreType == null ? KeyStore.getDefaultType() : keystoreType);
            keyStore.load(stream, keystorePassword.toCharArray());
        } catch (final KeyStoreException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (final CertificateException | IOException e) {
            throw new IllegalArgumentException(e);
        }
        try {
            final TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(truststoreType == null ? TrustManagerFactory.getDefaultAlgorithm() : truststoreType);
            trustManagerFactory.init(keyStore);
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new java.security.SecureRandom());
            return sslContext;
        } catch (final KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException(e);
        }
    }

    private SSLContext createUnsafeSSLContext() {
        final TrustManager[] trustManagers = { new X509TrustManager() {

            @Override
            public void checkClientTrusted(final X509Certificate[] x509Certificates, final String s) {
                // no-op
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] x509Certificates, final String s) {
                // no-op
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        } };
        try {
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustManagers, new java.security.SecureRandom());
            return sslContext;
        } catch (final NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException(e);
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
}
