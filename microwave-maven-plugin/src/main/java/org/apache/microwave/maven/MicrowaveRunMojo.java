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
    @Parameter(name = "microwave.http", defaultValue = "8080")
    private int httpPort;

    @Parameter(name = "microwave.https", defaultValue = "8443")
    private int httpsPort;

    @Parameter(name = "microwave.stop", defaultValue = "8005")
    private int stopPort;

    @Parameter(name = "microwave.host", defaultValue = "localhost")
    private String host;

    @Parameter(name = "microwave.dir")
    protected String dir;

    @Parameter(name = "microwave.serverXml")
    private File serverXml;

    @Parameter(name = "microwave.keepServerXmlAsThis")
    private boolean keepServerXmlAsThis;

    @Parameter
    private Map<String, String> properties;

    @Parameter(name = "microwave.quickSession", defaultValue = "true")
    private boolean quickSession;

    @Parameter(name = "microwave.skipHttp")
    private boolean skipHttp;

    @Parameter(name = "microwave.ssl")
    private boolean ssl;

    @Parameter(name = "microwave.keystoreFile")
    private String keystoreFile;

    @Parameter(name = "microwave.keystorePass")
    private String keystorePass;

    @Parameter(name = "microwave.keystoreType", defaultValue = "JKS")
    private String keystoreType;

    @Parameter(name = "microwave.clientAuth")
    private String clientAuth;

    @Parameter(name = "microwave.keyAlias")
    private String keyAlias;

    @Parameter(name = "microwave.sslProtocol")
    private String sslProtocol;

    @Parameter(name = "microwave.webXml")
    private String webXml;

    @Parameter
    private Microwave.LoginConfigBuilder loginConfig;

    @Parameter
    private Collection<Microwave.SecurityConstaintBuilder> securityConstraints = new LinkedList<>();

    @Parameter
    private Map<String, String> users;

    @Parameter
    private Map<String, String> roles;

    @Parameter(name = "microwave.http2")
    private boolean http2;

    @Parameter(name = "microwave.tempDir")
    private String tempDir;

    @Parameter(name = "microwave.webResourceCached", defaultValue = "true")
    private boolean webResourceCached;

    @Parameter(name = "microwave.conf")
    private String conf;

    @Parameter(name = "microwave.deleteBaseOnStartup", defaultValue = "true")
    private boolean deleteBaseOnStartup;

    @Parameter(name = "microwave.jaxrsMapping", defaultValue = "/*")
    private String jaxrsMapping;

    @Parameter(name = "microwave.cdiConversation", defaultValue = "true")
    private boolean cdiConversation;

    @Parameter(name = "microwave.skip")
    private boolean skip;

    @Parameter(name = "microwave.skip-jaspic-setup", defaultValue = "false")
    private boolean skipJaspicProperty;

    @Parameter(name = "microwave.jaxrs-provider-setup", defaultValue = "true")
    private boolean jaxrsProviderSetup;

    @Parameter
    private List<String> jsCustomizers;

    @Parameter
    private List<String> applicationScopes;

    @Parameter
    private List<File> modules;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(name = "microwave.context", defaultValue = "")
    private String context;

    @Parameter(name = "microwave.webapp")
    private File webapp;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().warn("Mojo skipped");
            return;
        }

        final Thread thread = Thread.currentThread();
        final ClassLoader loader = thread.getContextClassLoader();
        thread.setContextClassLoader(createClassLoader(loader));
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
