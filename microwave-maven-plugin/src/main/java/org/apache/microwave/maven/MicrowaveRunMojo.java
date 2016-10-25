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
package org.apache.microwave.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.microwave.Microwave;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
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
import java.util.Scanner;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME_PLUS_SYSTEM;

@Mojo(name = "run", requiresDependencyResolution = RUNTIME_PLUS_SYSTEM)
public class MicrowaveRunMojo extends AbstractMojo {
    @Parameter(property = "microwave.http", defaultValue = "8080")
    private int httpPort;

    @Parameter(property = "microwave.https", defaultValue = "8443")
    private int httpsPort;

    @Parameter(property = "microwave.stop", defaultValue = "8005")
    private int stopPort;

    @Parameter(property = "microwave.host", defaultValue = "localhost")
    private String host;

    @Parameter(property = "microwave.dir")
    protected String dir;

    @Parameter(property = "microwave.serverXml")
    private File serverXml;

    @Parameter(property = "microwave.keepServerXmlAsThis")
    private boolean keepServerXmlAsThis;

    @Parameter(property = "microwave.useTomcatDefaults", defaultValue = "true")
    private boolean useTomcatDefaults;

    @Parameter
    private Map<String, String> properties;

    @Parameter
    private Map<String, String> cxfServletParams;

    @Parameter(property = "microwave.quickSession", defaultValue = "true")
    private boolean quickSession;

    @Parameter(property = "microwave.tomcatScanning", defaultValue = "true")
    private boolean tomcatScanning;

    @Parameter(property = "microwave.tomcatAutoSetup", defaultValue = "true")
    private boolean tomcatAutoSetup;

    @Parameter(property = "microwave.skipHttp")
    private boolean skipHttp;

    @Parameter(property = "microwave.ssl")
    private boolean ssl;

    @Parameter(property = "microwave.keystoreFile")
    private String keystoreFile;

    @Parameter(property = "microwave.keystorePass")
    private String keystorePass;

    @Parameter(property = "microwave.keystoreType", defaultValue = "JKS")
    private String keystoreType;

    @Parameter(property = "microwave.clientAuth")
    private String clientAuth;

    @Parameter(property = "microwave.keyAlias")
    private String keyAlias;

    @Parameter(property = "microwave.sslProtocol")
    private String sslProtocol;

    @Parameter(property = "microwave.webXml")
    private String webXml;

    @Parameter
    private Microwave.LoginConfigBuilder loginConfig;

    @Parameter
    private Collection<Microwave.SecurityConstaintBuilder> securityConstraints = new LinkedList<>();

    @Parameter
    private Map<String, String> users;

    @Parameter
    private Map<String, String> roles;

    @Parameter(property = "microwave.http2")
    private boolean http2;

    @Parameter(property = "microwave.tempDir")
    private String tempDir;

    @Parameter(property = "microwave.webResourceCached", defaultValue = "true")
    private boolean webResourceCached;

    @Parameter(property = "microwave.conf")
    private String conf;

    @Parameter(property = "microwave.deleteBaseOnStartup", defaultValue = "true")
    private boolean deleteBaseOnStartup;

    @Parameter(property = "microwave.jaxrsMapping", defaultValue = "/*")
    private String jaxrsMapping;

    @Parameter(property = "microwave.cdiConversation", defaultValue = "true")
    private boolean cdiConversation;

    @Parameter(property = "microwave.skip")
    private boolean skip;

    @Parameter(property = "microwave.jaxrs-provider-setup", defaultValue = "true")
    private boolean jaxrsProviderSetup;

    @Parameter(property = "microwave.logging-global-setup", defaultValue = "true")
    private boolean loggingGlobalSetup;

    @Parameter(property = "microwave.shutdown-hook", defaultValue = "true")
    private boolean useShutdownHook;

    @Parameter
    private List<String> jsCustomizers;

    @Parameter
    private List<String> applicationScopes;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private List<File> modules;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "microwave.tomcatFilter")
    private String tomcatFilter;

    @Parameter(property = "microwave.context", defaultValue = "")
    private String context;

    @Parameter(property = "microwave.webapp")
    private File webapp;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().warn("Mojo skipped");
            return;
        }

        final Thread thread = Thread.currentThread();
        final ClassLoader loader = thread.getContextClassLoader();
        final ClassLoader appLoader = createClassLoader(loader);
        thread.setContextClassLoader(appLoader);
        try {
            final Microwave.Builder builder = getConfig();
            try (final Microwave microwave = new Microwave(builder) {
                @Override
                protected void beforeStart() {
                    scriptCustomization(jsCustomizers, "js", base.getAbsolutePath());
                }
            }) {
                microwave.start();
                final String fixedContext = ofNullable(context).orElse("");
                if (webapp == null) {
                    microwave.deployClasspath(fixedContext);
                } else {
                    microwave.deployWebapp(fixedContext, webapp);
                }
                new Scanner(System.in).next();
            }
        } finally {
            if (appLoader != loader) {
                try {
                    URLClassLoader.class.cast(appLoader).close();
                } catch (final IOException e) {
                    getLog().warn(e.getMessage(), e);
                }
            }
            thread.setContextClassLoader(loader);
        }
    }

    private void scriptCustomization(final List<String> customizers, final String ext, final String base) {
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
                bindings.put("catalinaBase", base);
                engine.eval(new StringReader(js), bindings);
            } catch (final ScriptException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    private ClassLoader createClassLoader(final ClassLoader parent) {
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
        return urls.isEmpty() ? parent : new URLClassLoader(urls.toArray(new URL[urls.size()]), parent) {
            @Override
            public boolean equals(final Object obj) {
                return super.equals(obj) || parent.equals(obj);
            }
        };
    }

    private Microwave.Builder getConfig() {
        final Microwave.Builder config = new Microwave.Builder();
        for (final Field field : MicrowaveRunMojo.class.getDeclaredFields()) {
            if ("properties".equals(field.getName())) {
                continue;
            }
            try {
                final Field configField = Microwave.Builder.class.getDeclaredField(field.getName());
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
        if (properties != null) {
            config.getProperties().putAll(properties);
        }
        return config;
    }
}
