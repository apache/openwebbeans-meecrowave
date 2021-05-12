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
package org.apache.meecrowave.maven;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME_PLUS_SYSTEM;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Supplier;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.catalina.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.LifecycleStarter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.configuration.Configuration;
import org.apache.meecrowave.tomcat.ProvidedLoader;

@Mojo(name = "run", requiresDependencyResolution = RUNTIME_PLUS_SYSTEM)
public class MeecrowaveRunMojo extends AbstractMojo {
    @Parameter(property = "meecrowave.watcher-bounding", defaultValue = "0")
    private int watcherBouncing;

    @Parameter(property = "meecrowave.http", defaultValue = "8080")
    private int httpPort;

    @Parameter(property = "meecrowave.https", defaultValue = "8443")
    private int httpsPort;

    @Parameter(property = "meecrowave.stop", defaultValue = "8005")
    private int stopPort;

    @Parameter(property = "meecrowave.host", defaultValue = "localhost")
    private String host;

    @Parameter(property = "meecrowave.dir")
    protected String dir;

    @Parameter(property = "meecrowave.serverXml")
    private File serverXml;

    @Parameter(property = "meecrowave.keepServerXmlAsThis")
    private boolean keepServerXmlAsThis;

    @Parameter(property = "meecrowave.jaxrsLogProviders", defaultValue = "false")
    private boolean jaxrsLogProviders;

    @Parameter(property = "meecrowave.tomcatWrapLoader", defaultValue = "false")
    private boolean tomcatWrapLoader;

    @Parameter(property = "meecrowave.useTomcatDefaults", defaultValue = "true")
    private boolean useTomcatDefaults;

    @Parameter(property = "meecrowave.antiResourceLocking", defaultValue = "false")
    private boolean antiResourceLocking;

    @Parameter
    private Map<String, String> properties;

    @Parameter
    private Map<String, String> systemProperties;

    @Parameter
    private Map<String, String> cxfServletParams;

    @Parameter(property = "meecrowave.tomcatNoJmx", defaultValue = "true")
    private boolean tomcatNoJmx;

    @Parameter(property = "meecrowave.quickSession", defaultValue = "true")
    private boolean quickSession;

    @Parameter(property = "meecrowave.tomcatScanning", defaultValue = "true")
    private boolean tomcatScanning;

    @Parameter(property = "meecrowave.tomcatAutoSetup", defaultValue = "true")
    private boolean tomcatAutoSetup;

    @Parameter(property = "meecrowave.tomcatJspDevelopment", defaultValue = "false")
    private boolean tomcatJspDevelopment;

    @Parameter(property = "meecrowave.skipHttp")
    private boolean skipHttp;

    @Parameter(property = "meecrowave.ssl")
    private boolean ssl;

    @Parameter(property = "meecrowave.keystoreFile")
    private String keystoreFile;

    @Parameter(property = "meecrowave.keystorePass")
    private String keystorePass;

    @Parameter(property = "meecrowave.keystoreType", defaultValue = "JKS")
    private String keystoreType;

    @Parameter(property = "meecrowave.clientAuth")
    private String clientAuth;

    @Parameter(property = "meecrowave.keyAlias")
    private String keyAlias;

    @Parameter(property = "meecrowave.sslProtocol")
    private String sslProtocol;

    @Parameter(property = "meecrowave.webXml")
    private String webXml;

    @Parameter(property = "meecrowave.tomcatAccessLogPattern")
    private String tomcatAccessLogPattern;

    @Parameter
    private Meecrowave.LoginConfigBuilder loginConfig;

    @Parameter
    private Collection<Meecrowave.SecurityConstaintBuilder> securityConstraints = new LinkedList<>();

    @Parameter
    private Map<String, String> users;

    @Parameter
    private Map<String, String> roles;

    @Parameter(property = "meecrowave.http2")
    private boolean http2;

    @Parameter(property = "meecrowave.tempDir")
    private String tempDir;

    @Parameter(property = "meecrowave.webResourceCached", defaultValue = "true")
    private boolean webResourceCached;

    @Parameter(property = "meecrowave.conf")
    private String conf;

    @Parameter(property = "meecrowave.deleteBaseOnStartup", defaultValue = "true")
    private boolean deleteBaseOnStartup;

    @Parameter(property = "meecrowave.jaxrsMapping", defaultValue = "/*")
    private String jaxrsMapping;

    @Parameter(property = "meecrowave.cdiConversation", defaultValue = "false")
    private boolean cdiConversation;

    @Parameter(property = "meecrowave.skip")
    private boolean skip;

    @Parameter(property = "meecrowave.jaxrs-beanvalidation", defaultValue = "true")
    private boolean jaxrsAutoActivateBeanValidation;

    @Parameter(property = "meecrowave.jaxrs-default-providers")
    private String jaxrsDefaultProviders;

    @Parameter(property = "meecrowave.jaxrs-provider-setup", defaultValue = "true")
    private boolean jaxrsProviderSetup;

    @Parameter(property = "meecrowave.logging-global-setup", defaultValue = "true")
    private boolean loggingGlobalSetup;

    @Parameter(property = "meecrowave.servlet-container-initializer-injections", defaultValue = "true")
    private boolean injectServletContainerInitializer;

    @Parameter(property = "meecrowave.shutdown-hook", defaultValue = "true")
    private boolean useShutdownHook;

    @Parameter(property = "meecrowave.initialiaze-client-bus", defaultValue = "true")
    private boolean initializeClientBus;

    @Parameter
    private List<String> jsCustomizers;

    @Parameter
    private List<String> applicationScopes;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private List<File> modules;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "meecrowave.tomcatFilter")
    private String tomcatFilter;

    @Parameter(property = "meecrowave.context", defaultValue = "")
    private String context;

    // we don't need to resolve from maven coordinates cause can be added to the plugin deps, just here to reproduce manual deployments
    @Parameter(property = "meecrowave.shared-libraries")
    private String sharedLibraries;

    @Parameter(property = "meecrowave.log4j2-jul-bridge", defaultValue = "true")
    private boolean useLog4j2JulLogManager;

    @Parameter(property = "meecrowave.jsonp-buffer-strategy", defaultValue = "QUEUE")
    private String jsonpBufferStrategy;

    @Parameter(property = "meecrowave.jsonp-max-string-length", defaultValue = "10485760")
    private int jsonpMaxStringLen;

    @Parameter(property = "meecrowave.jsonp-max-read-buffer-size", defaultValue = "65536")
    private int jsonpMaxReadBufferLen;

    @Parameter(property = "meecrowave.jsonp-max-write-buffer-size", defaultValue = "65536")
    private int jsonpMaxWriteBufferLen;

    @Parameter(property = "meecrowave.jsonp-comments", defaultValue = "false")
    private boolean jsonpSupportsComment;

    @Parameter(property = "meecrowave.jsonp-prettify", defaultValue = "false")
    private boolean jsonpPrettify;

    @Parameter(property = "meecrowave.jsonb-encoding", defaultValue = "UTF-8")
    private String jsonbEncoding;

    @Parameter(property = "meecrowave.jsonb-nulls", defaultValue = "false")
    private boolean jsonbNulls = false;

    @Parameter(property = "meecrowave.jsonb-ijson", defaultValue = "false")
    private boolean jsonbIJson;

    @Parameter(property = "meecrowave.jsonb-prettify", defaultValue = "false")
    private boolean jsonbPrettify;

    @Parameter(property = "meecrowave.jsonb-binary-strategy")
    private String jsonbBinaryStrategy;

    @Parameter(property = "meecrowave.jsonb-naming-strategy")
    private String jsonbNamingStrategy;

    @Parameter(property = "meecrowave.jsonb-order-strategy")
    private String jsonbOrderStrategy;

    @Parameter(property = "meecrowave.scanning-include")
    private String scanningIncludes;

    @Parameter(property = "meecrowave.scanning-exclude")
    private String scanningExcludes;

    @Parameter(property = "meecrowave.scanning-package-include")
    private String scanningPackageIncludes;

    @Parameter(property = "meecrowave.scanning-package-exclude")
    private String scanningPackageExcludes;

    @Parameter(property = "meecrowave.force-log4j2-shutdown", defaultValue = "true")
    private boolean forceLog4j2Shutdown;

    @Parameter(property = "meecrowave.webapp", defaultValue = "${project.basedir}/src/main/webapp")
    private File webapp;

    @Parameter(property = "meecrowave.force-classpath-deployment", defaultValue = "true")
    private boolean useClasspathDeployment;

    @Parameter
    private String jsContextCustomizer;

    @Parameter(property = "meecrowave.meecrowave-properties", defaultValue = "meecrowave.properties")
    private String meecrowaveProperties;

    @Parameter(property = "meecrowave.jaxws-support", defaultValue = "true")
    private boolean jaxwsSupportIfAvailable;

    @Parameter(property = "meecrowave.reload-goals")
    private List<String> reloadGoals;

    @Parameter(property = "meecrowave.default-ssl-hostconfig-name")
    private String defaultSSLHostConfigName;

    @Parameter(property = "meecrowave.session-timeout")
    private Integer webSessionTimeout;

    @Parameter(property = "meecrowave.session-cookie-config")
    private String webSessionCookieConfig;

    @Component
    private LifecycleStarter lifecycleStarter;

    @Override
    public void execute() {
        if (skip) {
            getLog().warn("Mojo skipped");
            return;
        }
        if (watcherBouncing <= 0 && (reloadGoals == null || reloadGoals.isEmpty())) {
            try {
                reloadGoals = singletonList("process-classes");
            } catch (final RuntimeException re) { // mojo in read only mode
                // no-op
            }
        }
        logConfigurationErrors();

        final Map<String, String> originalSystemProps;
        if (systemProperties != null) {
            originalSystemProps = systemProperties.keySet().stream()
                                                  .filter(System.getProperties()::containsKey)
                                                  .collect(toMap(identity(), System::getProperty));
            systemProperties.forEach(System::setProperty);
        } else {
            originalSystemProps = null;
        }

        final Thread thread = Thread.currentThread();
        final ClassLoader loader = thread.getContextClassLoader();
        final Supplier<ClassLoader> appLoaderSupplier = createClassLoader(loader);
        thread.setContextClassLoader(appLoaderSupplier.get());
        try {
            final Configuration builder = getConfig();
            try (final Meecrowave meecrowave = new Meecrowave(builder) {
                @Override
                protected void beforeStart() {
                    scriptCustomization(jsCustomizers, "js", singletonMap("meecrowaveBase", base.getAbsolutePath()));
                }
            }) {
                meecrowave.start();
                final String fixedContext = ofNullable(context).orElse("");
                final Meecrowave.DeploymentMeta deploymentMeta = new Meecrowave.DeploymentMeta(
                        fixedContext,
                        webapp != null && webapp.isDirectory() ? webapp : null,
                        jsContextCustomizer == null ?
                                null : ctx -> scriptCustomization(
                                singletonList(jsContextCustomizer), "js", singletonMap("context", ctx)),
                                context -> reload(meecrowave, fixedContext, appLoaderSupplier, loader));
                deploy(meecrowave, deploymentMeta);
                final Scanner scanner = new Scanner(System.in);
                String cmd;
                boolean quit = false;
                while (!quit && (cmd = scanner.next()) != null) {
                    cmd = cmd.trim();
                    switch (cmd) {
                        case "": // normally impossible with a Scanner but we can move to another "reader"
                        case "q":
                        case "quit":
                        case "e":
                        case "exit":
                            quit = true;
                            break;
                        case "r":
                        case "reload":
                            reload(meecrowave, fixedContext, appLoaderSupplier, loader);
                            break;
                        default:
                            getLog().error("Unknown command: '" + cmd + "', use 'quit' or 'exit' or 'reload'");
                    }
                }
            }
        } finally {
            if (forceLog4j2Shutdown) {
                LogManager.shutdown();
            }
            destroyTcclIfNeeded(thread, loader);
            thread.setContextClassLoader(loader);
            if (originalSystemProps != null) {
                systemProperties.keySet().forEach(k -> {
                    final Optional<String> originalValue = ofNullable(originalSystemProps.get(k));
                    if (originalValue.isPresent()) {
                        System.setProperty(k, originalValue.get());
                    } else {
                        System.clearProperty(k);
                    }
                });
            }
        }
    }

    private void destroyTcclIfNeeded(final Thread thread, final ClassLoader loader) {
        if (thread.getContextClassLoader() != loader) {
            try {
                URLClassLoader.class.cast(thread.getContextClassLoader()).close();
            } catch (final IOException e) {
                getLog().warn(e.getMessage(), e);
            }
        }
    }

    private void logConfigurationErrors() {
        if (watcherBouncing > 0 && reloadGoals != null && !reloadGoals.isEmpty()) {
            getLog().warn("You set reloadGoals and watcherBouncing > 1, behavior is undefined");
        }
    }

    private void reload(final Meecrowave meecrowave, final String context,
                        final Supplier<ClassLoader> loaderSupplier, final ClassLoader mojoLoader) {
        if (reloadGoals != null && !reloadGoals.isEmpty()) {
            final List<String> goals = session.getGoals();
            session.getRequest().setGoals(reloadGoals);
            try {
                lifecycleStarter.execute(session);
            } finally {
                session.getRequest().setGoals(goals);
            }
        }
        final Context ctx = Context.class.cast(meecrowave.getTomcat().getHost().findChild(context));
        if (useClasspathDeployment) {
            final Thread thread = Thread.currentThread();
            destroyTcclIfNeeded(thread, mojoLoader);
            thread.setContextClassLoader(loaderSupplier.get());
            ctx.setLoader(new ProvidedLoader(thread.getContextClassLoader(), meecrowave.getConfiguration().isTomcatWrapLoader()));
        }
        ctx.reload();
    }

    private void deploy(final Meecrowave meecrowave, final Meecrowave.DeploymentMeta deploymentMeta) {
        if (useClasspathDeployment) {
            meecrowave.deployClasspath(deploymentMeta);
        } else {
            meecrowave.deployWebapp(deploymentMeta);
        }
    }

    private void scriptCustomization(final List<String> customizers, final String ext, final Map<String, Object> customBindings) {
        if (customizers == null || customizers.isEmpty()) {
            return;
        }
        final ScriptEngine engine = new ScriptEngineManager().getEngineByExtension(ext);
        if (engine == null) {
            throw new IllegalStateException("No engine for " + ext + ". Maybe add the JSR223 implementation as plugin dependency.");
        }
        for (final String js : customizers) {
            try {
                final SimpleBindings bindings = new SimpleBindings();
                bindings.put("project", project);
                engine.eval(new StringReader(js), bindings);
                bindings.putAll(customBindings);
            } catch (final ScriptException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    private Supplier<ClassLoader> createClassLoader(final ClassLoader parent) {
        final List<URL> urls = new ArrayList<>();
        urls.addAll(project.getArtifacts().stream()
                .filter(a -> !((applicationScopes == null && !(Artifact.SCOPE_COMPILE.equals(a.getScope()) || Artifact.SCOPE_RUNTIME.equals(a.getScope())))
                        || (applicationScopes != null && !applicationScopes.contains(a.getScope()))))
                .map(f -> {
                    try {
                        return f.getFile().toURI().toURL();
                    } catch (final MalformedURLException e) {
                        throw new IllegalArgumentException(e);
                    }
                })
                .collect(toList()));
        urls.addAll(ofNullable(modules).orElse(Collections.emptyList()).stream().map(f -> {
            try {
                return f.toURI().toURL();
            } catch (final MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }).collect(toList()));
        return urls.isEmpty() ? () -> parent : () -> new URLClassLoader(urls.toArray(new URL[0]), parent) {
            @Override
            public boolean equals(final Object obj) {
                return super.equals(obj) || parent.equals(obj);
            }
        };
    }

    private Configuration getConfig() {
        final Configuration config = new Configuration();
        for (final Field field : MeecrowaveRunMojo.class.getDeclaredFields()) {
            if ("properties".equals(field.getName())) {
                continue;
            }
            try {
                final Field configField = Configuration.class.getDeclaredField(field.getName());
                field.setAccessible(true);
                configField.setAccessible(true);

                final Object value = field.get(this);
                if (value != null) {
                    configField.set(config, value);
                    getLog().debug("using " + field.getName() + " = " + value);
                }
            } catch (final NoSuchFieldException nsfe) {
                // ignored
            } catch (final Exception e) {
                getLog().warn("can't initialize attribute " + field.getName());
            }
        }
        config.loadFrom(meecrowaveProperties);
        if (properties != null) {
            config.getProperties().putAll(properties);
        }
        return config;
    }
}
