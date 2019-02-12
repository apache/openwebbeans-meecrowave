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

import org.apache.meecrowave.gradle.classloader.FilterGradleClassLoader;
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

// Note we can nest inputs objects, if you think it is better (jsonb, jsonp, jaxrs etc..) send a mail on the list ;)
public class MeecrowaveTask extends DefaultTask {
    private Configuration classpath;

    @Input
    @Optional
    private int watcherBouncing;

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
    private String dir;

    @Input
    @Optional
    private String sharedLibraries;

    @Input
    @Optional
    private File serverXml;

    @Input
    @Optional
    private boolean keepServerXmlAsThis;

    @Input
    @Optional
    private boolean tomcatWrapLoader = false;

    @Input
    @Optional
    private Map<String, String> properties;

    @Input
    @Optional
    private boolean quickSession = true;

    @Input
    @Optional
    private boolean jaxrsLogProviders = false;

    @Input
    @Optional
    private boolean useTomcatDefaults = true;

    @Input
    @Optional
    private boolean skipHttp;

    @Input
    @Optional
    private boolean tomcatNoJmx = true;

    @Input
    @Optional
    private boolean initializeClientBus = true;

    @Input
    @Optional
    private boolean injectServletContainerInitializer = true;

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
    private String tomcatFilter;

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
    private Map<String, String> cxfServletParams;

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
    private boolean useLog4j2JulLogManager = System.getProperty("java.util.logging.manager") == null;

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
    private String jaxrsDefaultProviders;

    @Input
    @Optional
    private boolean loggingGlobalSetup = true;

    @Input
    @Optional
    private boolean cdiConversation;

    @Input
    @Optional
    private boolean skip;

    @Input
    @Optional
    private boolean tomcatScanning = true;

    @Input
    @Optional
    private boolean tomcatAutoSetup = true;

    @Input
    @Optional
    private boolean tomcatJspDevelopment = false;

    @Input
    @Optional
    private boolean useShutdownHook = true;

    @Input
    @Optional
    private boolean antiResourceLocking = true;

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

    @Input
    @Optional
    private String jsonpBufferStrategy = "QUEUE";

    @Input
    @Optional
    private int jsonpMaxStringLen = 10 * 1024 * 1024;

    @Input
    @Optional
    private int jsonpMaxReadBufferLen = 64 * 1024;

    @Input
    @Optional
    private int jsonpMaxWriteBufferLen = 64 * 1024;

    @Input
    @Optional
    private boolean jsonpSupportsComment = false;

    @Input
    @Optional
    private boolean jsonpPrettify = false;

    @Input
    @Optional
    private String jsonbEncoding = "UTF-8";

    @Input
    @Optional
    private boolean jsonbNulls = false;

    @Input
    @Optional
    private boolean jsonbIJson = false;

    @Input
    @Optional
    private boolean jsonbPrettify = false;

    @Input
    @Optional
    private String jsonbBinaryStrategy;

    @Input
    @Optional
    private String jsonbNamingStrategy;

    @Input
    @Optional
    private String jsonbOrderStrategy;

    @Input
    @Optional
    private String scanningIncludes;

    @Input
    @Optional
    private String scanningExcludes;

    @Input
    @Optional
    private String scanningPackageIncludes;

    @Input
    @Optional
    private String scanningPackageExcludes;

    @Input
    @Optional
    private String tomcatAccessLogPattern;

    @Input
    @Optional
    private boolean jaxrsAutoActivateBeanValidation = true;

    @Input
    @Optional
    private String meecrowaveProperties = "meecrowave.properties";

    @Input
    @Optional
    private boolean jaxwsSupportIfAvailable = true;

    @Input
    @Optional
    private String defaultSSLHostConfigName;

    @Input
    @Optional
    private Integer webSessionTimeout;

    @Input
    @Optional
    private String webSessionCookieConfig;

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
            final Class<?> containerClass = loader.loadClass("org.apache.meecrowave.Meecrowave");
            final Class<?> configClass = loader.loadClass("org.apache.meecrowave.Meecrowave$Builder");

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
                            getLogger().debug("can't stop Meecrowave", noClassDefFoundError);
                        } catch (final Exception e) {
                            getLogger().error("can't stop Meecrowave", e);
                        } finally {
                            thread.setContextClassLoader(old);
                        }
                    }
                }
            };
            hook.setName("Meecrowave-Embedded-ShutdownHook");
            Runtime.getRuntime().addShutdownHook(hook);

            containerClass.getMethod("start").invoke(container);
            final String fixedContext = ofNullable(context).orElse("");
            if (webapp == null) {
                containerClass.getMethod("deployClasspath", String.class).invoke(container, fixedContext);
            } else {
                containerClass.getMethod("deployWebapp", String.class, File.class).invoke(container, fixedContext, webapp);
            }

            getLogger().info("Meecrowave started on " + configClass.getMethod("getHost").invoke(config) + ":" + configClass.getMethod("getHttpPort").invoke(config));
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
        for (final Field field : MeecrowaveTask.class.getDeclaredFields()) {
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
                getLogger().warn("can't initialize attribute " + field.getName(), e);
            }
        }

        if (securityConstraints != null) {
            configClass.getMethod("setSecurityConstraints", Collection.class).invoke(config, securityConstraints.stream()
                    .map(item -> {
                        try {
                            final Class<?> recipeType = configClass.getClassLoader().loadClass("org.apache.xbean.recipe.ObjectRecipe");
                            final Class<?> builderType = configClass.getClassLoader().loadClass("org.apache.meecrowave.Meecrowave$SecurityConstaintBuilder");
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
                final Class<?> builderType = configClass.getClassLoader().loadClass("org.apache.meecrowave.Meecrowave$LoginConfigBuilder");
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
            classpath = project.getConfigurations().getByName(MeecrowavePlugin.NAME);
        }

        if (dir == null) {
            dir = new File(project.getBuildDir(), "meecrowave/run").getAbsolutePath();
        }

        // extension override
        final MeecrowaveExtension extension = MeecrowaveExtension.class.cast(project.getExtensions().findByName(MeecrowavePlugin.NAME));
        if (extension != null) {
            for (final Field f : MeecrowaveTask.class.getDeclaredFields()) {
                if (f.isAnnotationPresent(Input.class)) {
                    try {
                        final Field extField = MeecrowaveExtension.class.getDeclaredField(f.getName());
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
                        getLogger().debug("No field " + f.getName() + " in " + extension, e);
                    }
                }
            }
        }
    }

    public Configuration getClasspath() {
        return classpath;
    }

    public void setClasspath(final Configuration classpath) {
        this.classpath = classpath;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(final int httpPort) {
        this.httpPort = httpPort;
    }

    public int getHttpsPort() {
        return httpsPort;
    }

    public void setHttpsPort(final int httpsPort) {
        this.httpsPort = httpsPort;
    }

    public int getStopPort() {
        return stopPort;
    }

    public void setStopPort(final int stopPort) {
        this.stopPort = stopPort;
    }

    public String getHost() {
        return host;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(final String dir) {
        this.dir = dir;
    }

    public File getServerXml() {
        return serverXml;
    }

    public void setServerXml(final File serverXml) {
        this.serverXml = serverXml;
    }

    public boolean isKeepServerXmlAsThis() {
        return keepServerXmlAsThis;
    }

    public void setKeepServerXmlAsThis(final boolean keepServerXmlAsThis) {
        this.keepServerXmlAsThis = keepServerXmlAsThis;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(final Map<String, String> properties) {
        this.properties = properties;
    }

    public boolean isQuickSession() {
        return quickSession;
    }

    public void setQuickSession(final boolean quickSession) {
        this.quickSession = quickSession;
    }

    public boolean isSkipHttp() {
        return skipHttp;
    }

    public void setSkipHttp(final boolean skipHttp) {
        this.skipHttp = skipHttp;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(final boolean ssl) {
        this.ssl = ssl;
    }

    public String getKeystoreFile() {
        return keystoreFile;
    }

    public void setKeystoreFile(final String keystoreFile) {
        this.keystoreFile = keystoreFile;
    }

    public String getKeystorePass() {
        return keystorePass;
    }

    public void setKeystorePass(final String keystorePass) {
        this.keystorePass = keystorePass;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public void setKeystoreType(final String keystoreType) {
        this.keystoreType = keystoreType;
    }

    public String getClientAuth() {
        return clientAuth;
    }

    public void setClientAuth(final String clientAuth) {
        this.clientAuth = clientAuth;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(final String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    public void setSslProtocol(final String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public String getWebXml() {
        return webXml;
    }

    public void setWebXml(final String webXml) {
        this.webXml = webXml;
    }

    public String getLoginConfig() {
        return loginConfig;
    }

    public void setLoginConfig(final String loginConfig) {
        this.loginConfig = loginConfig;
    }

    public Collection<String> getSecurityConstraints() {
        return securityConstraints;
    }

    public void setSecurityConstraints(final Collection<String> securityConstraints) {
        this.securityConstraints = securityConstraints;
    }

    public Map<String, String> getUsers() {
        return users;
    }

    public void setUsers(final Map<String, String> users) {
        this.users = users;
    }

    public Map<String, String> getRoles() {
        return roles;
    }

    public void setRoles(final Map<String, String> roles) {
        this.roles = roles;
    }

    public Map<String, String> getCxfServletParams() {
        return cxfServletParams;
    }

    public void setCxfServletParams(final Map<String, String> cxfServletParams) {
        this.cxfServletParams = cxfServletParams;
    }

    public boolean isHttp2() {
        return http2;
    }

    public void setHttp2(final boolean http2) {
        this.http2 = http2;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(final String tempDir) {
        this.tempDir = tempDir;
    }

    public boolean isWebResourceCached() {
        return webResourceCached;
    }

    public void setWebResourceCached(final boolean webResourceCached) {
        this.webResourceCached = webResourceCached;
    }

    public String getConf() {
        return conf;
    }

    public void setConf(final String conf) {
        this.conf = conf;
    }

    public boolean isDeleteBaseOnStartup() {
        return deleteBaseOnStartup;
    }

    public void setDeleteBaseOnStartup(final boolean deleteBaseOnStartup) {
        this.deleteBaseOnStartup = deleteBaseOnStartup;
    }

    public String getJaxrsMapping() {
        return jaxrsMapping;
    }

    public void setJaxrsMapping(final String jaxrsMapping) {
        this.jaxrsMapping = jaxrsMapping;
    }

    public boolean isJaxrsProviderSetup() {
        return jaxrsProviderSetup;
    }

    public void setJaxrsProviderSetup(final boolean jaxrsProviderSetup) {
        this.jaxrsProviderSetup = jaxrsProviderSetup;
    }

    public boolean isLoggingGlobalSetup() {
        return loggingGlobalSetup;
    }

    public void setLoggingGlobalSetup(final boolean loggingGlobalSetup) {
        this.loggingGlobalSetup = loggingGlobalSetup;
    }

    public boolean isCdiConversation() {
        return cdiConversation;
    }

    public void setCdiConversation(final boolean cdiConversation) {
        this.cdiConversation = cdiConversation;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(final boolean skip) {
        this.skip = skip;
    }

    public boolean isTomcatScanning() {
        return tomcatScanning;
    }

    public void setTomcatScanning(final boolean tomcatScanning) {
        this.tomcatScanning = tomcatScanning;
    }

    public List<File> getModules() {
        return modules;
    }

    public void setModules(final List<File> modules) {
        this.modules = modules;
    }

    public Collection<String> getApplicationScopes() {
        return applicationScopes;
    }

    public void setApplicationScopes(final Collection<String> applicationScopes) {
        this.applicationScopes = applicationScopes;
    }

    public Collection<String> getClassloaderFilteredPackages() {
        return classloaderFilteredPackages;
    }

    public void setClassloaderFilteredPackages(final Collection<String> classloaderFilteredPackages) {
        this.classloaderFilteredPackages = classloaderFilteredPackages;
    }

    public String getContext() {
        return context;
    }

    public void setContext(final String context) {
        this.context = context;
    }

    public File getWebapp() {
        return webapp;
    }

    public void setWebapp(final File webapp) {
        this.webapp = webapp;
    }

    public boolean isTomcatAutoSetup() {
        return tomcatAutoSetup;
    }

    public void setTomcatAutoSetup(final boolean tomcatAutoSetup) {
        this.tomcatAutoSetup = tomcatAutoSetup;
    }

    public boolean isUseShutdownHook() {
        return useShutdownHook;
    }

    public void setUseShutdownHook(final boolean useShutdownHook) {
        this.useShutdownHook = useShutdownHook;
    }

    public String getTomcatFilter() {
        return tomcatFilter;
    }

    public void setTomcatFilter(final String tomcatFilter) {
        this.tomcatFilter = tomcatFilter;
    }

    public boolean isUseTomcatDefaults() {
        return useTomcatDefaults;
    }

    public void setUseTomcatDefaults(final boolean useTomcatDefaults) {
        this.useTomcatDefaults = useTomcatDefaults;
    }

    public boolean isJaxrsLogProviders() {
        return jaxrsLogProviders;
    }

    public void setJaxrsLogProviders(final boolean jaxrsLogProviders) {
        this.jaxrsLogProviders = jaxrsLogProviders;
    }

    public boolean isTomcatWrapLoader() {
        return tomcatWrapLoader;
    }

    public void setTomcatWrapLoader(final boolean tomcatWrapLoader) {
        this.tomcatWrapLoader = tomcatWrapLoader;
    }

    public String getJaxrsDefaultProviders() {
        return jaxrsDefaultProviders;
    }

    public void setJaxrsDefaultProviders(final String jaxrsDefaultProviders) {
        this.jaxrsDefaultProviders = jaxrsDefaultProviders;
    }

    public String getSharedLibraries() {
        return sharedLibraries;
    }

    public void setSharedLibraries(final String sharedLibraries) {
        this.sharedLibraries = sharedLibraries;
    }

    public boolean isUseLog4j2JulLogManager() {
        return useLog4j2JulLogManager;
    }

    public void setUseLog4j2JulLogManager(final boolean useLog4j2JulLogManager) {
        this.useLog4j2JulLogManager = useLog4j2JulLogManager;
    }

    public String getScanningIncludes() {
        return scanningIncludes;
    }

    public void setScanningIncludes(final String scanningIncludes) {
        this.scanningIncludes = scanningIncludes;
    }

    public String getScanningExcludes() {
        return scanningExcludes;
    }

    public void setScanningExcludes(final String scanningExcludes) {
        this.scanningExcludes = scanningExcludes;
    }

    public String getJsonpBufferStrategy() {
        return jsonpBufferStrategy;
    }

    public void setJsonpBufferStrategy(final String jsonpBufferStrategy) {
        this.jsonpBufferStrategy = jsonpBufferStrategy;
    }

    public int getJsonpMaxStringLen() {
        return jsonpMaxStringLen;
    }

    public void setJsonpMaxStringLen(final int jsonpMaxStringLen) {
        this.jsonpMaxStringLen = jsonpMaxStringLen;
    }

    public int getJsonpMaxReadBufferLen() {
        return jsonpMaxReadBufferLen;
    }

    public void setJsonpMaxReadBufferLen(final int jsonpMaxReadBufferLen) {
        this.jsonpMaxReadBufferLen = jsonpMaxReadBufferLen;
    }

    public int getJsonpMaxWriteBufferLen() {
        return jsonpMaxWriteBufferLen;
    }

    public void setJsonpMaxWriteBufferLen(final int jsonpMaxWriteBufferLen) {
        this.jsonpMaxWriteBufferLen = jsonpMaxWriteBufferLen;
    }

    public boolean isJsonpSupportsComment() {
        return jsonpSupportsComment;
    }

    public void setJsonpSupportsComment(final boolean jsonpSupportsComment) {
        this.jsonpSupportsComment = jsonpSupportsComment;
    }

    public boolean isJsonpPrettify() {
        return jsonpPrettify;
    }

    public void setJsonpPrettify(final boolean jsonpPrettify) {
        this.jsonpPrettify = jsonpPrettify;
    }

    public String getJsonbEncoding() {
        return jsonbEncoding;
    }

    public void setJsonbEncoding(final String jsonbEncoding) {
        this.jsonbEncoding = jsonbEncoding;
    }

    public boolean isJsonbNulls() {
        return jsonbNulls;
    }

    public void setJsonbNulls(final boolean jsonbNulls) {
        this.jsonbNulls = jsonbNulls;
    }

    public boolean isJsonbIJson() {
        return jsonbIJson;
    }

    public void setJsonbIJson(final boolean jsonbIJson) {
        this.jsonbIJson = jsonbIJson;
    }

    public boolean isJsonbPrettify() {
        return jsonbPrettify;
    }

    public void setJsonbPrettify(final boolean jsonbPrettify) {
        this.jsonbPrettify = jsonbPrettify;
    }

    public String getJsonbBinaryStrategy() {
        return jsonbBinaryStrategy;
    }

    public void setJsonbBinaryStrategy(final String jsonbBinaryStrategy) {
        this.jsonbBinaryStrategy = jsonbBinaryStrategy;
    }

    public String getJsonbNamingStrategy() {
        return jsonbNamingStrategy;
    }

    public void setJsonbNamingStrategy(final String jsonbNamingStrategy) {
        this.jsonbNamingStrategy = jsonbNamingStrategy;
    }

    public String getJsonbOrderStrategy() {
        return jsonbOrderStrategy;
    }

    public void setJsonbOrderStrategy(final String jsonbOrderStrategy) {
        this.jsonbOrderStrategy = jsonbOrderStrategy;
    }

    public String getScanningPackageIncludes() {
        return scanningPackageIncludes;
    }

    public void setScanningPackageIncludes(final String scanningPackageIncludes) {
        this.scanningPackageIncludes = scanningPackageIncludes;
    }

    public String getScanningPackageExcludes() {
        return scanningPackageExcludes;
    }

    public void setScanningPackageExcludes(final String scanningPackageExcludes) {
        this.scanningPackageExcludes = scanningPackageExcludes;
    }

    public boolean isTomcatNoJmx() {
        return tomcatNoJmx;
    }

    public void setTomcatNoJmx(final boolean tomcatNoJmx) {
        this.tomcatNoJmx = tomcatNoJmx;
    }

    public boolean isInjectServletContainerInitializer() {
        return injectServletContainerInitializer;
    }

    public void setInjectServletContainerInitializer(final boolean injectServletContainerInitializer) {
        this.injectServletContainerInitializer = injectServletContainerInitializer;
    }

    public String getTomcatAccessLogPattern() {
        return tomcatAccessLogPattern;
    }

    public void setTomcatAccessLogPattern(final String tomcatAccessLogPattern) {
        this.tomcatAccessLogPattern = tomcatAccessLogPattern;
    }

    public boolean isJaxrsAutoActivateBeanValidation() {
        return jaxrsAutoActivateBeanValidation;
    }

    public void setJaxrsAutoActivateBeanValidation(final boolean jaxrsAutoActivateBeanValidation) {
        this.jaxrsAutoActivateBeanValidation = jaxrsAutoActivateBeanValidation;
    }

    public String getMeecrowaveProperties() {
        return meecrowaveProperties;
    }

    public void setMeecrowaveProperties(final String meecrowaveProperties) {
        this.meecrowaveProperties = meecrowaveProperties;
    }

    public int getWatcherBouncing() {
        return watcherBouncing;
    }

    public void setWatcherBouncing(final int watcherBouncing) {
        this.watcherBouncing = watcherBouncing;
    }

    public boolean isJaxwsSupportIfAvailable() {
        return jaxwsSupportIfAvailable;
    }

    public void setJaxwsSupportIfAvailable(final boolean jaxwsSupportIfAvailable) {
        this.jaxwsSupportIfAvailable = jaxwsSupportIfAvailable;
    }

    public String getDefaultSSLHostConfigName() {
        return defaultSSLHostConfigName;
    }

    public void setDefaultSSLHostConfigName(final String defaultSSLHostConfigName) {
        this.defaultSSLHostConfigName = defaultSSLHostConfigName;
    }

    public boolean isInitializeClientBus() {
        return initializeClientBus;
    }

    public void setInitializeClientBus(final boolean initializeClientBus) {
        this.initializeClientBus = initializeClientBus;
    }

    public Integer getWebSessionTimeout() {
        return webSessionTimeout;
    }

    public void setWebSessionTimeout(final Integer webSessionTimeout) {
        this.webSessionTimeout = webSessionTimeout;
    }

    public String getWebSessionCookieConfig() {
        return webSessionCookieConfig;
    }

    public void setWebSessionCookieConfig(final String webSessionCookieConfig) {
        this.webSessionCookieConfig = webSessionCookieConfig;
    }

    public boolean isTomcatJspDevelopment() {
        return tomcatJspDevelopment;
    }

    public void setTomcatJspDevelopment(final boolean tomcatJspDevelopment) {
        this.tomcatJspDevelopment = tomcatJspDevelopment;
    }

    public boolean isAntiResourceLocking() {
        return antiResourceLocking;
    }

    public void setAntiResourceLocking(final boolean antiResourceLocking) {
        this.antiResourceLocking = antiResourceLocking;
    }
}
