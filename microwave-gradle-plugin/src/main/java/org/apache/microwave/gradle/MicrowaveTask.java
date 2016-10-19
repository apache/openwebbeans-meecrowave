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
package org.apache.microwave.gradle;

import org.apache.microwave.gradle.classloader.FilterGradleClassLoader;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public class MicrowaveTask extends DefaultTask {
    private Configuration classpath;

    @Input
    @Optional
    private int httpPort = 8080;

    @Input
    @Optional
    private int httpsPort = 8443;

    @Input
    @Optional
    private int stopPort = -1;

    @Input
    @Optional
    private String host = "localhost";

    @Input
    @Optional
    protected String dir;

    @Input
    @Optional
    private File serverXml;

    @Input
    @Optional
    private boolean keepServerXmlAsThis;

    @Input
    @Optional
    private Map<String, String> properties;

    @Input
    @Optional
    private boolean quickSession = true;

    @Input
    @Optional
    private boolean skipHttp;

    @Input
    @Optional
    private boolean ssl;

    @Input
    @Optional
    private String keystoreFile;

    @Input
    @Optional
    private String keystorePass;

    @Input
    @Optional
    private String keystoreType;

    @Input
    @Optional
    private String clientAuth;

    @Input
    @Optional
    private String keyAlias;

    @Input
    @Optional
    private String sslProtocol;

    @Input
    @Optional
    private String webXml;

    @Input
    @Optional
    private String loginConfig;

    @Input
    @Optional
    private Collection<String> securityConstraints = new LinkedList<>();

    @Input
    @Optional
    private Map<String, String> users;

    @Input
    @Optional
    private Map<String, String> roles;

    @Input
    @Optional
    private boolean http2;

    @Input
    @Optional
    private String tempDir;

    @Input
    @Optional
    private boolean webResourceCached;

    @Input
    @Optional
    private String conf;

    @Input
    @Optional
    private boolean deleteBaseOnStartup = true;

    @Input
    @Optional
    private String jaxrsMapping = "/*";

    @Input
    @Optional
    private boolean jaxrsProviderSetup = true;

    @Input
    @Optional
    private boolean cdiConversation;

    @Input
    @Optional
    private boolean skip;

    @Input
    @Optional
    private List<File> modules;

    @Input
    @Optional
    private Collection<String> applicationScopes = new HashSet<>(asList("compile", "runtime"));

    @Input
    @Optional
    private Collection<String> classloaderFilteredPackages;

    @Input
    @Optional
    private String context = "";

    @Input
    @Optional
    private File webapp;

    @TaskAction
    public void bake() {
        fixConfig();

        final Thread thread = Thread.currentThread();
        final ClassLoader tccl = thread.getContextClassLoader();
        thread.setContextClassLoader(createLoader(tccl));
        try {
            doRun();
        } finally {
            thread.setContextClassLoader(tccl);
        }
    }

    private void doRun() {
        final Thread thread = Thread.currentThread();
        final ClassLoader loader = thread.getContextClassLoader();

        final AtomicBoolean running = new AtomicBoolean(false);
        Thread hook = null;
        AutoCloseable container;
        try {
            final Class<?> containerClass = loader.loadClass("org.apache.microwave.Microwave");
            final Class<?> configClass = loader.loadClass("org.apache.microwave.Microwave$Builder");

            final Object config = getConfig(configClass);

            running.set(true);
            container = AutoCloseable.class.cast(containerClass.getConstructor(configClass).newInstance(config));

            final AutoCloseable finalContainer = container;
            hook = new Thread() {
                @Override
                public void run() {
                    if (running.compareAndSet(true, false)) {
                        final Thread thread = Thread.currentThread();
                        final ClassLoader old = thread.getContextClassLoader();
                        thread.setContextClassLoader(loader);
                        try {
                            finalContainer.close();
                        } catch (final NoClassDefFoundError noClassDefFoundError) {
                            // debug cause it is too late to shutdown properly so don't pollute logs
                            getLogger().debug("can't stop Microwave", noClassDefFoundError);
                        } catch (final Exception e) {
                            getLogger().error("can't stop Microwave", e);
                        } finally {
                            thread.setContextClassLoader(old);
                        }
                    }
                }
            };
            hook.setName("Microwave-Embedded-ShutdownHook");
            Runtime.getRuntime().addShutdownHook(hook);

            containerClass.getMethod("start").invoke(container);
            final String fixedContext = ofNullable(context).orElse("");
            if (webapp == null) {
                configClass.getMethod("deployClasspath", String.class).invoke(container, fixedContext);
            } else {
                configClass.getMethod("deployWebapp", String.class, File.class).invoke(container, fixedContext, webapp);
            }

            getLogger().info("Microwave started on " + configClass.getMethod("getHost").invoke(config) + ":" + configClass.getMethod("getHttpPort").invoke(config));
        } catch (final Exception e) {
            ofNullable(hook).ifPresent(h -> {
                try {
                    h.run();
                } finally {
                    Runtime.getRuntime().removeShutdownHook(h);
                }
            });
            throw new GradleException(e.getMessage(), e);
        }

        try {
            String line;
            final Scanner scanner = new Scanner(System.in);
            while ((line = scanner.nextLine()) != null) {
                final String cmd = line.trim().toLowerCase(Locale.ENGLISH);
                switch (cmd) {
                    case "exit":
                    case "quit":
                        running.set(false);
                        try {
                            hook.run();
                        } finally {
                            Runtime.getRuntime().removeShutdownHook(hook);
                        }
                        return;
                    default:
                        getLogger().warn("Unknown: '" + cmd + "', use 'exit' or 'quit'");
                }
            }
        } catch (final Exception e) {
            Thread.interrupted();
        } finally {
            thread.setContextClassLoader(loader);
        }
    }

    private Object getConfig(final Class<?> configClass) throws Exception {
        final Object config = configClass.newInstance();
        for (final Field field : MicrowaveExtension.class.getDeclaredFields()) {
            try {
                final Field configField = configClass.getDeclaredField(field.getName());
                if (!configField.getType().equals(field.getType())) {
                    getLogger().debug("Skipping " + field.getName() + " since type doesnt match");
                    continue;
                }
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }

                final Object value = field.get(this);
                if (value != null) {
                    if (!configField.isAccessible()) {
                        configField.setAccessible(true);
                    }
                    configField.set(config, value);
                    getLogger().debug("using " + field.getName() + " = " + value);
                }
            } catch (final NoSuchFieldException nsfe) {
                // ignored
            } catch (final Exception e) {
                getLogger().warn("can't initialize attribute " + field.getName());
            }
        }

        if (securityConstraints != null) {
            configClass.getMethod("setSecurityConstraints", Collection.class).invoke(config, securityConstraints.stream()
                    .map(item -> {
                        try {
                            final Class<?> recipeType = configClass.getClassLoader().loadClass("org.apache.xbean.recipe.ObjectRecipe");
                            final Class<?> builderType = configClass.getClassLoader().loadClass("org.apache.microwave.Microwave$SecurityConstaintBuilder");
                            final Object recipe = recipeType.getConstructor(Class.class).newInstance(builderType);
                            Stream.of(item.split(";"))
                                    .map(v -> v.split("="))
                                    .forEach(v -> {
                                        try {
                                            recipe.getClass().getMethod("setProperty", String.class, String.class).invoke(recipe, v[0], v[1]);
                                        } catch (final NoSuchMethodException | IllegalAccessException e) {
                                            throw new IllegalStateException(e);
                                        } catch (final InvocationTargetException e) {
                                            throw new IllegalStateException(e.getCause());
                                        }
                                    });
                            return recipe.getClass().getMethod("create", ClassLoader.class).invoke(recipe, configClass.getClassLoader());
                        } catch (final Exception cnfe) {
                            throw new IllegalArgumentException(item);
                        }
                    }).collect(toList()));
        }
        ofNullable(loginConfig).ifPresent(lc -> {
            try {
                final Class<?> recipeType = configClass.getClassLoader().loadClass("org.apache.xbean.recipe.ObjectRecipe");
                final Class<?> builderType = configClass.getClassLoader().loadClass("org.apache.microwave.Microwave$LoginConfigBuilder");
                final Object recipe = recipeType.getConstructor(Class.class).newInstance(builderType);
                Stream.of(loginConfig.split(";"))
                        .map(v -> v.split("="))
                        .forEach(v -> {
                            try {
                                recipe.getClass().getMethod("setProperty", String.class, String.class).invoke(recipe, v[0], v[1]);
                            } catch (final NoSuchMethodException | IllegalAccessException e) {
                                throw new IllegalStateException(e);
                            } catch (final InvocationTargetException e) {
                                throw new IllegalStateException(e.getCause());
                            }
                        });
                configClass.getMethod("setLoginConfig", Collection.class)
                        .invoke(config, recipe.getClass().getMethod("create", ClassLoader.class).invoke(recipe, configClass.getClassLoader()));
            } catch (final Exception cnfe) {
                throw new IllegalArgumentException(loginConfig);
            }
        });

        return config;
    }

    private ClassLoader createLoader(final ClassLoader parent) {
        final Collection<URL> urls = new LinkedHashSet<>(64);

        addFiles(modules, urls);

        for (final Configuration cc : getProject().getConfigurations()) {
            if (applicationScopes.contains(cc.getName())) {
                addFiles(cc.getFiles(), urls);
            }
        }

        addFiles(classpath.getFiles(), urls);

        // use JVM loader to avoid the noise of gradle and its plugins
        return new URLClassLoader(urls.toArray(new URL[urls.size()]), new FilterGradleClassLoader(parent, classloaderFilteredPackages));
    }

    private void addFiles(final Collection<File> files, final Collection<URL> urls) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (final File f : files) {
            final String name = f.getName();
            if (name.startsWith("slf4j-api") || name.startsWith("slf4j-jdk14")) {
                continue; // use gradle
            }
            try {
                urls.add(f.toURI().toURL());
            } catch (final MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    private void fixConfig() {
        final Project project = getProject();

        // defaults
        if (classpath == null) {
            classpath.add(project.getConfigurations().getByName(MicrowavePlugin.NAME).fileCollection());
        }

        if (dir == null) {
            dir = new File(project.getBuildDir(), "microwave/run").getAbsolutePath();
        }

        // extension override
        final MicrowaveExtension extension = MicrowaveExtension.class.cast(project.getExtensions().findByName(MicrowavePlugin.NAME));
        if (extension != null) {
            for (final Field f : MicrowaveTask.class.getDeclaredFields()) {
                if (f.isAnnotationPresent(Input.class)) {
                    try {
                        final Field extField = MicrowaveExtension.class.getDeclaredField(f.getName());
                        if (!extField.isAccessible()) {
                            extField.setAccessible(true);
                        }
                        final Object val = extField.get(extension);
                        if (val != null) {
                            if (!f.isAccessible()) {
                                f.setAccessible(true);
                            }
                            f.set(this, val);
                        }
                    } catch (final IllegalAccessException | NoSuchFieldException e) {
                        getLogger().warn("No field " + f.getName() + " in " + extension, e);
                    }
                }
            }
        }
    }

}
