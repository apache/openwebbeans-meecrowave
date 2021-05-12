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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
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
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.meecrowave.api.StartListening;
import org.apache.meecrowave.api.StopListening;
import org.apache.meecrowave.configuration.Configuration;
import org.apache.meecrowave.cxf.ConfigurableBus;
import org.apache.meecrowave.cxf.CxfCdiAutoSetup;
import org.apache.meecrowave.cxf.Cxfs;
import org.apache.meecrowave.io.IO;
import org.apache.meecrowave.lang.Substitutor;
import org.apache.meecrowave.logging.jul.Log4j2Logger;
import org.apache.meecrowave.logging.log4j2.Log4j2Shutdown;
import org.apache.meecrowave.logging.log4j2.Log4j2s;
import org.apache.meecrowave.logging.openwebbeans.Log4j2LoggerFactory;
import org.apache.meecrowave.logging.tomcat.Log4j2Log;
import org.apache.meecrowave.logging.tomcat.LogFacade;
import org.apache.meecrowave.openwebbeans.OWBAutoSetup;
import org.apache.meecrowave.service.Priotities;
import org.apache.meecrowave.service.ValueTransformer;
import org.apache.meecrowave.tomcat.CDIInstanceManager;
import org.apache.meecrowave.tomcat.LoggingAccessLogPattern;
import org.apache.meecrowave.tomcat.MeecrowaveContextConfig;
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
    private final Configuration configuration;
    protected ConfigurableBus clientBus;
    protected File base;
    protected final File ownedTempDir;
    protected File workDir;
    protected InternalTomcat tomcat;
    protected volatile Thread hook;

    // we can undeploy webapps with that later
    private final Map<String, Runnable> contexts = new HashMap<>();
    private Runnable postTask;
    private boolean clearCatalinaSystemProperties;
    private boolean deleteBase;

    public Meecrowave() {
        this(new Builder());
    }

    public Meecrowave(final Configuration configuration) {
        this.configuration = configuration;
        this.ownedTempDir = new File(this.configuration.getTempDir(), "meecrowave_" + System.nanoTime());
    }

    public Builder getConfiguration() {
        return Builder.class.isInstance(configuration) ?
                Builder.class.cast(configuration) :
                new Builder(configuration);
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
        }).orElse(builtInCustomizer), meta.redeployCallback));
    }

    // shortcut
    public Meecrowave deployClasspath() {
        return deployClasspath("");
    }

    // shortcut
    public Meecrowave bake(final Consumer<Context> customizer) {
        start();
        return deployClasspath(new DeploymentMeta("", null, customizer, null));
    }

    // shortcut (used by plugins)
    public Meecrowave deployClasspath(final String context) {
        return deployClasspath(new DeploymentMeta(context, null, null, null));
    }

    // shortcut
    public Meecrowave deployWebapp(final File warOrDir) {
        return deployWebapp("", warOrDir);
    }

    // shortcut (used by plugins)
    public Meecrowave deployWebapp(final String context, final File warOrDir) {
        return deployWebapp(new DeploymentMeta(context, warOrDir, null, null));
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
        ofNullable(configuration.getTomcatFilter()).ifPresent(filter -> {
            try {
                scanner.setJarScanFilter(JarScanFilter.class.cast(Thread.currentThread().getContextClassLoader().loadClass(filter).newInstance()));
            } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException(e);
            }
        });

        final AtomicReference<Runnable> releaseSCI = new AtomicReference<>();
        final ServletContainerInitializer meecrowaveInitializer = (c, ctx1) -> {
            ctx1.setAttribute("meecrowave.configuration", getConfiguration());
            ctx1.setAttribute("meecrowave.instance", Meecrowave.this);

            new OWBAutoSetup().onStartup(c, ctx1);
            if (Cxfs.IS_PRESENT) {
                new CxfCdiAutoSetup().onStartup(c, ctx1);
            }
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

        ctx.addLifecycleListener(new MeecrowaveContextConfig(configuration, meta.docBase != null, meecrowaveInitializer, meta.redeployCallback));
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
                    ctx.getResources().setCachingAllowed(configuration.isWebResourceCached());
                    break;
                case Lifecycle.BEFORE_INIT_EVENT:
                    if (configuration.getLoginConfig() != null) {
                        ctx.setLoginConfig(configuration.getLoginConfig().build());
                    }
                    for (final SecurityConstaintBuilder sc : configuration.getSecurityConstraints()) {
                        ctx.addConstraint(sc.build());
                    }
                    if (configuration.getWebXml() != null) {
                        ctx.getServletContext().setAttribute(Globals.ALT_DD_ATTR, configuration.getWebXml());
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
            Tomcat.addDefaultMimeTypeMappings(ctx);
        } else if (configuration.getWebSessionTimeout() != null) {
            ctx.setSessionTimeout(configuration.getWebSessionTimeout());
        }

        ofNullable(meta.consumer).ifPresent(c -> c.accept(ctx));
        if (configuration.isQuickSession() && ctx.getManager() == null) {
            final StandardManager manager = new StandardManager();
            manager.setSessionIdGenerator(new StandardSessionIdGenerator() {
                @Override
                protected void getRandomBytes(final byte bytes[]) {
                    ThreadLocalRandom.current().nextBytes(bytes);
                }

                @Override
                public String toString() {
                    return "MeecrowaveSessionIdGenerator@" + System.identityHashCode(this);
                }
            });
            ctx.setManager(manager);
        }
        if (configuration.isAntiResourceLocking() && StandardContext.class.isInstance(ctx)) {
            StandardContext.class.cast(ctx).setAntiResourceLocking(true);
        }
        configuration.getInitializers().forEach(i -> ctx.addServletContainerInitializer(i, emptySet()));
        configuration.getGlobalContextConfigurers().forEach(it -> it.accept(ctx));

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

        if (configuration.isUseLog4j2JulLogManager() && Log4j2s.IS_PRESENT) { // /!\ don't move this line or add anything before without checking log setup
            System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        }

        if (configuration.isLoggingGlobalSetup() && Log4j2s.IS_PRESENT) {

            setSystemProperty(systemPropsToRestore, "log4j.shutdownHookEnabled", "false");
            setSystemProperty(systemPropsToRestore, "openwebbeans.logging.factory", Log4j2LoggerFactory.class.getName());
            setSystemProperty(systemPropsToRestore, "org.apache.cxf.Logger", Log4j2Logger.class.getName());
            setSystemProperty(systemPropsToRestore, "org.apache.tomcat.Logger", Log4j2Log.class.getName());

            postTask = () -> {
                if (Log4j2s.IS_PRESENT) {
                    new Log4j2Shutdown().shutdown();
                }
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

        tomcat = new InternalTomcat();

        { // setup
            base = new File(newBaseDir());

            // create the temp dir folder.
            File tempDir;
            if (configuration.getTempDir() == null || configuration.getTempDir().length() == 0) {
                tempDir = createDirectory(base, "temp");
            } else {
                tempDir = new File(configuration.getTempDir());
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }
            }

            try {
                workDir = createDirectory(base, "work");
            } catch (final IllegalStateException ise) {
                // in case we could not create that directory we create it in the temp dir folder
                workDir = createDirectory(tempDir, "work");
            }

            synchronize(new File(base, "conf"), configuration.getConf());
        }

        final Properties props = configuration.getProperties();
        Substitutor substitutor = null;
        for (final String s : props.stringPropertyNames()) {
            final String v = props.getProperty(s);
            if (v != null && v.contains("${")) {
                if (substitutor == null) {
                    final Map<String, String> placeHolders = new HashMap<>();
                    placeHolders.put("meecrowave.embedded.http", Integer.toString(configuration.getHttpPort()));
                    placeHolders.put("meecrowave.embedded.https", Integer.toString(configuration.getHttpsPort()));
                    placeHolders.put("meecrowave.embedded.stop", Integer.toString(configuration.getStopPort()));
                    substitutor = new Substitutor(placeHolders);
                }
                props.put(s, substitutor.replace(v));
            }
        }

        final File conf = new File(base, "conf");

        tomcat.setBaseDir(base.getAbsolutePath());
        tomcat.setHostname(configuration.getHost());

        final boolean initialized;
        if (configuration.getServerXml() != null) {
            final File file = new File(conf, "server.xml");
            if (!file.equals(configuration.getServerXml())) {
                try (final InputStream is = new FileInputStream(configuration.getServerXml());
                     final FileOutputStream fos = new FileOutputStream(file)) {
                    IO.copy(is, fos);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }

            // respect config (host/port) of the Configuration
            final QuickServerXmlParser ports = QuickServerXmlParser.parse(file);
            if (configuration.isKeepServerXmlAsThis()) {
                configuration.setHttpPort(Integer.parseInt(ports.http()));
                configuration.setStopPort(Integer.parseInt(ports.stop()));
            } else {
                final Map<String, String> replacements = new HashMap<>();
                replacements.put(ports.http(), String.valueOf(configuration.getHttpPort()));
                replacements.put(ports.https(), String.valueOf(configuration.getHttpsPort()));
                replacements.put(ports.stop(), String.valueOf(configuration.getStopPort()));

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
            tomcat.getServer().setPort(configuration.getStopPort());
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
            tomcat.setHostname(configuration.getHost());
            tomcat.getEngine().setDefaultHost(configuration.getHost());
            final StandardHost host = new StandardHost();
            host.setName(configuration.getHost());

            try {
                final File webapps = createDirectory(base, "webapps");
                host.setAppBase(webapps.getAbsolutePath());
            } catch (final IllegalStateException ise) {
                // never an issue since the webapps are deployed being put in webapps - so no dynamic folder
                // or through their path - so don't need webapps folder
            }

            host.setUnpackWARs(true); // forced for now cause OWB doesn't support war:file:// urls
            try {
                host.setWorkDir(workDir.getCanonicalPath());
            } catch (final IOException e) {
                host.setWorkDir(workDir.getAbsolutePath());
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

        if (configuration.getRealm() != null) {
            tomcat.getEngine().setRealm(configuration.getRealm());
        }

        if (tomcat.getRawConnector() == null && !configuration.isSkipHttp()) {
            final Connector connector = createConnector();
            connector.setPort(configuration.getHttpPort());
            if (connector.getProperty("connectionTimeout") == null) {
                connector.setProperty("connectionTimeout", "3000");
            }

            tomcat.getService().addConnector(connector);
            tomcat.setConnector(connector);
        }

        // create https connector
        if (configuration.isSsl()) {
            final Connector httpsConnector = createConnector();
            httpsConnector.setPort(configuration.getHttpsPort());
            httpsConnector.setSecure(true);
            httpsConnector.setScheme("https");
            httpsConnector.setProperty("SSLEnabled", "true");
            if (configuration.getSslProtocol() != null) {
                configuration.getProperties().setProperty("connector.sslhostconfig.sslProtocol", configuration.getSslProtocol());
            }
            if (configuration.getProperties().getProperty("connector.sslhostconfig.hostName") != null) {
                httpsConnector.setProperty("defaultSSLHostConfigName", configuration.getProperties().getProperty("connector.sslhostconfig.hostName"));
            }
            if (configuration.getKeystoreFile() != null) {
                configuration.getProperties().setProperty("connector.sslhostconfig.certificateKeystoreFile", configuration.getKeystoreFile());
            }
            if (configuration.getKeystorePass() != null) {
                configuration.getProperties().setProperty("connector.sslhostconfig.certificateKeystorePassword", configuration.getKeystorePass());
            }
            configuration.getProperties().setProperty("connector.sslhostconfig.certificateKeystoreType", configuration.getKeystoreType());
            if (configuration.getClientAuth() != null) {
                httpsConnector.setProperty("clientAuth", configuration.getClientAuth());
            }

            if (configuration.getKeyAlias() != null) {
                configuration.getProperties().setProperty("connector.sslhostconfig.certificateKeyAlias", configuration.getKeyAlias());
            }
            if (configuration.isHttp2()) {
                httpsConnector.addUpgradeProtocol(new Http2Protocol());
            }
            final List<SSLHostConfig> buildSslHostConfig = buildSslHostConfig();
            if (!buildSslHostConfig.isEmpty()) {
                createDirectory(base, "conf");
            }
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

            if (configuration.getDefaultSSLHostConfigName() != null) {
                httpsConnector.setProperty("defaultSSLHostConfigName", configuration.getDefaultSSLHostConfigName());
            }
            tomcat.getService().addConnector(httpsConnector);
            if (configuration.isSkipHttp()) {
                tomcat.setConnector(httpsConnector);
            }
        }

        for (final Connector c : configuration.getConnectors()) {
            tomcat.getService().addConnector(c);
        }
        if (!configuration.isSkipHttp() && !configuration.isSsl() && !configuration.getConnectors().isEmpty()) {
            tomcat.setConnector(configuration.getConnectors().iterator().next());
        }

        if (configuration.getUsers() != null) {
            for (final Map.Entry<String, String> user : configuration.getUsers().entrySet()) {
                tomcat.addUser(user.getKey(), user.getValue());
            }
        }
        if (configuration.getRoles() != null) {
            for (final Map.Entry<String, String> user : configuration.getRoles().entrySet()) {
                for (final String role : user.getValue().split(" *, *")) {
                    tomcat.addRole(user.getKey(), role);
                }
            }
        }

        StreamSupport.stream(ServiceLoader.load(Meecrowave.InstanceCustomizer.class).spliterator(), false)
                .peek(i -> {
                    if (MeecrowaveAwareInstanceCustomizer.class.isInstance(i)) {
                        MeecrowaveAwareInstanceCustomizer.class.cast(i).setMeecrowave(this);
                    }
                })
                .sorted(Priotities::sortByPriority)
                .forEach(c -> c.accept(tomcat));
        configuration.getInstanceCustomizers().forEach(c -> c.accept(tomcat));

        StreamSupport.stream(ServiceLoader.load(Meecrowave.ContextCustomizer.class).spliterator(), false)
                .peek(i -> {
                    if (MeecrowaveAwareContextCustomizer.class.isInstance(i)) {
                        MeecrowaveAwareContextCustomizer.class.cast(i).setMeecrowave(this);
                    }
                })
                .sorted(Priotities::sortByPriority)
                .forEach(configuration::addGlobalContextCustomizer);

        beforeStart();


        if (configuration.isInitializeClientBus() && Cxfs.IS_PRESENT && !Cxfs.hasDefaultBus()) {
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

    public ConfigurableBus getClientBus() {
        return clientBus;
    }

    /**
     * Store away the current system property for restoring it later
     * during shutdown.
     *
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
        if (skip) {
            Registry.disableRegistry();
        }
    }

    /**
     * Syntax uses:
     * <code>
     * valves.myValve1._className = org.apache.meecrowave.tomcat.LoggingAccessLogPattern
     * valves.myValve1._order = 0
     * <p>
     * valves.myValve1._className = SSOVa
     * valves.myValve1._order = 1
     * valves.myValve1.showReportInfo = false
     * </code>
     *
     * @return the list of valve from the properties.
     */
    private List<Valve> buildValves() {
        final List<Valve> valves = new ArrayList<>();
        configuration.getProperties().stringPropertyNames().stream()
                .filter(key -> key.startsWith("valves.") && key.endsWith("._className"))
                .sorted(comparing(key -> Integer.parseInt(configuration.getProperties()
                        .getProperty(key.replaceFirst("\\._className$", "._order"), "0"))))
                .map(key -> key.split("\\."))
                .filter(parts -> parts.length == 3)
                .forEach(key -> {
                    final String prefix = key[0] + '.' + key[1] + '.';
                    final ObjectRecipe recipe = newRecipe(configuration.getProperties().getProperty(prefix + key[2]));
                    configuration.getProperties().stringPropertyNames().stream()
                            .filter(it -> it.startsWith(prefix) && !it.endsWith("._order") && !it.endsWith("._className"))
                            .forEach(propKey -> {
                                final String value = configuration.getProperties().getProperty(propKey);
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
        for (final String key : configuration.getProperties().stringPropertyNames()) {
            if (key.startsWith("connector.sslhostconfig.") && key.split("\\.").length == 3) {
                final String substring = key.substring("connector.sslhostconfig.".length());
                defaultSslHostConfig.setProperty(substring, configuration.getProperties().getProperty(key));
            }
        }
        if (!defaultSslHostConfig.getProperties().isEmpty()) {
            sslHostConfigs.add(SSLHostConfig.class.cast(defaultSslHostConfig.create()));
        }
        // Allows to add N Multiple SSLHostConfig elements not including the default one.
        final Collection<Integer> itemNumbers = configuration.getProperties().stringPropertyNames()
                .stream()
                .filter(key -> (key.startsWith("connector.sslhostconfig.") && key.split("\\.").length == 4))
                .map(key -> Integer.parseInt(key.split("\\.")[2]))
                .collect(toSet());
        itemNumbers.stream().sorted().forEach(itemNumber -> {
            final ObjectRecipe recipe = newRecipe(SSLHostConfig.class.getName());
            final String prefix = "connector.sslhostconfig." + itemNumber + '.';
            configuration.getProperties().stringPropertyNames().stream()
                    .filter(k -> k.startsWith(prefix))
                    .forEach(key -> {
                        final String keyName = key.split("\\.")[3];
                        recipe.setProperty(keyName, configuration.getProperties().getProperty(key));
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
                Cxfs.resetDefaultBusIfEquals(clientBus); // after if runnables or listeners trigger CXF
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
                    if (deleteBase && base != null) {
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
        final Properties properties = configuration.getProperties();
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
                connector.setProperty(attr.getKey(), attr.getValue());
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
                    final File parentFile = to.getParentFile();
                    createDirectory(parentFile.getParentFile(), parentFile.getName());
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
        deleteBase = false;
        String dir = configuration.getDir();
        if (dir != null) {
            final File dirFile = new File(dir);
            if (dirFile.exists()) {
                if (base != null && base.exists() && configuration.isDeleteBaseOnStartup()) {
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

        deleteBase = true;
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
    public static class Builder extends Configuration /* inheritance for backward compatibility */ {
        // IMPORTANT: this must stay without any field, use parent class to add config please
        //            this only holds builder methods

        public Builder() {
            // no-op
        }

        // mainly for backward compat
        public Builder(final Configuration configuration) {
            super(configuration);
        }

        @Override
        public Builder loadFrom(String resource) {
            super.loadFrom(resource);
            return this;
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

        public Builder sharedLibraries(final String sharedLibraries) {
            setSharedLibraries(sharedLibraries);
            return this;
        }

        public Builder securityConstraints(final Meecrowave.SecurityConstaintBuilder securityConstraint) {
            if (getSecurityConstraints() == null) {
                setSecurityConstraints(new ArrayList<>());
            }
            getSecurityConstraints().add(securityConstraint);
            return this;
        }

        public Builder randomHttpPort() {
            try (final ServerSocket serverSocket = new ServerSocket(0)) {
                setHttpPort(serverSocket.getLocalPort());
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return this;
        }

        public Builder randomHttpsPort() {
            try (final ServerSocket serverSocket = new ServerSocket(0)) {
                setHttpsPort(serverSocket.getLocalPort());
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return this;
        }

        public Builder property(final String key, final String value) {
            getProperties().setProperty(key, value);
            return this;
        }


        public Builder user(final String name, final String pwd) {
            if (getUsers() == null) {
                setUsers(new HashMap<>());
            }
            getUsers().put(name, pwd);
            return this;
        }

        public Builder role(final String user, final String roles) {
            if (getRoles() == null) {
                setRoles(new HashMap<>());
            }
            getRoles().put(user, roles);
            return this;
        }

        public Builder cxfServletParam(final String key, final String value) {
            if (getCxfServletParams() == null) {
                setCxfServletParams(new HashMap<>());
            }
            getCxfServletParams().put(key, value);
            return this;
        }

        @Deprecated // withers are deprecated, we prefer the no fluent API
        public Builder withJsonpBufferStrategy(final String jsonpBufferStrategy) {
            setJsonpBufferStrategy(jsonpBufferStrategy);
            return this;
        }

        public Builder noShutdownHook() {
            setUseShutdownHook(false);
            return this;
        }

        public Builder instanceCustomizer(final Consumer<Tomcat> customizer) {
            addInstanceCustomizer(customizer);
            return this;
        }

        public Builder pidFile(final Path pidFile) {
            setPidFile(pidFile.toFile());
            return this;
        }

        public Builder watcherBouncing(final int watcherBouncing) {
            setWatcherBouncing(watcherBouncing);
            return this;
        }

        public Builder httpPort(final int httpPort) {
            if (httpPort <= 0) {
                randomHttpPort();
            } else {
                setHttpPort(httpPort);
            }
            return this;
        }

        public Builder httpsPort(final int httpsPort) {
            if (httpsPort <= 0) {
                randomHttpsPort();
            } else {
                setHttpsPort(httpsPort);
            }
            return this;
        }

        public Builder stopPort(final int stopPort) {
            setStopPort(stopPort);
            return this;
        }

        public Builder host(final String host) {
            setHost(host);
            return this;
        }

        public Builder dir(final String dir) {
            setDir(dir);
            return this;
        }

        public Builder serverXml(final Path serverXml) {
            setServerXml(serverXml.toFile());
            return this;
        }

        public Builder keepServerXmlAsThis(final boolean keepServerXmlAsThis) {
            setKeepServerXmlAsThis(keepServerXmlAsThis);
            return this;
        }

        public Builder properties(final Properties properties) {
            getProperties().putAll(properties);
            return this;
        }

        public Builder quickSession(final boolean quickSession) {
            setQuickSession(quickSession);
            return this;
        }

        public Builder skipHttp(final boolean skipHttp) {
            setSkipHttp(skipHttp);
            return this;
        }

        public Builder ssl(final boolean ssl) {
            setSsl(ssl);
            return this;
        }

        public Builder keystoreFile(final String keystoreFile) {
            setKeystoreFile(keystoreFile);
            return this;
        }

        public Builder keystorePass(final String keystorePass) {
            setKeystorePass(keystorePass);
            return this;
        }

        public Builder keystoreType(final String keystoreType) {
            setKeystoreType(keystoreType);
            return this;
        }

        public Builder clientAuth(final String clientAuth) {
            setClientAuth(clientAuth);
            return this;
        }

        public Builder keyAlias(final String keyAlias) {
            setKeyAlias(keyAlias);
            return this;
        }

        public Builder sslProtocol(final String sslProtocol) {
            setSslProtocol(sslProtocol);
            return this;
        }

        public Builder webXml(final String webXml) {
            setWebXml(webXml);
            return this;
        }

        public Builder loginConfig(final LoginConfigBuilder loginConfig) {
            setLoginConfig(loginConfig);
            return this;
        }

        public Builder securityConstraint(final SecurityConstaintBuilder securityConstraint) {
            if (getSecurityConstraints() == null) {
                setSecurityConstraints(new ArrayList<>());
            }
            getSecurityConstraints().add(securityConstraint);
            return this;
        }

        public Builder realm(final Realm realm) {
            setRealm(realm);
            return this;
        }

        public Builder users(final Map<String, String> users) {
            if (getUsers() == null) {
                setUsers(new HashMap<>());
            }
            getUsers().putAll(users);
            return this;
        }

        public Builder roles(final Map<String, String> roles) {
            if (getRoles() == null) {
                setRoles(new HashMap<>());
            }
            getRoles().putAll(roles);
            return this;
        }

        public Builder http2(final boolean http2) {
            setHttp2(http2);
            return this;
        }

        public Builder connector(final Connector connector) {
            getConnectors().add(connector);
            return this;
        }

        public Builder tempDir(final String tempDir) {
            setTempDir(tempDir);
            return this;
        }

        public Builder webResourceCached(final boolean webResourceCached) {
            setWebResourceCached(webResourceCached);
            return this;
        }

        public Builder conf(final String conf) {
            setConf(conf);
            return this;
        }

        public Builder deleteBaseOnStartup(final boolean deleteBaseOnStartup) {
            setDeleteBaseOnStartup(deleteBaseOnStartup);
            return this;
        }

        public Builder jaxrsMapping(final String jaxrsMapping) {
            setJaxrsMapping(jaxrsMapping);
            return this;
        }

        public Builder cdiConversation(final boolean cdiConversation) {
            setCdiConversation(cdiConversation);
            return this;
        }

        public Builder jaxrsProviderSetup(final boolean jaxrsProviderSetup) {
            setJaxrsProviderSetup(jaxrsProviderSetup);
            return this;
        }

        public Builder jaxrsDefaultProviders(final String jaxrsDefaultProviders) {
            setJaxrsDefaultProviders(jaxrsDefaultProviders);
            return this;
        }

        public Builder jaxrsAutoActivateBeanValidation(final boolean jaxrsAutoActivateBeanValidation) {
            setJaxrsAutoActivateBeanValidation(jaxrsAutoActivateBeanValidation);
            return this;
        }

        public Builder jaxrsLogProviders(final boolean jaxrsLogProviders) {
            setJaxrsLogProviders(jaxrsLogProviders);
            return this;
        }

        public Builder jsonpBufferStrategy(final String jsonpBufferStrategy) {
            setJsonpBufferStrategy(jsonpBufferStrategy);
            return this;
        }

        public Builder jsonpMaxStringLen(final int jsonpMaxStringLen) {
            setJsonpMaxStringLen(jsonpMaxStringLen);
            return this;
        }

        public Builder jsonpMaxReadBufferLen(final int jsonpMaxReadBufferLen) {
            setJsonpMaxReadBufferLen(jsonpMaxReadBufferLen);
            return this;
        }

        public Builder jsonpMaxWriteBufferLen(final int jsonpMaxWriteBufferLen) {
            setJsonpMaxWriteBufferLen(jsonpMaxWriteBufferLen);
            return this;
        }

        public Builder jsonpSupportsComment(final boolean jsonpSupportsComment) {
            setJsonpSupportsComment(jsonpSupportsComment);
            return this;
        }

        public Builder jsonpPrettify(final boolean jsonpPrettify) {
            setJsonpPrettify(jsonpPrettify);
            return this;
        }

        public Builder jsonbEncoding(final String jsonbEncoding) {
            setJsonbEncoding(jsonbEncoding);
            return this;
        }

        public Builder jsonbNulls(final boolean jsonbNulls) {
            setJsonbNulls(jsonbNulls);
            return this;
        }

        public Builder jsonbIJson(final boolean jsonbIJson) {
            setJsonbIJson(jsonbIJson);
            return this;
        }

        public Builder jsonbPrettify(final boolean jsonbPrettify) {
            setJsonbPrettify(jsonbPrettify);
            return this;
        }

        public Builder jsonbBinaryStrategy(final String jsonbBinaryStrategy) {
            setJsonbBinaryStrategy(jsonbBinaryStrategy);
            return this;
        }

        public Builder jsonbNamingStrategy(final String jsonbNamingStrategy) {
            setJsonbNamingStrategy(jsonbNamingStrategy);
            return this;
        }

        public Builder jsonbOrderStrategy(final String jsonbOrderStrategy) {
            setJsonbOrderStrategy(jsonbOrderStrategy);
            return this;
        }

        public Builder loggingGlobalSetup(final boolean loggingGlobalSetup) {
            setLoggingGlobalSetup(loggingGlobalSetup);
            return this;
        }

        public Builder tomcatScanning(final boolean tomcatScanning) {
            setTomcatScanning(tomcatScanning);
            return this;
        }

        public Builder tomcatAutoSetup(final boolean tomcatAutoSetup) {
            setTomcatAutoSetup(tomcatAutoSetup);
            return this;
        }

        public Builder tomcatJspDevelopment(final boolean tomcatJspDevelopment) {
            setTomcatJspDevelopment(tomcatJspDevelopment);
            return this;
        }

        public Builder useShutdownHook(final boolean useShutdownHook) {
            setUseShutdownHook(useShutdownHook);
            return this;
        }

        public Builder tomcatFilter(final String tomcatFilter) {
            setTomcatFilter(tomcatFilter);
            return this;
        }

        public Builder scanningIncludes(final String scanningIncludes) {
            setScanningIncludes(scanningIncludes);
            return this;
        }

        public Builder scanningExcludes(final String scanningExcludes) {
            setScanningExcludes(scanningExcludes);
            return this;
        }

        public Builder scanningPackageIncludes(final String scanningPackageIncludes) {
            setScanningPackageIncludes(scanningPackageIncludes);
            return this;
        }

        public Builder scanningPackageIncludes(final Collection<String> scanningPackageIncludes) {
            setScanningPackageIncludes(String.join(",", scanningPackageIncludes));
            return this;
        }

        public Builder scanningPackageExcludes(final String scanningPackageExcludes) {
            setScanningPackageExcludes(scanningPackageExcludes);
            return this;
        }

        public Builder scanningPackageExcludes(final Collection<String> scanningPackageExcludes) {
            setScanningPackageExcludes(String.join(",", scanningPackageExcludes));
            return this;
        }

        public Builder webSessionTimeout(final int webSessionTimeout) {
            setWebSessionTimeout(webSessionTimeout);
            return this;
        }

        public Builder webSessionCookieConfig(final String webSessionCookieConfig) {
            setWebSessionCookieConfig(webSessionCookieConfig);
            return this;
        }

        public Builder useTomcatDefaults(final boolean useTomcatDefaults) {
            setUseTomcatDefaults(useTomcatDefaults);
            return this;
        }

        public Builder tomcatWrapLoader(final boolean tomcatWrapLoader) {
            setTomcatWrapLoader(tomcatWrapLoader);
            return this;
        }

        public Builder tomcatNoJmx(final boolean tomcatNoJmx) {
            setTomcatNoJmx(tomcatNoJmx);
            return this;
        }

        public Builder useLog4j2JulLogManager(final boolean useLog4j2JulLogManager) {
            setUseLog4j2JulLogManager(useLog4j2JulLogManager);
            return this;
        }

        public Builder injectServletContainerInitializer(final boolean injectServletContainerInitializer) {
            setInjectServletContainerInitializer(injectServletContainerInitializer);
            return this;
        }

        public Builder tomcatAccessLogPattern(final String tomcatAccessLogPattern) {
            setTomcatAccessLogPattern(tomcatAccessLogPattern);
            return this;
        }

        public Builder meecrowaveProperties(final String meecrowaveProperties) {
            setMeecrowaveProperties(meecrowaveProperties);
            return this;
        }

        public Builder jaxwsSupportIfAvailable(final boolean jaxwsSupportIfAvailable) {
            setJaxwsSupportIfAvailable(jaxwsSupportIfAvailable);
            return this;
        }

        public Builder defaultSSLHostConfigName(final String defaultSSLHostConfigName) {
            setDefaultSSLHostConfigName(defaultSSLHostConfigName);
            return this;
        }

        public Builder initializeClientBus(final boolean initializeClientBus) {
            setInitializeClientBus(initializeClientBus);
            return this;
        }

        public Builder instanceCustomizers(final Consumer<Tomcat> instanceCustomizer) {
            addInstanceCustomizer(instanceCustomizer);
            return this;
        }

        public Builder initializer(final ServletContainerInitializer initializer) {
            addServletContextInitializer(initializer);
            return this;
        }

        public Builder antiResourceLocking(final boolean antiResourceLocking) {
            setAntiResourceLocking(antiResourceLocking);
            return this;
        }

        public Builder contextConfigurer(final Consumer<Context> contextConfigurers) {
            addGlobalContextCustomizer(contextConfigurers);
            return this;
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
        private final Consumer<Context> redeployCallback;

        public DeploymentMeta(final String context, final File docBase, final Consumer<Context> consumer, final Consumer<Context> redeployCallback) {
            this.context = context;
            this.docBase = docBase;
            this.consumer = consumer;
            this.redeployCallback = redeployCallback;
        }
    }

    // just to type it and allow some extensions to use a ServiceLoader
    public interface ConfigurationCustomizer extends Consumer<Configuration> {
    }

    /**
     * SPI to customize Tomcat instance. They are sorted by Priority, default being 0.
     */
    public interface InstanceCustomizer extends Consumer<Tomcat> {
    }

    /**
     * SPI to customize context instances. They are sorted by Priority, default being 0.
     */
    public interface ContextCustomizer extends Consumer<Context> {
    }

    public interface MeecrowaveAwareContextCustomizer extends ContextCustomizer {
        void setMeecrowave(Meecrowave meecrowave);
    }

    // since it is too early to have CDI and lookup the instance we must set it manually
    public interface MeecrowaveAwareInstanceCustomizer extends InstanceCustomizer {
        void setMeecrowave(Meecrowave meecrowave);
    }

    private static final class MeecrowaveContainerLoader extends URLClassLoader {
        private MeecrowaveContainerLoader(final URL[] urls, final ClassLoader parent) {
            super(urls, parent);
        }
    }
}
