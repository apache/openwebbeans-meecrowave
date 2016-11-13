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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.meecrowave.Meecrowave;

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
public class MeecrowaveRunMojo extends AbstractMojo {
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

    @Parameter(property = "meecrowave.jaxrsLogProviders", defaultValue = "true")
    private boolean tomcatWrapLoader;

    @Parameter(property = "meecrowave.useTomcatDefaults", defaultValue = "true")
    private boolean useTomcatDefaults;

    @Parameter
    private Map<String, String> properties;

    @Parameter
    private Map<String, String> cxfServletParams;

    @Parameter(property = "meecrowave.quickSession", defaultValue = "true")
    private boolean quickSession;

    @Parameter(property = "meecrowave.tomcatScanning", defaultValue = "true")
    private boolean tomcatScanning;

    @Parameter(property = "meecrowave.tomcatAutoSetup", defaultValue = "true")
    private boolean tomcatAutoSetup;

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

    @Parameter(property = "meecrowave.cdiConversation", defaultValue = "true")
    private boolean cdiConversation;

    @Parameter(property = "meecrowave.skip")
    private boolean skip;

    @Parameter(property = "meecrowave.jaxrs-default-providers")
    private String jaxrsDefaultProviders;

    @Parameter(property = "meecrowave.jaxrs-provider-setup", defaultValue = "true")
    private boolean jaxrsProviderSetup;

    @Parameter(property = "meecrowave.logging-global-setup", defaultValue = "true")
    private boolean loggingGlobalSetup;

    @Parameter(property = "meecrowave.shutdown-hook", defaultValue = "true")
    private boolean useShutdownHook;

    @Parameter
    private List<String> jsCustomizers;

    @Parameter
    private List<String> applicationScopes;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private List<File> modules;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "meecrowave.tomcatFilter")
    private String tomcatFilter;

    @Parameter(property = "meecrowave.context", defaultValue = "")
    private String context;

    // we don't need to resolve from maven coordinates cause can be added to the plugin deps, just here to reproduce manual deployments
    @Parameter(property = "meecrowave.shared-libraries")
    private String sharedLibraries;

    @Parameter(property = "meecrowave.webapp")
    private File webapp;

    @Parameter(property = "meecrowave.log4j2-jul-bridge", defaultValue = "true")
    private boolean useLog4j2JulLogManager;

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
            final Meecrowave.Builder builder = getConfig();
            try (final Meecrowave meecrowave = new Meecrowave(builder) {
                @Override
                protected void beforeStart() {
                    scriptCustomization(jsCustomizers, "js", base.getAbsolutePath());
                }
            }) {
                meecrowave.start();
                final String fixedContext = ofNullable(context).orElse("");
                if (webapp == null) {
                    meecrowave.deployClasspath(fixedContext);
                } else {
                    meecrowave.deployWebapp(fixedContext, webapp);
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

    private Meecrowave.Builder getConfig() {
        final Meecrowave.Builder config = new Meecrowave.Builder();
        for (final Field field : MeecrowaveRunMojo.class.getDeclaredFields()) {
            if ("properties".equals(field.getName())) {
                continue;
            }
            try {
                final Field configField = Meecrowave.Builder.class.getDeclaredField(field.getName());
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
