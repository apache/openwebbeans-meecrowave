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
package org.apache.meecrowave;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Comparator.comparing;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.SessionCookieConfig;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Manager;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.MeecrowaveContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.text.StrLookup;
import org.apache.commons.text.StrSubstitutor;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.cxf.BusFactory;
import org.apache.johnzon.core.BufferStrategy;
import org.apache.meecrowave.api.StartListening;
import org.apache.meecrowave.api.StopListening;
import org.apache.meecrowave.cxf.ConfigurableBus;
import org.apache.meecrowave.cxf.CxfCdiAutoSetup;
import org.apache.meecrowave.io.IO;
import org.apache.meecrowave.logging.jul.Log4j2Logger;
import org.apache.meecrowave.logging.log4j2.Log4j2Shutdown;
import org.apache.meecrowave.logging.openwebbeans.Log4j2LoggerFactory;
import org.apache.meecrowave.logging.tomcat.Log4j2Log;
import org.apache.meecrowave.logging.tomcat.LogFacade;
import org.apache.meecrowave.openwebbeans.OWBAutoSetup;
import org.apache.meecrowave.runner.cli.CliOption;
import org.apache.meecrowave.service.ValueTransformer;
import org.apache.meecrowave.tomcat.CDIInstanceManager;
import org.apache.meecrowave.tomcat.LoggingAccessLogPattern;
import org.apache.meecrowave.tomcat.NoDescriptorRegistry;
import org.apache.meecrowave.tomcat.OWBJarScanner;
import org.apache.meecrowave.tomcat.ProvidedLoader;
import org.apache.meecrowave.tomcat.TomcatAutoInitializer;
import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.xbean.finder.ResourceFinder;
import org.apache.xbean.recipe.ObjectRecipe;
import org.apache.xbean.recipe.Option;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class Meecrowave implements AutoCloseable {
    private final Builder configuration;
    protected ConfigurableBus clientBus;
    protected File base;
    protected final File ownedTempDir;
    protected InternalTomcat tomcat;
    protected volatile Thread hook;

    // we can undeploy webapps with that later
    private final Map<String, Runnable> contexts = new HashMap<>();
    private Runnable postTask;
    private boolean clearCatalinaSystemProperties;

    public Meecrowave() {
        this(new Builder());
    }

    public Meecrowave(final Builder builder) {
        this.configuration = builder;
        this.ownedTempDir = new File(configuration.tempDir, "meecrowave_" + System.nanoTime());
    }

    public Builder getConfiguration() {
        return configuration;
    }

    public File getBase() {
        return base;
    }

    public Tomcat getTomcat() {
        return tomcat;
    }

    public boolean isServing() {
        return tomcat != null && tomcat.getHost().getState() == LifecycleState.STARTED;
    }

    public void undeploy(final String root) {
        ofNullable(this.contexts.remove(root)).ifPresent(Runnable::run);
    }

    public Meecrowave deployClasspath(final DeploymentMeta meta) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader parentLoader = tomcat.getServer().getParentClassLoader();
        if (parentLoader.getParent() == classLoader) {
            classLoader = parentLoader;
        }

        final ProvidedLoader loader = new ProvidedLoader(classLoader, configuration.isTomcatWrapLoader());
        final Consumer<Context> builtInCustomizer = c -> c.setLoader(loader);
        return deployWebapp(new DeploymentMeta(meta.context, meta.docBase, ofNullable(meta.consumer).map(c -> (Consumer<Context>) ctx -> {
            builtInCustomizer.accept(ctx);
            c.accept(ctx);
        }).orElse(builtInCustomizer)));
    }

    // shortcut
    public Meecrowave deployClasspath() {
        return deployClasspath("");
    }

    // shortcut
    public Meecrowave bake(final Consumer<Context> customizer) {
        start();
        return deployClasspath(new DeploymentMeta("", null, customizer));
    }

    // shortcut (used by plugins)
    public Meecrowave deployClasspath(final String context) {
        return deployClasspath(new DeploymentMeta(context, null, null));
    }

    // shortcut
    public Meecrowave deployWebapp(final File warOrDir) {
        return deployWebapp("", warOrDir);
    }

    // shortcut (used by plugins)
    public Meecrowave deployWebapp(final String context, final File warOrDir) {
        return deployWebapp(new DeploymentMeta(context, warOrDir, null));
    }

    public Meecrowave deployWebapp(final DeploymentMeta meta) {
        if (contexts.containsKey(meta.context)) {
            throw new IllegalArgumentException("Already deployed: '" + meta.context + "'");
        }
        // always nice to see the deployment with something else than internals
        final String base = tomcat.getService().findConnectors().length > 0 ?
                (configuration.getActiveProtocol() + "://" + tomcat.getHost().getName() + ':' + configuration.getActivePort()) : "";
        new LogFacade(Meecrowave.class.getName()).info("--------------- " + base + meta.context);


        final OWBJarScanner scanner = new OWBJarScanner();
        final StandardContext ctx = new StandardContext() {
            @Override
            public void setApplicationEventListeners(final Object[] listeners) {
                if (listeners == null) {
                    super.setApplicationEventListeners(null);
                    return;
                }

                // ensure owb is first and cxf is last otherwise surprises,
                // if we don't -> no @RequestScoped in request listeners :(
                for (int i = 1; i < listeners.length; i++) {
                    if (OWBAutoSetup.EagerBootListener.class.isInstance(listeners[i])) {
                        final Object first = listeners[0];
                        listeners[0] = listeners[i];
                        listeners[i] = first;
                        break;
                    }
                }

                // and finally let it go after our re-ordering
                super.setApplicationEventListeners(listeners);
            }
        };
        ctx.setPath(meta.context);
        ctx.setName(meta.context);
        ctx.setJarScanner(scanner);
        ctx.setInstanceManager(new CDIInstanceManager());
        ofNullable(meta.docBase).ifPresent(d -> {
            try {
                ctx.setDocBase(meta.docBase.getCanonicalPath());
            } catch (final IOException e) {
                ctx.setDocBase(meta.docBase.getAbsolutePath());
            }
        });
        ofNullable(configuration.tomcatFilter).ifPresent(filter -> {
            try {
                scanner.setJarScanFilter(JarScanFilter.class.cast(Thread.currentThread().getContextClassLoader().loadClass(filter).newInstance()));
            } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException(e);
            }
        });

        final AtomicReference<Runnable> releaseSCI = new AtomicReference<>();
        final ServletContainerInitializer meecrowaveInitializer = (c, ctx1) -> {
            new OWBAutoSetup().onStartup(c, ctx1);
            new CxfCdiAutoSetup().onStartup(c, ctx1);
            new TomcatAutoInitializer().onStartup(c, ctx1);

            if (configuration.isInjectServletContainerInitializer()) {
                final Field f;
                try { // now cdi is on, we can inject cdi beans in ServletContainerInitializer
                    f = StandardContext.class.getDeclaredField("initializers");
                    if (!f.isAccessible()) {
                        f.setAccessible(true);
                    }
                } catch (final Exception e) {
                    throw new IllegalStateException("Bad tomcat version", e);
                }

                final List<AutoCloseable> cc;
                try {
                    cc = ((Map<ServletContainerInitializer, Set<Class<?>>>) f.get(ctx)).keySet().stream()
                            .filter(i -> !i.getClass().getName().startsWith(Meecrowave.class.getName()))
                            .map(i -> {
                                try {
                                    return this.inject(i);
                                } catch (final IllegalArgumentException iae) {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(toList());
                } catch (final IllegalAccessException e) {
                    throw new IllegalStateException("Can't read initializers", e);
                }
                releaseSCI.set(() -> cc.forEach(closeable -> {
                    try {
                        closeable.close();
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                }));
            }
        };

        ctx.addLifecycleListener(new MeecrowaveContextConfig(configuration, meta.docBase != null, meecrowaveInitializer));
        ctx.addLifecycleListener(event -> {
            switch (event.getType()) {
                case Lifecycle.BEFORE_START_EVENT:
                    if (configuration.getWebSessionCookieConfig() != null) {
                        final Properties p = new Properties();
                        try {
                            p.load(new StringReader(configuration.getWebSessionCookieConfig()));
                        } catch (final IOException e) {
                            throw new IllegalArgumentException(e);
                        }
                        if (p.containsKey("domain")) {
                            ctx.setSessionCookieDomain(p.getProperty("domain"));
                        }
                        if (p.containsKey("path")) {
                            ctx.setSessionCookiePath(p.getProperty("path"));
                        }
                        if (p.containsKey("name")) {
                            ctx.setSessionCookieName(p.getProperty("name"));
                        }
                        if (p.containsKey("use-trailing-slash")) {
                            ctx.setSessionCookiePathUsesTrailingSlash(Boolean.parseBoolean(p.getProperty("use-trailing-slash")));
                        }
                        if (p.containsKey("http-only")) {
                            ctx.setUseHttpOnly(Boolean.parseBoolean(p.getProperty("http-only")));
                        }
                        if (p.containsKey("secured")) {
                            final SessionCookieConfig sessionCookieConfig = ctx.getServletContext().getSessionCookieConfig();
                            sessionCookieConfig.setSecure(Boolean.parseBoolean(p.getProperty("secured")));
                        }
                    }
                    break;
                case Lifecycle.AFTER_START_EVENT:
                    ctx.getResources().setCachingAllowed(configuration.webResourceCached);
                    break;
                case Lifecycle.BEFORE_INIT_EVENT:
                    ctx.getServletContext().setAttribute("meecrowave.configuration", configuration);
                    ctx.getServletContext().setAttribute("meecrowave.instance", Meecrowave.this);
                    if (configuration.loginConfig != null) {
                        ctx.setLoginConfig(configuration.loginConfig.build());
                    }
                    for (final SecurityConstaintBuilder sc : configuration.securityConstraints) {
                        ctx.addConstraint(sc.build());
                    }
                    if (configuration.webXml != null) {
                        ctx.getServletContext().setAttribute(Globals.ALT_DD_ATTR, configuration.webXml);
                    }
                    break;
                default:
            }

        });
        ctx.addLifecycleListener(new Tomcat.FixContextListener()); // after having configured the security!!!

        ctx.addServletContainerInitializer(meecrowaveInitializer, emptySet());

        if (configuration.isUseTomcatDefaults()) {
            ctx.setSessionTimeout(configuration.getWebSessionTimeout() != null ? configuration.getWebSessionTimeout() : 30);
            ctx.addWelcomeFile("index.html");
            ctx.addWelcomeFile("index.htm");
            try {
                final Field mimesField = Tomcat.class.getDeclaredField("DEFAULT_MIME_MAPPINGS");
                if (!mimesField.isAccessible()) {
                    mimesField.setAccessible(true);
                }
                final String[] defaultMimes = String[].class.cast(mimesField.get(null));
                for (int i = 0; i < defaultMimes.length; ) {
                    ctx.addMimeMapping(defaultMimes[i++], defaultMimes[i++]);
                }
            } catch (final NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("Incompatible Tomcat", e);
            }
        } else if (configuration.getWebSessionTimeout() != null) {
            ctx.setSessionTimeout(configuration.getWebSessionTimeout());
        }

        ofNullable(meta.consumer).ifPresent(c -> c.accept(ctx));

        final Host host = tomcat.getHost();
        host.addChild(ctx);

        final ClassLoader classLoader = ctx.getLoader().getClassLoader();
        if (host.getState().isAvailable()) {
            fire(new StartListening(findFirstConnector(), host, ctx), classLoader);
        }
        contexts.put(meta.context, () -> {
            if (host.getState().isAvailable()) {
                fire(new StopListening(findFirstConnector(), host, ctx), classLoader);
            }
            ofNullable(releaseSCI.get()).ifPresent(Runnable::run);
            tomcat.getHost().removeChild(ctx);
        });
        return this;
    }

    public Meecrowave bake() {
        return bake("");
    }

    public Meecrowave bake(final String ctx) {
        start();
        return deployClasspath(ctx);
    }

    public Meecrowave start() {
        final Map<String, String> systemPropsToRestore = new HashMap<>();

        if (configuration.getMeecrowaveProperties() != null && !"meecrowave.properties".equals(configuration.getMeecrowaveProperties())) {
            configuration.loadFrom(configuration.getMeecrowaveProperties());
        }

        if (configuration.isUseLog4j2JulLogManager()) { // /!\ don't move this line or add anything before without checking log setup
            System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        }

        if (configuration.loggingGlobalSetup) {

            setSystemProperty(systemPropsToRestore, "log4j.shutdownHookEnabled", "false");
            setSystemProperty(systemPropsToRestore, "openwebbeans.logging.factory", Log4j2LoggerFactory.class.getName());
            setSystemProperty(systemPropsToRestore, "org.apache.cxf.Logger", Log4j2Logger.class.getName());
            setSystemProperty(systemPropsToRestore, "org.apache.tomcat.Logger", Log4j2Log.class.getName());

            postTask = () -> {
                new Log4j2Shutdown().shutodwn();
                systemPropsToRestore.forEach((key, value) -> {
                    if (value == null) {
                        System.clearProperty(key);
                    } else {
                        System.setProperty(key, value);
                    }
                });
            };
        }

        setupJmx(configuration.isTomcatNoJmx());

        clearCatalinaSystemProperties = System.getProperty("catalina.base") == null && System.getProperty("catalina.home") == null;

        if (configuration.quickSession) {
            tomcat = new TomcatWithFastSessionIDs();
        } else {
            tomcat = new InternalTomcat();
        }

        { // setup
            base = new File(newBaseDir());

            final File conf = createDirectory(base, "conf");
            createDirectory(base, "lib");
            createDirectory(base, "logs");
            createDirectory(base, "temp");
            createDirectory(base, "work");
            createDirectory(base, "webapps");

            synchronize(conf, configuration.conf);
        }

        final Properties props = configuration.properties;
        StrSubstitutor substitutor = null;
        for (final String s : props.stringPropertyNames()) {
            final String v = props.getProperty(s);
            if (v != null && v.contains("${")) {
                if (substitutor == null) {
                    final Map<String, String> placeHolders = new HashMap<>();
                    placeHolders.put("meecrowave.embedded.http", Integer.toString(configuration.httpPort));
                    placeHolders.put("meecrowave.embedded.https", Integer.toString(configuration.httpsPort));
                    placeHolders.put("meecrowave.embedded.stop", Integer.toString(configuration.stopPort));
                    substitutor = new StrSubstitutor(placeHolders);
                }
                props.put(s, substitutor.replace(v));
            }
        }

        final File conf = new File(base, "conf");
        final File webapps = new File(base, "webapps");

        tomcat.setBaseDir(base.getAbsolutePath());
        tomcat.setHostname(configuration.host);

        final boolean initialized;
        if (configuration.serverXml != null) {
            final File file = new File(conf, "server.xml");
            if (!file.equals(configuration.serverXml)) {
                try (final InputStream is = new FileInputStream(configuration.serverXml);
                     final FileOutputStream fos = new FileOutputStream(file)) {
                    IO.copy(is, fos);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }

            // respect config (host/port) of the Configuration
            final QuickServerXmlParser ports = QuickServerXmlParser.parse(file);
            if (configuration.keepServerXmlAsThis) {
                configuration.httpPort = Integer.parseInt(ports.http());
                configuration.stopPort = Integer.parseInt(ports.stop());
            } else {
                final Map<String, String> replacements = new HashMap<>();
                replacements.put(ports.http(), String.valueOf(configuration.httpPort));
                replacements.put(ports.https(), String.valueOf(configuration.httpsPort));
                replacements.put(ports.stop(), String.valueOf(configuration.stopPort));

                String serverXmlContent;
                try (final InputStream stream = new FileInputStream(file)) {
                    serverXmlContent = IO.toString(stream);
                    for (final Map.Entry<String, String> pair : replacements.entrySet()) {
                        serverXmlContent = serverXmlContent.replace(pair.getKey(), pair.getValue());
                    }
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
                try (final OutputStream os = new FileOutputStream(file)) {
                    os.write(serverXmlContent.getBytes(StandardCharsets.UTF_8));
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }

            tomcat.server(createServer(file.getAbsolutePath()));
            initialized = true;
        } else {
            tomcat.getServer().setPort(configuration.stopPort);
            initialized = false;
        }

        ofNullable(configuration.getSharedLibraries()).map(File::new).filter(File::isDirectory).ifPresent(libRoot -> {
            final Collection<URL> libs = new ArrayList<>();
            try {
                libs.add(libRoot.toURI().toURL());
            } catch (final MalformedURLException e) {
                throw new IllegalStateException(e);
            }
            libs.addAll(ofNullable(libRoot.listFiles((dir, name) -> name.endsWith(".jar") || name.endsWith(".zip")))
                    .map(Stream::of).map(s -> s.map(f -> {
                        try {
                            return f.toURI().toURL();
                        } catch (final MalformedURLException e) {
                            throw new IllegalStateException(e);
                        }
                    }).collect(toList()))
                    .orElse(emptyList()));
            tomcat.getServer().setParentClassLoader(new MeecrowaveContainerLoader(libs.toArray(new URL[libs.size()]), Thread.currentThread().getContextClassLoader()));
        });

        if (!initialized) {
            tomcat.setHostname(configuration.host);
            tomcat.getEngine().setDefaultHost(configuration.host);
            final StandardHost host = new StandardHost();
            host.setName(configuration.host);
            host.setAppBase(webapps.getAbsolutePath());
            host.setUnpackWARs(true); // forced for now cause OWB doesn't support war:file:// urls
            try {
                host.setWorkDir(new File(base, "work").getCanonicalPath());
            } catch (final IOException e) {
                host.setWorkDir(new File(base, "work").getAbsolutePath());
            }
            tomcat.setHost(host);
        }

        ofNullable(configuration.getTomcatAccessLogPattern())
                .ifPresent(pattern -> tomcat.getHost().getPipeline().addValve(new LoggingAccessLogPattern(pattern)));
        final List<Valve> valves = buildValves();
        if (!valves.isEmpty()) {
            final Pipeline pipeline = tomcat.getHost().getPipeline();
            valves.forEach(pipeline::addValve);
        }

        if (configuration.realm != null) {
            tomcat.getEngine().setRealm(configuration.realm);
        }

        if (tomcat.getRawConnector() == null && !configuration.skipHttp) {
            final Connector connector = createConnector();
            connector.setPort(configuration.httpPort);
            if (connector.getAttribute("connectionTimeout") == null) {
                connector.setAttribute("connectionTimeout", "3000");
            }

            tomcat.getService().addConnector(connector);
            tomcat.setConnector(connector);
        }

        // create https connector
        if (configuration.ssl) {
            final Connector httpsConnector = createConnector();
            httpsConnector.setPort(configuration.httpsPort);
            httpsConnector.setSecure(true);
            httpsConnector.setScheme("https");
            httpsConnector.setProperty("SSLEnabled", "true");
            if (configuration.sslProtocol != null) {
                configuration.property("connector.sslhostconfig.sslProtocol", configuration.sslProtocol);
            }
            if (configuration.properties.getProperty("connector.sslhostconfig.hostName") != null) {
                httpsConnector.setAttribute("defaultSSLHostConfigName", configuration.properties.getProperty("connector.sslhostconfig.hostName"));
            }
            if (configuration.keystoreFile != null) {
                configuration.property("connector.sslhostconfig.certificateKeystoreFile", configuration.keystoreFile);
            }
            if (configuration.keystorePass != null) {
                configuration.property("connector.sslhostconfig.certificateKeystorePassword", configuration.keystorePass);
            }
            configuration.property("connector.sslhostconfig.certificateKeystoreType", configuration.keystoreType);
            if (configuration.clientAuth != null) {
                httpsConnector.setAttribute("clientAuth", configuration.clientAuth);
            }

            if (configuration.keyAlias != null) {
                configuration.property("connector.sslhostconfig.certificateKeyAlias", configuration.keyAlias);
            }
            if (configuration.http2) {
                httpsConnector.addUpgradeProtocol(new Http2Protocol());
            }
            final List<SSLHostConfig> buildSslHostConfig = buildSslHostConfig();
            buildSslHostConfig.forEach(sslHostConf -> {
                if (isCertificateFromClasspath(sslHostConf.getCertificateKeystoreFile())) {
                    copyCertificateToConfDir(sslHostConf.getCertificateKeystoreFile());
                    sslHostConf.setCertificateKeystoreFile(base.getAbsolutePath() + "/conf/" + sslHostConf.getCertificateKeystoreFile());
                }
                if (isCertificateFromClasspath(sslHostConf.getCertificateKeyFile())) {
                    copyCertificateToConfDir(sslHostConf.getCertificateKeyFile());
                    sslHostConf.setCertificateKeyFile(base.getAbsolutePath() + "/conf/" + sslHostConf.getCertificateKeyFile());
                    copyCertificateToConfDir(sslHostConf.getCertificateFile());
                    sslHostConf.setCertificateFile(base.getAbsolutePath() + "/conf/" + sslHostConf.getCertificateFile());
                }
                if (isCertificateFromClasspath(sslHostConf.getTruststoreFile())) {
                    copyCertificateToConfDir(sslHostConf.getTruststoreFile());
                    sslHostConf.setTruststoreFile(base.getAbsolutePath() + "/conf/" + sslHostConf.getTruststoreFile());
                }
                if (isCertificateFromClasspath(sslHostConf.getCertificateChainFile())) {
                    copyCertificateToConfDir(sslHostConf.getCertificateChainFile());
                    sslHostConf.setCertificateChainFile(base.getAbsolutePath() + "/conf/" + sslHostConf.getCertificateChainFile());
                }
            });
            
            buildSslHostConfig.forEach(httpsConnector::addSslHostConfig);

            if (configuration.defaultSSLHostConfigName != null) {
                httpsConnector.setAttribute("defaultSSLHostConfigName", configuration.defaultSSLHostConfigName);
            }
            tomcat.getService().addConnector(httpsConnector);
            if (configuration.skipHttp) {
                tomcat.setConnector(httpsConnector);
            }
        }

        for (final Connector c : configuration.connectors) {
            tomcat.getService().addConnector(c);
        }
        if (!configuration.skipHttp && !configuration.ssl && !configuration.connectors.isEmpty()) {
            tomcat.setConnector(configuration.connectors.iterator().next());
        }

        if (configuration.users != null) {
            for (final Map.Entry<String, String> user : configuration.users.entrySet()) {
                tomcat.addUser(user.getKey(), user.getValue());
            }
        }
        if (configuration.roles != null) {
            for (final Map.Entry<String, String> user : configuration.roles.entrySet()) {
                for (final String role : user.getValue().split(" *, *")) {
                    tomcat.addRole(user.getKey(), role);
                }
            }
        }

        StreamSupport.stream(ServiceLoader.load(Meecrowave.InstanceCustomizer.class).spliterator(), false)
                .forEach(c -> c.accept(tomcat));
        configuration.instanceCustomizers.forEach(c -> c.accept(tomcat));

        beforeStart();


        if (configuration.initializeClientBus && BusFactory.getDefaultBus(false) == null) {
            clientBus = new ConfigurableBus();
            clientBus.initProviders(configuration,
                    ofNullable(Thread.currentThread().getContextClassLoader()).orElseGet(ClassLoader::getSystemClassLoader));
            clientBus.addClientLifecycleListener();
        }

        try {
            if (!initialized) {
                tomcat.init();
            }

            tomcat.getHost().addLifecycleListener(event -> {
                if (!Host.class.isInstance(event.getSource())) {
                    return;
                }
                broadcastHostEvent(event.getType(), Host.class.cast(event.getSource()));
            });

            tomcat.start();
        } catch (final LifecycleException e) {
            throw new IllegalStateException(e);
        }
        ofNullable(configuration.getPidFile()).ifPresent(pidFile -> {
            if (pidFile.getParentFile() != null && !pidFile.getParentFile().isDirectory() && !pidFile.getParentFile().mkdirs()) {
                throw new IllegalArgumentException("Can't create " + pidFile);
            }
            final String pid = ManagementFactory.getRuntimeMXBean().getName();
            final int at = pid.indexOf('@');
            try (final Writer w = new FileWriter(pidFile)) {
                w.write(at > 0 ? pid.substring(0, at) : pid);
            } catch (final IOException e) {
                throw new IllegalStateException("Can't write the pid in " + pid, e);
            }
        });
        if (configuration.isUseShutdownHook()) {
            hook = new Thread(() -> {
                hook = null; // prevent close to remove the hook which would throw an exception
                close();
            }, "meecrowave-stop-hook");
            Runtime.getRuntime().addShutdownHook(hook);
        }
        return this;
    }

    private boolean isCertificateFromClasspath(final String certificate) {
        final BiPredicate<String, String> equals = System.getProperty("os.name", "ignore").toLowerCase(ROOT).contains("win") ?
                String::equalsIgnoreCase : String::equals;
        return certificate != null && !(new File(certificate).exists()) 
                && !equals.test(
                        Paths.get(System.getProperty("user.home")).resolve(".keystore").toAbsolutePath().normalize().toString(),
                        Paths.get(certificate).toAbsolutePath().normalize().toString());
    }
    
    private void copyCertificateToConfDir(String certificate) {
        InputStream resourceAsStream = null;
        try {
            final Path dstFile = Paths.get(base.getAbsolutePath() + "/conf/" + certificate);
            resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(certificate);
            if (resourceAsStream == null) {
                resourceAsStream = new FileInputStream(new File((this.getClass().getResource("/").toString().replaceAll("file:", "") + "/" + certificate)));
            }
            Files.copy(resourceAsStream, dstFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (resourceAsStream != null) {
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    /**
     * Store away the current system property for restoring it later
     * during shutdown.
     * @param backupPropertyMap a Map to store away the previous value before setting the newValue
     * @param propertyKey
     * @param newValue
     */
    private void setSystemProperty(Map<String, String> backupPropertyMap, String propertyKey, String newValue) {
        backupPropertyMap.put(propertyKey, System.getProperty(propertyKey));

        System.setProperty(propertyKey, newValue);
    }

    private void broadcastHostEvent(final String event, final Host host) {
        switch (event) {
            case Lifecycle.AFTER_START_EVENT: {
                final Connector connector = findFirstConnector();
                findContexts(host).forEach(ctx -> fire(new StartListening(connector, host, ctx), ctx.getLoader().getClassLoader()));
                break;
            }
            case Lifecycle.BEFORE_STOP_EVENT: {
                final Connector connector = findFirstConnector();
                findContexts(host).forEach(ctx -> fire(new StopListening(connector, host, ctx), ctx.getLoader().getClassLoader()));
                break;
            }
            default:
        }
    }

    private Connector findFirstConnector() {
        return Stream.of(tomcat.getServer().findServices())
                .flatMap(s -> Stream.of(s.findConnectors()))
                .findFirst()
                .orElse(null);
    }

    private Stream<Context> findContexts(final Host host) {
        return Stream.of(host.findChildren())
                .filter(Context.class::isInstance)
                .map(Context.class::cast)
                .filter(ctx -> ctx.getState().isAvailable());
    }

    private <T> void fire(final T event, final ClassLoader classLoader) {
        final Thread thread = Thread.currentThread();
        final ClassLoader loader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            WebBeansContext.currentInstance()
                    .getBeanManagerImpl()
                    .getEvent()
                    .select(Class.class.cast(event.getClass()))
                    .fire(event);
        } finally {
            thread.setContextClassLoader(loader);
        }
    }

    private void setupJmx(final boolean skip) {
        try {
            final Field registry = Registry.class.getDeclaredField("registry");
            registry.setAccessible(true);
            registry.set(null, skip ? new NoDescriptorRegistry() : new Registry());
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Syntax uses:
     * <code>
     *     valves.myValve1._className = org.apache.meecrowave.tomcat.LoggingAccessLogPattern
     *     valves.myValve1._order = 0
     *
     *     valves.myValve1._className = SSOVa
     *     valves.myValve1._order = 1
     *     valves.myValve1.showReportInfo = false
     * </code>
     *
     * @return the list of valve from the properties.
     */
    private List<Valve> buildValves() {
        final List<Valve> valves = new ArrayList<>();
        configuration.properties.stringPropertyNames().stream()
                                .filter(key -> key.startsWith("valves.") && key.endsWith("._className"))
                                .sorted(comparing(key -> Integer.parseInt(configuration.properties
                                        .getProperty(key.replaceFirst("\\._className$", "._order"), "0"))))
                                .map(key -> key.split("\\."))
                                .filter(parts -> parts.length == 3)
                                .forEach(key -> {
            final String prefix = key[0] + '.' + key[1] + '.';
            final ObjectRecipe recipe = newRecipe(configuration.properties.getProperty(prefix + key[2]));
            configuration.properties.stringPropertyNames().stream()
                    .filter(it -> it.startsWith(prefix) && !it.endsWith("._order") && !it.endsWith("._className"))
                    .forEach(propKey -> {
                        final String value = configuration.properties.getProperty(propKey);
                        recipe.setProperty(propKey.substring(prefix.length()), value);
                    });
            valves.add(Valve.class.cast(recipe.create(Thread.currentThread().getContextClassLoader())));
        });
        return valves;
    }

    private List<SSLHostConfig> buildSslHostConfig() {
        final List<SSLHostConfig> sslHostConfigs = new ArrayList<>();
        // Configures default SSLHostConfig
        final ObjectRecipe defaultSslHostConfig = newRecipe(SSLHostConfig.class.getName());
        for (final String key : configuration.properties.stringPropertyNames()) {
            if (key.startsWith("connector.sslhostconfig.") && key.split("\\.").length == 3) {
                final String substring = key.substring("connector.sslhostconfig.".length());
                defaultSslHostConfig.setProperty(substring, configuration.properties.getProperty(key));
            }
        }
        if (!defaultSslHostConfig.getProperties().isEmpty()) {
            sslHostConfigs.add(SSLHostConfig.class.cast(defaultSslHostConfig.create()));
        }
        // Allows to add N Multiple SSLHostConfig elements not including the default one.
        final Collection<Integer> itemNumbers = configuration.properties.stringPropertyNames()
                                .stream()
                                .filter(key -> (key.startsWith("connector.sslhostconfig.") && key.split("\\.").length == 4))
                                .map(key -> Integer.parseInt(key.split("\\.")[2]))
                                .collect(toSet());
        itemNumbers.stream().sorted().forEach(itemNumber -> {
            final ObjectRecipe recipe = newRecipe(SSLHostConfig.class.getName());
            final String prefix = "connector.sslhostconfig." + itemNumber + '.';
            configuration.properties.stringPropertyNames().stream()
                                    .filter(k -> k.startsWith(prefix))
                                    .forEach(key -> {
                                        final String keyName = key.split("\\.")[3];
                                        recipe.setProperty(keyName, configuration.properties.getProperty(key));
                                    });
            if (!recipe.getProperties().isEmpty()) {
                final SSLHostConfig sslHostConfig = SSLHostConfig.class.cast(recipe.create());
                sslHostConfigs.add(sslHostConfig);
                new LogFacade(Meecrowave.class.getName())
                        .info("Created SSLHostConfig #" + itemNumber + " (" + sslHostConfig.getHostName() + ")");
            }
        });
        return sslHostConfigs;
    }

    protected void beforeStart() {
        // no-op
    }

    protected void beforeStop() {
        // no-op
    }

    public <T> AutoCloseable inject(final T instance) {
        final BeanManager bm = CDI.current().getBeanManager();
        final AnnotatedType<?> annotatedType = bm.createAnnotatedType(instance.getClass());
        final InjectionTarget injectionTarget = bm.createInjectionTarget(annotatedType);
        final CreationalContext<Object> creationalContext = bm.createCreationalContext(null);
        injectionTarget.inject(instance, creationalContext);
        return creationalContext::release;
    }

    @Override
    public synchronized void close() {
        if (tomcat == null) {
            return;
        }
        if (hook != null) {
            Runtime.getRuntime().removeShutdownHook(hook);
            this.hook = null;
        }
        beforeStop();
        if (MeecrowaveContainerLoader.class.isInstance(tomcat.getServer().getParentClassLoader())) {
            try {
                MeecrowaveContainerLoader.class.cast(tomcat.getServer().getParentClassLoader()).close();
            } catch (final IOException e) {
                new LogFacade(Meecrowave.class.getName()).error(e.getMessage(), e);
            }
        }
        try {
            contexts.values().forEach(Runnable::run);
        } finally {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (final LifecycleException e) {
                throw new IllegalStateException(e);
            } finally {
                if (BusFactory.getDefaultBus(false) == clientBus) { // after if runnables or listeners trigger CXF
                    BusFactory.setDefaultBus(null);
                }
                tomcat = null; // ensure we can call close() N times and not have side effects
                contexts.clear();
                if (clearCatalinaSystemProperties) {
                    Stream.of("catalina.base", "catalina.home").forEach(System::clearProperty);
                }
                if (configuration.isUseLog4j2JulLogManager()) {
                    System.clearProperty("java.util.logging.manager");
                }
                ofNullable(postTask).ifPresent(Runnable::run);
                postTask = null;
                try {
                    if (base != null) {
                        IO.delete(base);
                    }

                    if (ownedTempDir != null) {
                        IO.delete(ownedTempDir);
                    }
                } catch (final IllegalArgumentException /*does not exist from the hook*/ e) {
                    // no-op
                } finally {
                    base = null;

                    // not very important if we can't delete it since next restart will write another value normally
                    ofNullable(configuration.getPidFile()).ifPresent(File::delete);
                }
            }
        }
    }

    protected Connector createConnector() {
        final Connector connector;
        final Properties properties = configuration.properties;
        if (properties != null) {
            final Map<String, String> attributes = new HashMap<>();
            final ObjectRecipe recipe = newRecipe(Connector.class.getName());
            for (final String key : properties.stringPropertyNames()) {
                if (!key.startsWith("connector.")) {
                    continue;
                }

                final String substring = key.substring("connector.".length());
                if (substring.startsWith("sslhostconfig.")) {
                    continue;
                }

                if (!substring.startsWith("attributes.")) {
                    recipe.setProperty(substring, properties.getProperty(key));
                } else {
                    attributes.put(substring.substring("attributes.".length()), properties.getProperty(key));
                }
            }
            connector = recipe.getProperties().isEmpty() ? new Connector() : Connector.class.cast(recipe.create());
            for (final Map.Entry<String, String> attr : attributes.entrySet()) {
                connector.setAttribute(attr.getKey(), attr.getValue());
            }
        } else {
            connector = new Connector();
        }
        return connector;
    }

    private static Server createServer(final String serverXml) {
        final Catalina catalina = new Catalina() {
            // skip few init we don't need *here*
            @Override
            protected void initDirs() {
                // no-op
            }

            @Override
            protected void initStreams() {
                // no-op
            }

            @Override
            protected void initNaming() {
                // no-op
            }
        };
        catalina.setConfigFile(serverXml);
        catalina.load();
        return catalina.getServer();
    }

    private File createDirectory(final File parent, final String directory) {
        final File dir = new File(parent, directory);
        IO.mkdirs(dir);
        return dir;
    }

    private void synchronize(final File base, final String resourceBase) {
        if (resourceBase == null) {
            return;
        }

        try {
            final Map<String, URL> urls = new ResourceFinder("").getResourcesMap(resourceBase);
            for (final Map.Entry<String, URL> u : urls.entrySet()) {
                try (final InputStream is = u.getValue().openStream()) {
                    final File to = new File(base, u.getKey());
                    try (final OutputStream os = new FileOutputStream(to)) {
                        IO.copy(is, os);
                    }
                    if ("server.xml".equals(u.getKey())) {
                        configuration.setServerXml(to.getAbsolutePath());
                    }
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String newBaseDir() {
        String dir = configuration.dir;
        if (dir != null) {
            final File dirFile = new File(dir);
            if (dirFile.exists()) {
                if (base != null && base.exists() && configuration.deleteBaseOnStartup) {
                    IO.delete(base);
                }
                return dir;
            }
            IO.mkdirs(dirFile);
            return dirFile.getAbsolutePath();
        }

        final String base = System.getProperty("meecrowave.base");
        if (base != null && new File(base).exists()) {
            return new File(base).getAbsolutePath();
        }

        final List<String> lookupPaths = new ArrayList<>();
        lookupPaths.add("target");
        lookupPaths.add("build");
        final File file = lookupPaths.stream()
                          .map(File::new)
                          .filter(File::isDirectory)
                          .findFirst()
                          .map(file1 -> new File(file1, "meecrowave-" + System.nanoTime())).orElse(ownedTempDir);
        IO.mkdirs(file);
        return file.getAbsolutePath();
    }

    public void await() {
        tomcat.getServer().await();
    }

    private static ObjectRecipe newRecipe(final String clazz) {
        final ObjectRecipe recipe = new ObjectRecipe(clazz);
        recipe.allow(Option.FIELD_INJECTION);
        recipe.allow(Option.PRIVATE_PROPERTIES);
        return recipe;
    }

    // this class holds all the built-in config,
    // extension can use extensions feature (see cli.html) which is basically the same kind of bean
    // accessible through builder.getExtension(type) builder being accessible through the meecrowave.configuration
    // attribute of the ServletContext.
    public static class Builder {
        @CliOption(name = "pid-file", description = "A file path to write the process id if the server starts")
        private File pidFile;

        @CliOption(name = "watcher-bouncing", description = "Activate redeployment on directories update using this bouncing.")
        private int watcherBouncing = 500;

        @CliOption(name = "http", description = "HTTP port")
        private int httpPort = 8080;

        @CliOption(name = "https", description = "HTTPS port")
        private int httpsPort = 8443;

        @CliOption(name = "stop", description = "Shutdown port if used or -1")
        private int stopPort = -1;

        @CliOption(name = "host", description = "Default host")
        private String host = "localhost";

        @CliOption(name = "dir", description = "Root folder if provided otherwise a fake one is created in tmp-dir")
        private String dir;

        @CliOption(name = "server-xml", description = "Provided server.xml")
        private File serverXml;

        @CliOption(name = "keep-server-xml-as-this", description = "Don't replace ports in server.xml")
        private boolean keepServerXmlAsThis;

        @CliOption(name = "properties", description = "Passthrough properties")
        private Properties properties = new Properties();

        @CliOption(name = "quick-session", description = "Should an unsecured but fast session id generator be used")
        private boolean quickSession = true;

        @CliOption(name = "skip-http", description = "Skip HTTP connector")
        private boolean skipHttp;

        @CliOption(name = "ssl", description = "Use HTTPS")
        private boolean ssl;

        @CliOption(name = "keystore-file", description = "HTTPS keystore location")
        private String keystoreFile;

        @CliOption(name = "keystore-password", description = "HTTPS keystore password")
        private String keystorePass;

        @CliOption(name = "keystore-type", description = "HTTPS keystore type")
        private String keystoreType = "JKS";

        @CliOption(name = "client-auth", description = "HTTPS keystore client authentication")
        private String clientAuth;

        @CliOption(name = "keystore-alias", description = "HTTPS keystore alias")
        private String keyAlias;

        @CliOption(name = "ssl-protocol", description = "HTTPS protocol")
        private String sslProtocol;

        @CliOption(name = "web-xml", description = "Global web.xml")
        private String webXml;

        @CliOption(name = "login-config", description = "web.xml login config")
        private LoginConfigBuilder loginConfig;

        @CliOption(name = "security-constraint", description = "web.xml security constraint")
        private Collection<SecurityConstaintBuilder> securityConstraints = new LinkedList<>();

        @CliOption(name = "realm", description = "realm")
        private Realm realm;

        @CliOption(name = "users", description = "In memory users")
        private Map<String, String> users;

        @CliOption(name = "roles", description = "In memory roles")
        private Map<String, String> roles;

        @CliOption(name = "http2", description = "Activate HTTP 2")
        private boolean http2;

        @CliOption(name = "connector", description = "Custom connectors")
        private final Collection<Connector> connectors = new ArrayList<>();

        @CliOption(name = "tmp-dir", description = "Temporary directory")
        private String tempDir = System.getProperty("java.io.tmpdir");

        @CliOption(name = "web-resource-cached", description = "Cache web resources")
        private boolean webResourceCached = true;

        @CliOption(name = "conf", description = "Conf folder to synchronize")
        private String conf;

        @CliOption(name = "delete-on-startup", description = "Should the directory be cleaned on startup if existing")
        private boolean deleteBaseOnStartup = true;

        @CliOption(name = "jaxrs-mapping", description = "Default jaxrs mapping")
        private String jaxrsMapping = "/*";

        @CliOption(name = "cdi-conversation", description = "Should CDI conversation be activated")
        private boolean cdiConversation;

        @CliOption(name = "jaxrs-provider-setup", description = "Should default JAX-RS provider be configured")
        private boolean jaxrsProviderSetup = true;

        @CliOption(name = "jaxrs-default-providers", description = "If jaxrsProviderSetup is true the list of default providers to load (or defaulting to johnson jsonb and jsonp ones)")
        private String jaxrsDefaultProviders;

        @CliOption(name = "jaxrs-beanvalidation", description = "Should bean validation be activated on JAX-RS endpoint if present in the classpath.")
        private boolean jaxrsAutoActivateBeanValidation = true;

        @CliOption(name = "jaxrs-log-provider", description = "Should JAX-RS providers be logged")
        private boolean jaxrsLogProviders = false;

        @CliOption(name = "jsonp-buffer-strategy", description = "JSON-P JAX-RS provider buffer strategy (see johnzon)")
        private String jsonpBufferStrategy = BufferStrategy.QUEUE.name();

        @CliOption(name = "jsonp-max-string-length", description = "JSON-P JAX-RS provider max string limit size (see johnzon)")
        private int jsonpMaxStringLen = 64 * 1024;

        @CliOption(name = "jsonp-read-buffer-length", description = "JSON-P JAX-RS provider read buffer limit size (see johnzon)")
        private int jsonpMaxReadBufferLen = 64 * 1024;

        @CliOption(name = "jsonp-write-buffer-length", description = "JSON-P JAX-RS provider write buffer limit size (see johnzon)")
        private int jsonpMaxWriteBufferLen = 64 * 1024;

        @CliOption(name = "jsonp-supports-comment", description = "Should JSON-P JAX-RS provider support comments (see johnzon)")
        private boolean jsonpSupportsComment = false;

        @CliOption(name = "jsonp-supports-comment", description = "Should JSON-P JAX-RS provider prettify the outputs (see johnzon)")
        private boolean jsonpPrettify = false;

        @CliOption(name = "jsonb-encoding", description = "Which encoding provider JSON-B should use")
        private String jsonbEncoding = "UTF-8";

        @CliOption(name = "jsonb-nulls", description = "Should JSON-B provider serialize nulls")
        private boolean jsonbNulls = false;

        @CliOption(name = "jsonb-ijson", description = "Should JSON-B provider comply to I-JSON")
        private boolean jsonbIJson = false;

        @CliOption(name = "jsonb-prettify", description = "Should JSON-B provider prettify the output")
        private boolean jsonbPrettify = false;

        @CliOption(name = "jsonb-binary-strategy", description = "Should JSON-B provider prettify the output")
        private String jsonbBinaryStrategy;

        @CliOption(name = "jsonb-naming-strategy", description = "Should JSON-B provider prettify the output")
        private String jsonbNamingStrategy;

        @CliOption(name = "jsonb-order-strategy", description = "Should JSON-B provider prettify the output")
        private String jsonbOrderStrategy;

        @CliOption(name = "logging-global-setup", description = "Should logging be configured to use log4j2 (it is global)")
        private boolean loggingGlobalSetup = true;

        @CliOption(name = "cxf-servlet-params", description = "Init parameters passed to CXF servlet")
        private Map<String, String> cxfServletParams;

        @CliOption(name = "tomcat-scanning", description = "Should Tomcat scanning be used (@HandleTypes, @WebXXX)")
        private boolean tomcatScanning = true;

        @CliOption(name = "tomcat-default-setup", description = "Add default servlet")
        private boolean tomcatAutoSetup = true;

        @CliOption(name = "use-shutdown-hook", description = "Use shutdown hook to automatically stop the container on Ctrl+C")
        private boolean useShutdownHook = true;

        @CliOption(name = "tomcat-filter", description = "A Tomcat JarScanFilter")
        private String tomcatFilter;

        @CliOption(name = "scanning-include", description = "A forced include list of jar names (comma separated values)")
        private String scanningIncludes;

        @CliOption(name = "scanning-exclude", description = "A forced exclude list of jar names (comma separated values)")
        private String scanningExcludes;

        @CliOption(name = "scanning-package-include", description = "A forced include list of packages names (comma separated values)")
        private String scanningPackageIncludes;

        @CliOption(name = "scanning-package-exclude", description = "A forced exclude list of packages names (comma separated values)")
        private String scanningPackageExcludes;

        @CliOption(name = "web-session-timeout", description = "Force the session timeout for webapps")
        private Integer webSessionTimeout;

        @CliOption(name = "web-session-cookie-config", description = "Force the cookie-config, it uses a properties syntax with the keys being the web.xml tag names.")
        private String webSessionCookieConfig;

        @CliOption(name = "tomcat-default", description = "Should Tomcat default be set (session timeout, mime mapping etc...)")
        private boolean useTomcatDefaults = true;

        @CliOption(name = "tomcat-wrap-loader", description = "(Experimental) When deploying a classpath (current classloader), " +
                "should meecrowave wrap the loader to define another loader identity but still use the same classes and resources.")
        private boolean tomcatWrapLoader = false;

        @CliOption(name = "tomcat-skip-jmx", description = "(Experimental) Should Tomcat MBeans be skipped.")
        private boolean tomcatNoJmx = true;

        @CliOption(name = "shared-libraries", description = "A folder containing shared libraries.", alias = "shared-librairies")
        private String sharedLibraries;

        @CliOption(name = "log4j2-jul-bridge", description = "Should JUL logs be redirected to Log4j2 - only works before JUL usage.")
        private boolean useLog4j2JulLogManager = System.getProperty("java.util.logging.manager") == null;

        @CliOption(name = "servlet-container-initializer-injection", description = "Should ServletContainerInitialize support injections.")
        private boolean injectServletContainerInitializer = true;

        @CliOption(
                name = "tomcat-access-log-pattern",
                description = "Activates and configure the access log valve. Value example: '%h %l %u %t \"%r\" %s %b \"%{Referer}i\" \"%{User-Agent}i\"'")
        private String tomcatAccessLogPattern;

        @CliOption(
                name = "meecrowave-properties",
                description = "Loads a meecrowave properties, defaults to meecrowave.properties.")
        private String meecrowaveProperties = "meecrowave.properties";

        @CliOption(name = "jaxws-support-if-present", description = "Should @WebService CDI beans be deployed if cxf-rt-frontend-jaxws is in the classpath.")
        private boolean jaxwsSupportIfAvailable = true;
        
        @CliOption(name = "default-ssl-hostconfig-name", description = "The name of the default SSLHostConfig that will be used for secure https connections.")
        private String defaultSSLHostConfigName;

        @CliOption(name = "cxf-initialize-client-bus", description = "Should the client bus be set. If false the server one will likely be reused.")
        private boolean initializeClientBus = true;

        private final Map<Class<?>, Object> extensions = new HashMap<>();
        private final Collection<Consumer<Tomcat>> instanceCustomizers = new ArrayList<>();

        public Builder() { // load defaults
            extensions.put(ValueTransformers.class, new ValueTransformers());
            StreamSupport.stream(ServiceLoader.load(Meecrowave.ConfigurationCustomizer.class).spliterator(), false)
                    .forEach(c -> c.accept(this));
            loadFrom(meecrowaveProperties);
        }

        public <T> T getExtension(final Class<T> extension) {
            // in the cli we read the values from the cli but in other mode from properties
            // to ensure we can do the same in all modes keeping a nice cli
            return extension.cast(extensions.computeIfAbsent(extension, k -> {
                try {
                    return bind(k.newInstance());
                } catch (final InstantiationException | IllegalAccessException e) {
                    throw new IllegalArgumentException(e);
                }
            }));
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

        public boolean isInitializeClientBus() {
            return initializeClientBus;
        }

        public void setInitializeClientBus(final boolean initializeClientBus) {
            this.initializeClientBus = initializeClientBus;
        }

        public boolean isJaxwsSupportIfAvailable() {
            return jaxwsSupportIfAvailable;
        }

        public void setJaxwsSupportIfAvailable(final boolean jaxwsSupportIfAvailable) {
            this.jaxwsSupportIfAvailable = jaxwsSupportIfAvailable;
        }

        public int getWatcherBouncing() {
            return watcherBouncing;
        }

        public void setWatcherBouncing(final int watcherBouncing) {
            this.watcherBouncing = watcherBouncing;
        }

        public String getTomcatAccessLogPattern() {
            return tomcatAccessLogPattern;
        }

        public void setTomcatAccessLogPattern(final String tomcatAccessLogPattern) {
            this.tomcatAccessLogPattern = tomcatAccessLogPattern;
        }

        public boolean isTomcatNoJmx() {
            return tomcatNoJmx;
        }

        public void setTomcatNoJmx(final boolean tomcatNoJmx) {
            this.tomcatNoJmx = tomcatNoJmx;
        }

        public File getPidFile() {
            return pidFile;
        }

        public void setPidFile(final File pidFile) {
            this.pidFile = pidFile;
        }

        public String getScanningPackageIncludes() {
            return scanningPackageIncludes;
        }

        /**
         * Define some package names (startsWith) which must get scanned for beans.
         * This rule get's applied before {@link #setScanningPackageExcludes(String)}
         */
        public void setScanningPackageIncludes(final String scanningPackageIncludes) {
            this.scanningPackageIncludes = scanningPackageIncludes;
        }

        public String getScanningPackageExcludes() {
            return scanningPackageExcludes;
        }

        /**
         * Define some package names (startsWith) which must <em>NOT</em> get scanned for beans.
         * This rule get's applied after {@link #setScanningPackageIncludes(String)}.
         *
         * Defining just a '*' will be a marker for skipping all not-included packages.
         * Otherwise we will defer to the standard OpenWebBeans class Filter mechanism.
         */
        public void setScanningPackageExcludes(final String scanningPackageExcludes) {
            this.scanningPackageExcludes = scanningPackageExcludes;
        }

        public Builder excludePackages(final String packages) {
            this.setScanningPackageExcludes(packages);
            return this;
        }

        /**
         * Only scan the very packages given (startsWith).
         * This will exclude <em>all</em> other packages from bean scanning
         */
        public Builder includePackages(final String packages) {
            this.setScanningPackageIncludes(packages);
            this.setScanningPackageExcludes("*");
            return this;
        }

        /**
         * Scan the very packages given (startsWith) <em>in addition</em> to the default rules.
         */
        public Builder withPackages(final String packages) {
            this.setScanningPackageIncludes(packages);
            return this;
        }

        public void setExtension(final Class<?> type, final Object value) {
            extensions.put(type, value);
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

        public String getSharedLibraries() {
            return sharedLibraries;
        }

        public Builder sharedLibraries(final String sharedLibraries) {
            setSharedLibraries(sharedLibraries);
            return this;
        }

        public void setSharedLibraries(final String sharedLibraries) {
            this.sharedLibraries = sharedLibraries;
        }

        public boolean isJaxrsLogProviders() {
            return jaxrsLogProviders;
        }

        public void setJaxrsLogProviders(final boolean jaxrsLogProviders) {
            this.jaxrsLogProviders = jaxrsLogProviders;
        }

        public boolean isUseTomcatDefaults() {
            return useTomcatDefaults;
        }

        public void setUseTomcatDefaults(final boolean useTomcatDefaults) {
            this.useTomcatDefaults = useTomcatDefaults;
        }

        public String getTomcatFilter() {
            return tomcatFilter;
        }

        public void setTomcatFilter(final String tomcatFilter) {
            this.tomcatFilter = tomcatFilter;
        }

        public boolean isTomcatScanning() {
            return tomcatScanning;
        }

        public void setTomcatScanning(final boolean tomcatScanning) {
            this.tomcatScanning = tomcatScanning;
        }

        public Map<String, String> getCxfServletParams() {
            return cxfServletParams;
        }

        public void setCxfServletParams(final Map<String, String> cxfServletParams) {
            this.cxfServletParams = cxfServletParams;
        }

        public boolean isLoggingGlobalSetup() {
            return loggingGlobalSetup;
        }

        public void setLoggingGlobalSetup(final boolean loggingGlobalSetup) {
            this.loggingGlobalSetup = loggingGlobalSetup;
        }

        public boolean isJaxrsAutoActivateBeanValidation() {
            return jaxrsAutoActivateBeanValidation;
        }

        public void setJaxrsAutoActivateBeanValidation(final boolean jaxrsAutoActivateBeanValidation) {
            this.jaxrsAutoActivateBeanValidation = jaxrsAutoActivateBeanValidation;
        }

        public boolean isJaxrsProviderSetup() {
            return jaxrsProviderSetup;
        }

        public void setJaxrsProviderSetup(final boolean jaxrsProviderSetup) {
            this.jaxrsProviderSetup = jaxrsProviderSetup;
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

        public Properties getProperties() {
            return properties;
        }

        public void setProperties(final Properties properties) {
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

        public LoginConfigBuilder getLoginConfig() {
            return loginConfig;
        }

        public Builder loginConfig(final LoginConfigBuilder loginConfig) {
            setLoginConfig(loginConfig);
            return this;
        }

        public void setLoginConfig(final LoginConfigBuilder loginConfig) {
            this.loginConfig = loginConfig;
        }

        public Collection<SecurityConstaintBuilder> getSecurityConstraints() {
            return securityConstraints;
        }

        public Builder securityConstraints(final SecurityConstaintBuilder securityConstraint) {
            securityConstraints = securityConstraints == null ? new ArrayList<>() : securityConstraints;
            securityConstraints.add(securityConstraint);
            return this;
        }

        public void setSecurityConstraints(final Collection<SecurityConstaintBuilder> securityConstraints) {
            this.securityConstraints = securityConstraints;
        }

        public Realm getRealm() {
            return realm;
        }

        public Builder realm(final Realm realm) {
            setRealm(realm);
            return this;
        }

        public void setRealm(final Realm realm) {
            this.realm = realm;
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

        public boolean isHttp2() {
            return http2;
        }

        public void setHttp2(final boolean http2) {
            this.http2 = http2;
        }

        public Collection<Connector> getConnectors() {
            return connectors;
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

        public boolean isCdiConversation() {
            return cdiConversation;
        }

        public void setCdiConversation(final boolean cdiConversation) {
            this.cdiConversation = cdiConversation;
        }

        public Builder randomHttpPort() {
            try (final ServerSocket serverSocket = new ServerSocket(0)) {
                this.httpPort = serverSocket.getLocalPort();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return this;
        }
        
        public Builder randomHttpsPort() {
            try (final ServerSocket serverSocket = new ServerSocket(0)) {
                this.httpsPort = serverSocket.getLocalPort();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return this;
        }

        public Builder loadFrom(final String resource) {
            try (final InputStream is = findStream(resource)) {
                if (is != null) {
                    final Properties config = new Properties() {{
                        load(is);
                    }};
                    loadFromProperties(config);
                }
                return this;
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public void setServerXml(final String file) {
            if (file == null) {
                serverXml = null;
            } else {
                final File sXml = new File(file);
                if (sXml.exists()) {
                    serverXml = sXml;
                }
            }
        }

        public Builder property(final String key, final String value) {
            properties.setProperty(key, value);
            return this;
        }

        private InputStream findStream(final String resource) throws FileNotFoundException {
            InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
            if (stream == null) {
                final File file = new File(resource);
                if (file.exists()) {
                    return new FileInputStream(file);
                }
            }
            return stream;
        }

        public Builder user(final String name, final String pwd) {
            if (users == null) {
                users = new HashMap<>();
            }
            this.users.put(name, pwd);
            return this;
        }

        public Builder role(final String user, final String roles) {
            if (this.roles == null) {
                this.roles = new HashMap<>();
            }
            this.roles.put(user, roles);
            return this;
        }

        public Builder cxfServletParam(final String key, final String value) {
            if (this.cxfServletParams == null) {
                this.cxfServletParams = new HashMap<>();
            }
            this.cxfServletParams.put(key, value);
            return this;
        }

        public String getActiveProtocol() {
            return isSkipHttp() ? "https" : "http";
        }

        public int getActivePort() {
            return isSkipHttp() ? getHttpsPort() : getHttpPort();
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

        public Builder noShutdownHook() {
            setUseShutdownHook(false);
            return this;
        }

        public boolean isTomcatWrapLoader() {
            return tomcatWrapLoader;
        }

        public void setTomcatWrapLoader(final boolean tomcatWrapLoader) {
            this.tomcatWrapLoader = tomcatWrapLoader;
        }

        public void addInstanceCustomizer(final Consumer<Tomcat> customizer) {
            instanceCustomizers.add(customizer);
        }

        public Builder instanceCustomizer(final Consumer<Tomcat> customizer) {
            addInstanceCustomizer(customizer);
            return this;
        }

        public void addCustomizer(final Consumer<Builder> configurationCustomizer) {
            configurationCustomizer.accept(this);
        }

        public String getJaxrsDefaultProviders() {
            return jaxrsDefaultProviders;
        }

        public void setJaxrsDefaultProviders(final String jaxrsDefaultProviders) {
            this.jaxrsDefaultProviders = jaxrsDefaultProviders;
        }

        public boolean isUseLog4j2JulLogManager() {
            return useLog4j2JulLogManager;
        }

        public void setUseLog4j2JulLogManager(final boolean useLog4j2JulLogManager) {
            this.useLog4j2JulLogManager = useLog4j2JulLogManager;
        }

        public String getDefaultSSLHostConfigName() {
            return defaultSSLHostConfigName;
        }

        public void setDefaultSSLHostConfigName(final String defaultSSLHostConfigName) {
            this.defaultSSLHostConfigName = defaultSSLHostConfigName;
        }

        public void loadFromProperties(final Properties config) {
            // filtering properties with system properties or themself
            final StrSubstitutor strSubstitutor = new StrSubstitutor(new StrLookup<String>() {
                @Override
                public String lookup(final String key) {
                    final String property = System.getProperty(key);
                    return property == null ? config.getProperty(key) : null;
                }
            });

            final ValueTransformers transformers = getExtension(ValueTransformers.class);
            for (final String key : config.stringPropertyNames()) {
                final String val = config.getProperty(key);
                if (val == null || val.trim().isEmpty()) {
                    continue;
                }
                final String newVal = transformers.apply(strSubstitutor.replace(config.getProperty(key)));
                if (!val.equals(newVal)) {
                    config.setProperty(key, newVal);
                }
            }

            for (final Field field : Builder.class.getDeclaredFields()) {
                final CliOption annotation = field.getAnnotation(CliOption.class);
                if (annotation == null) {
                    continue;
                }
                final String name = field.getName();
                Stream.of(Stream.of(annotation.name()), Stream.of(annotation.alias()))
                        .flatMap(a -> a)
                        .map(config::getProperty)
                        .filter(Objects::nonNull)
                        .findAny().ifPresent(val -> {
                    final Object toSet;
                    if (field.getType() == String.class) {
                        toSet = val;
                    } else if (field.getType() == int.class) {
                        if ("httpPort".equals(name) && "-1".equals(val)) { // special case in case of random port
                            randomHttpPort();
                            toSet = null;
                        } else {
                            toSet = Integer.parseInt(val);
                        }
                    } else if (field.getType() == boolean.class) {
                        toSet = Boolean.parseBoolean(val);
                    } else if (field.getType() == File.class) {
                        toSet = new File(val);
                    } else {
                        toSet = null;
                    }
                    if (toSet == null) { // handled elsewhere
                        return;
                    }

                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    try {
                        field.set(this, toSet);
                    } catch (final IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                });
            }

            // not trivial types
            for (final String prop : config.stringPropertyNames()) {
                if (prop.startsWith("properties.")) {
                    property(prop.substring("properties.".length()), config.getProperty(prop));
                } else if (prop.startsWith("users.")) {
                    user(prop.substring("users.".length()), config.getProperty(prop));
                } else if (prop.startsWith("roles.")) {
                    role(prop.substring("roles.".length()), config.getProperty(prop));
                } else if (prop.startsWith("cxf.servlet.params.")) {
                    cxfServletParam(prop.substring("cxf.servlet.params.".length()), config.getProperty(prop));
                } else if (prop.startsWith("connector.")) { // created in container
                    property(prop, config.getProperty(prop));
                } else if (prop.equals("realm")) {
                    final ObjectRecipe recipe = newRecipe(config.getProperty(prop));
                    for (final String realmConfig : config.stringPropertyNames()) {
                        if (realmConfig.startsWith("realm.")) {
                            recipe.setProperty(realmConfig.substring("realm.".length()), config.getProperty(realmConfig));
                        }
                    }
                    this.realm = Realm.class.cast(recipe.create());
                } else if (prop.equals("login")) {
                    final ObjectRecipe recipe = newRecipe(LoginConfigBuilder.class.getName());
                    for (final String nestedConfig : config.stringPropertyNames()) {
                        if (nestedConfig.startsWith("login.")) {
                            recipe.setProperty(nestedConfig.substring("login.".length()), config.getProperty(nestedConfig));
                        }
                    }
                    loginConfig = LoginConfigBuilder.class.cast(recipe.create());
                } else if (prop.equals("securityConstraint")) {
                    final ObjectRecipe recipe = newRecipe(SecurityConstaintBuilder.class.getName());
                    for (final String nestedConfig : config.stringPropertyNames()) {
                        if (nestedConfig.startsWith("securityConstraint.")) {
                            recipe.setProperty(nestedConfig.substring("securityConstraint.".length()), config.getProperty(nestedConfig));
                        }
                    }
                    securityConstraints.add(SecurityConstaintBuilder.class.cast(recipe.create()));
                } else if (prop.equals("configurationCustomizer")) {
                    final ObjectRecipe recipe = newRecipe(prop);
                    for (final String nestedConfig : config.stringPropertyNames()) {
                        if (nestedConfig.startsWith(prop + '.')) {
                            recipe.setProperty(nestedConfig.substring(prop.length() + 2 /*dot*/), config.getProperty(nestedConfig));
                        }
                    }
                    addCustomizer(Consumer.class.cast(recipe.create()));
                }
            }
        }

        public <T> T bind(final T instance) {
            final ValueTransformers transformers = getExtension(ValueTransformers.class);
            Class<? extends Object> type = instance.getClass();
            do {
                Stream.of(type.getDeclaredFields())
                        .filter(f -> f.isAnnotationPresent(CliOption.class))
                        .forEach(f -> {
                            final CliOption annotation = f.getAnnotation(CliOption.class);
                            String value = properties.getProperty(annotation.name());
                            if (value == null) {
                                value = Stream.of(annotation.alias()).map(properties::getProperty).findFirst().orElse(null);
                                if (value == null) {
                                    return;
                                }
                            }

                            value = transformers.apply(value);

                            if (!f.isAccessible()) {
                                f.setAccessible(true);
                            }
                            final Class<?> t = f.getType();
                            try {
                                if (t == String.class) {
                                    f.set(instance, value);
                                } else if (t == int.class) {
                                    f.set(instance, Integer.parseInt(value));
                                } else if (t == boolean.class) {
                                    f.set(instance, Boolean.parseBoolean(value));
                                } else {
                                    throw new IllegalArgumentException("Unsupported type " + t);
                                }
                            } catch (final IllegalAccessException iae) {
                                throw new IllegalStateException(iae);
                            }
                        });
                type = type.getSuperclass();
            } while (type != Object.class);
            return instance;
        }

        public boolean isInjectServletContainerInitializer() {
            return injectServletContainerInitializer;
        }

        public void setInjectServletContainerInitializer(final boolean injectServletContainerInitializer) {
            this.injectServletContainerInitializer = injectServletContainerInitializer;
        }

        public String getMeecrowaveProperties() {
            return meecrowaveProperties;
        }

        public void setMeecrowaveProperties(final String meecrowaveProperties) {
            this.meecrowaveProperties = meecrowaveProperties;
        }
    }

    public static class ValueTransformers implements Function<String, String> {
        private final Map<String, ValueTransformer> transformers = new HashMap<>();

        @Override
        public String apply(final String value) {
            if (value.startsWith("decode:")) {
                if (transformers.isEmpty()) { // lazy loading
                    transformers.put("Static3DES", new ValueTransformer() { // compatibility with tomee
                        private final SecretKeySpec key = new SecretKeySpec(new byte[]{
                                (byte) 0x76, (byte) 0x6F, (byte) 0xBA, (byte) 0x39, (byte) 0x31,
                                (byte) 0x2F, (byte) 0x0D, (byte) 0x4A, (byte) 0xA3, (byte) 0x90,
                                (byte) 0x55, (byte) 0xFE, (byte) 0x55, (byte) 0x65, (byte) 0x61,
                                (byte) 0x13, (byte) 0x34, (byte) 0x82, (byte) 0x12, (byte) 0x17,
                                (byte) 0xAC, (byte) 0x77, (byte) 0x39, (byte) 0x19}, "DESede");

                        @Override
                        public String name() {
                            return "Static3DES";
                        }

                        @Override
                        public String apply(final String encodedPassword) {
                            Objects.requireNonNull(encodedPassword, "value can't be null");
                            try {
                                final byte[] cipherText = Base64.getDecoder().decode(encodedPassword);
                                final Cipher cipher = Cipher.getInstance("DESede");
                                cipher.init(Cipher.DECRYPT_MODE, key);
                                return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
                            } catch (final Exception e) {
                                throw new IllegalArgumentException(e);
                            }
                        }
                    });
                    for (final ValueTransformer t : ServiceLoader.load(ValueTransformer.class)) {
                        transformers.put(t.name(), t);
                    }
                }

                final String substring = value.substring("decode:".length());
                final int sep = substring.indexOf(':');
                if (sep < 0) {
                    throw new IllegalArgumentException("No transformer algorithm for " + value);
                }
                final String algo = substring.substring(0, sep);
                return Objects.requireNonNull(transformers.get(algo), "No ValueTransformer for value '" + value + "'").apply(substring.substring(sep + 1));
            }
            return value;
        }
    }

    public static class LoginConfigBuilder {
        private final LoginConfig loginConfig = new LoginConfig();

        public void setErrorPage(final String errorPage) {
            loginConfig.setErrorPage(errorPage);
        }

        public void setLoginPage(final String loginPage) {
            loginConfig.setLoginPage(loginPage);
        }

        public void setRealmName(final String realmName) {
            loginConfig.setRealmName(realmName);
        }

        public void setAuthMethod(final String authMethod) {
            loginConfig.setAuthMethod(authMethod);
        }

        public LoginConfigBuilder errorPage(final String errorPage) {
            loginConfig.setErrorPage(errorPage);
            return this;
        }

        public LoginConfigBuilder loginPage(final String loginPage) {
            loginConfig.setLoginPage(loginPage);
            return this;
        }

        public LoginConfigBuilder realmName(final String realmName) {
            loginConfig.setRealmName(realmName);
            return this;
        }

        public LoginConfigBuilder authMethod(final String authMethod) {
            loginConfig.setAuthMethod(authMethod);
            return this;
        }

        public LoginConfig build() {
            return loginConfig;
        }

        public LoginConfigBuilder basic() {
            return authMethod("BASIC");
        }

        public LoginConfigBuilder digest() {
            return authMethod("DIGEST");
        }

        public LoginConfigBuilder clientCert() {
            return authMethod("CLIENT-CERT");
        }

        public LoginConfigBuilder form() {
            return authMethod("FORM");
        }
    }

    public static class SecurityConstaintBuilder {
        private final SecurityConstraint securityConstraint = new SecurityConstraint();

        public SecurityConstaintBuilder authConstraint(final boolean authConstraint) {
            securityConstraint.setAuthConstraint(authConstraint);
            return this;
        }

        public SecurityConstaintBuilder displayName(final String displayName) {
            securityConstraint.setDisplayName(displayName);
            return this;
        }

        public SecurityConstaintBuilder userConstraint(final String constraint) {
            securityConstraint.setUserConstraint(constraint);
            return this;
        }

        public SecurityConstaintBuilder addAuthRole(final String authRole) {
            securityConstraint.addAuthRole(authRole);
            return this;
        }

        public SecurityConstaintBuilder addCollection(final String name, final String pattern, final String... methods) {
            final SecurityCollection collection = new SecurityCollection();
            collection.setName(name);
            collection.addPattern(pattern);
            for (final String httpMethod : methods) {
                collection.addMethod(httpMethod);
            }
            securityConstraint.addCollection(collection);
            return this;
        }

        public void setAuthConstraint(final boolean authConstraint) {
            securityConstraint.setAuthConstraint(authConstraint);
        }

        public void setDisplayName(final String displayName) {
            securityConstraint.setDisplayName(displayName);
        }

        public void setUserConstraint(final String userConstraint) {
            securityConstraint.setUserConstraint(userConstraint);
        }

        public void setAuthRole(final String authRole) { // easier for config
            addAuthRole(authRole);
        }

        // name:pattern:method1/method2
        public void setCollection(final String value) { // for config
            final String[] split = value.split(":");
            if (split.length != 3 && split.length != 2) {
                throw new IllegalArgumentException("Can't parse " + value + ", syntax is: name:pattern:method1/method2");
            }
            addCollection(split[0], split[1], split.length == 2 ? new String[0] : split[2].split("/"));
        }

        public SecurityConstraint build() {
            return securityConstraint;
        }
    }

    private static class InternalTomcat extends Tomcat {
        private Connector connector;

        private void server(final Server s) {
            server = s;
            connector = server != null && server.findServices().length > 0 && server.findServices()[0].findConnectors().length > 0 ?
                    server.findServices()[0].findConnectors()[0] : null;
        }

        Connector getRawConnector() {
            return connector;
        }
    }

    private static class TomcatWithFastSessionIDs extends InternalTomcat {
        @Override
        public void start() throws LifecycleException {
            // Use fast, insecure session ID generation for all tests
            final Server server = getServer();
            for (final Service service : server.findServices()) {
                final org.apache.catalina.Container e = service.getContainer();
                for (final org.apache.catalina.Container h : e.findChildren()) {
                    for (final org.apache.catalina.Container c : h.findChildren()) {
                        Manager m = ((org.apache.catalina.Context) c).getManager();
                        if (m == null) {
                            m = new StandardManager();
                            org.apache.catalina.Context.class.cast(c).setManager(m);
                        }
                        if (m instanceof ManagerBase) {
                            ManagerBase.class.cast(m).setSecureRandomClass(
                                    "org.apache.catalina.startup.FastNonSecureRandom");
                        }
                    }
                }
            }
            super.start();
        }
    }

    private static class QuickServerXmlParser extends DefaultHandler {
        private static final SAXParserFactory FACTORY = SAXParserFactory.newInstance();

        static {
            FACTORY.setNamespaceAware(true);
            FACTORY.setValidating(false);
        }

        private static final String STOP_KEY = "STOP";
        private static final String HTTP_KEY = "HTTP";
        private static final String SECURED_SUFFIX = "S";
        private static final String HOST_KEY = "host";
        private static final String DEFAULT_CONNECTOR_KEY = HTTP_KEY;

        private static final String DEFAULT_HTTP_PORT = "8080";
        private static final String DEFAULT_HTTPS_PORT = "8443";
        private static final String DEFAULT_STOP_PORT = "8005";
        private static final String DEFAULT_HOST = "localhost";

        private final Map<String, String> values = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);

        private QuickServerXmlParser(final boolean useDefaults) {
            if (useDefaults) {
                values.put(STOP_KEY, DEFAULT_STOP_PORT);
                values.put(HTTP_KEY, DEFAULT_HTTP_PORT);
                values.put(HOST_KEY, DEFAULT_HOST);
            }
        }

        @Override
        public void startElement(final String uri, final String localName,
                                 final String qName, final Attributes attributes) throws SAXException {
            if ("Server".equalsIgnoreCase(localName)) {
                final String port = attributes.getValue("port");
                if (port != null) {
                    values.put(STOP_KEY, port);
                } else {
                    values.put(STOP_KEY, DEFAULT_STOP_PORT);
                }
            } else if ("Connector".equalsIgnoreCase(localName)) {
                String protocol = attributes.getValue("protocol");
                if (protocol == null) {
                    protocol = DEFAULT_CONNECTOR_KEY;
                } else if (protocol.contains("/")) {
                    protocol = protocol.substring(0, protocol.indexOf("/"));
                }
                final String port = attributes.getValue("port");
                final String ssl = attributes.getValue("secure");

                if (ssl == null || "false".equalsIgnoreCase(ssl)) {
                    values.put(protocol.toUpperCase(), port);
                } else {
                    values.put(protocol.toUpperCase() + SECURED_SUFFIX, port);
                }
            } else if ("Host".equalsIgnoreCase(localName)) {
                final String host = attributes.getValue("name");
                if (host != null) {
                    values.put(HOST_KEY, host);
                }
            }
        }

        private static QuickServerXmlParser parse(final File serverXml) {
            return parse(serverXml, true);
        }

        private static QuickServerXmlParser parse(final File serverXml, final boolean defaults) {
            final QuickServerXmlParser handler = new QuickServerXmlParser(defaults);
            try {
                final SAXParser parser = FACTORY.newSAXParser();
                parser.parse(serverXml, handler);
            } catch (final Exception e) {
                // no-op: using defaults
            }
            return handler;
        }

        public String http() {
            return value(HTTP_KEY, DEFAULT_HTTP_PORT);
        }

        private String https() { // enough common to be exposed as method
            return securedValue(HTTP_KEY, DEFAULT_HTTPS_PORT);
        }

        private String stop() {
            return value(STOP_KEY, DEFAULT_STOP_PORT);
        }

        private String value(final String key, final String defaultValue) {
            final String val = values.get(key);
            if (val == null) {
                return defaultValue;
            }
            return val;
        }

        private String securedValue(final String key, final String defaultValue) {
            return value(key + SECURED_SUFFIX, defaultValue);
        }
    }

    // there to be able to stack config later on without breaking all methods
    public static class DeploymentMeta {
        private final String context;
        private final File docBase;
        private final Consumer<Context> consumer;

        public DeploymentMeta(final String context, final File docBase, final Consumer<Context> consumer) {
            this.context = context;
            this.docBase = docBase;
            this.consumer = consumer;
        }
    }

    // just to type it and allow some extensions to use a ServiceLoader
    public interface ConfigurationCustomizer extends Consumer<Meecrowave.Builder> {
    }

    public interface InstanceCustomizer extends Consumer<Tomcat> {
    }

    private static final class MeecrowaveContainerLoader extends URLClassLoader {
        private MeecrowaveContainerLoader(final URL[] urls, final ClassLoader parent) {
            super(urls, parent);
        }
    }
}
