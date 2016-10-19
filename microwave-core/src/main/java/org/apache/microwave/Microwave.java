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
package org.apache.microwave;

import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.cxf.helpers.FileUtils;
import org.apache.microwave.cxf.CxfCdiAutoSetup;
import org.apache.microwave.openwebbeans.OWBAutoSetup;
import org.apache.microwave.runner.cli.CliOption;
import org.apache.microwave.tomcat.ProvidedLoader;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.xbean.finder.ResourceFinder;
import org.apache.xbean.recipe.ObjectRecipe;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Consumer;

import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;

public class Microwave implements AutoCloseable {
    private final Builder configuration;
    protected File base;

    protected InternalTomcat tomcat;

    // we can undeploy webapps with that later
    private final Map<String, Context> contexts = new HashMap<>();

    public Microwave(final Builder builder) {
        this.configuration = builder;
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

    public Microwave deployClasspath() {
        return deployClasspath("");
    }

    public Microwave deployClasspath(final String context) {
        final File dir = new File(configuration.tempDir, "classpath/fake-" + context.replace("/", ""));
        FileUtils.mkDir(dir);
        return deployWebapp(context, dir, c -> c.setLoader(new ProvidedLoader(Thread.currentThread().getContextClassLoader())));
    }

    public Microwave deployWebapp(final File warOrDir) {
        return deployWebapp("", warOrDir, null);
    }

    public Microwave deployWebapp(final String context, final File warOrDir) {
        return deployWebapp(context, warOrDir, null);
    }

    public Microwave deployWebapp(final String context, final File warOrDir, final Consumer<Context> customizer) {
        if (contexts.containsKey(context)) {
            throw new IllegalArgumentException("Already deployed: '" + context + "'");
        }

        final StandardContext ctx = new StandardContext();
        ctx.setPath(context);
        ctx.setName(context);
        try {
            ctx.setDocBase(warOrDir.getCanonicalPath());
        } catch (final IOException e) {
            ctx.setDocBase(warOrDir.getAbsolutePath());
        }
        ctx.addLifecycleListener(new Tomcat.FixContextListener());
        ctx.addLifecycleListener(event -> {
            switch (event.getType()) {
                case Lifecycle.AFTER_START_EVENT:
                    ctx.getResources().setCachingAllowed(configuration.webResourceCached);
                    break;
                case Lifecycle.BEFORE_START_EVENT:
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

        ctx.addServletContainerInitializer((c, ctx1) -> {
            ctx.getServletContext().setAttribute("microwave.configuration", configuration);
            try {
                new OWBAutoSetup().onStartup(c, ctx1);
                new CxfCdiAutoSetup().onStartup(c, ctx1);
            } finally {
                ctx.getServletContext().removeAttribute("microwave.configuration");
            }
        }, emptySet());

        ofNullable(customizer).ifPresent(c -> c.accept(ctx));

        tomcat.getHost().addChild(ctx);
        contexts.put(context, ctx);
        return this;
    }

    public Microwave bake() {
        return bake("");
    }

    public Microwave bake(final String ctx) {
        start();
        return deployClasspath(ctx);
    }

    public Microwave start() {
        if (configuration.quickSession) {
            tomcat = new TomcatWithFastSessionIDs();
        } else {
            tomcat = new InternalTomcat();
        }

        { // setup
            base = new File(getBaseDir());
            if (base.exists() && configuration.deleteBaseOnStartup) {
                FileUtils.delete(base);
            } else if (!base.exists()) {
                FileUtils.mkDir(base);
            }

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
                    placeHolders.put("microwave.embedded.http", Integer.toString(configuration.httpPort));
                    placeHolders.put("microwave.embedded.https", Integer.toString(configuration.httpsPort));
                    placeHolders.put("microwave.embedded.stop", Integer.toString(configuration.stopPort));
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
                    IOUtils.copy(is, fos);
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
                    serverXmlContent = IOUtils.toString(stream, StandardCharsets.UTF_8);
                    for (final Map.Entry<String, String> pair : replacements.entrySet()) {
                        serverXmlContent = serverXmlContent.replace(pair.getKey(), pair.getValue());
                    }
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
                try (final OutputStream os = new FileOutputStream(file)) {
                    IOUtils.write(serverXmlContent, os);
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

        if (!initialized) {
            tomcat.setHostname(configuration.host);
            tomcat.getEngine().setDefaultHost(configuration.host);
            final StandardHost host = new StandardHost();
            host.setName(configuration.host);
            host.setAppBase(webapps.getAbsolutePath());
            try {
                host.setWorkDir(new File(base, "work").getCanonicalPath());
            } catch (final IOException e) {
                host.setWorkDir(new File(base, "work").getAbsolutePath());
            }
            tomcat.setHost(host);
            tomcat.getEngine().addChild( host );
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
            if (configuration.http2) { // would likely need SSLHostConfig programmatically
                connector.addUpgradeProtocol(new Http2Protocol());
            }

            tomcat.getService().addConnector(connector);
            tomcat.setConnector(connector);
        }

        // create https connector
        if (configuration.ssl) {
            final Connector httpsConnector = createConnector();
            httpsConnector.setPort(configuration.httpsPort);
            httpsConnector.setSecure(true);
            httpsConnector.setProperty("SSLEnabled", "true");
            httpsConnector.setProperty("sslProtocol", configuration.sslProtocol);

            if (configuration.keystoreFile != null) {
                httpsConnector.setAttribute("", configuration.keystoreFile);
            }
            if (configuration.keystorePass != null) {
                httpsConnector.setAttribute("keystorePass", configuration.keystorePass);
            }
            httpsConnector.setAttribute("keystoreType", configuration.keystoreType);
            if (configuration.clientAuth != null) {
                httpsConnector.setAttribute("clientAuth", configuration.clientAuth);
            }
            if (configuration.keyAlias != null) {
                httpsConnector.setAttribute("keyAlias", configuration.keyAlias);
            }

            if (configuration.http2) { // would likely need SSLHostConfig programmatically
                httpsConnector.addUpgradeProtocol(new Http2Protocol());
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

        beforeStart();

        try {
            if (!initialized) {
                tomcat.init();
            }
            tomcat.start();
        } catch (final LifecycleException e) {
            throw new IllegalStateException(e);
        }
        return this;
    }

    protected void beforeStart() {
        // no-op
    }

    protected void beforeStop() {
        // no-op
    }

    @Override
    public void close() {
        if (tomcat == null) {
            return;
        }
        beforeStop();
        try {
            tomcat.stop();
            tomcat.destroy();
        } catch (final LifecycleException e) {
            throw new IllegalStateException(e);
        } finally {
            FileUtils.delete(base);
        }
    }

    protected Connector createConnector() {
        final Connector connector;
        final Properties properties = configuration.properties;
        if (properties != null) {
            final Map<String, String> attributes = new HashMap<>();
            final ObjectRecipe recipe = new ObjectRecipe(Connector.class);
            for (final String key : properties.stringPropertyNames()) {
                if (!key.startsWith("connector.")) {
                    continue;
                }
                final String substring = key.substring("connector.".length());
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
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Unable to make dir " + dir.getAbsolutePath());
        }

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
                        IOUtils.copy(is, os);
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

    private String getBaseDir() {
        File file;
        try {

            final String dir = configuration.dir;
            if (dir != null) {
                final File dirFile = new File(dir);
                if (dirFile.exists()) {
                    return dir;
                }
                FileUtils.mkDir(dirFile);
                return dirFile.getAbsolutePath();
            }

            try {
                final File target = new File("target");
                file = File.createTempFile("microwave", "-home", target.exists() ? target : null);
            } catch (final Exception e) {

                final File tmp = new File(configuration.tempDir);
                if (!tmp.exists() && !tmp.mkdirs()) {
                    throw new IOException("Failed to create local tmp directory: " + tmp.getAbsolutePath());
                }

                file = File.createTempFile("microwave", "-home", tmp);
            }

            return file.getAbsolutePath();

        } catch (final IOException e) {
            throw new MicrowaveExplosion("Failed to get or create base dir: " + configuration.dir, e);
        }
    }

    public static class Builder {
        @CliOption(name = "http", description = "HTTP port")
        private int httpPort = 8080;

        @CliOption(name = "https", description = "HTTPS port")
        private int httpsPort = 8443;

        @CliOption(name = "stop", description = "Shutdown port if used or -1")
        private int stopPort = -1;

        @CliOption(name = "host", description = "Default host")
        private String host = "localhost";

        @CliOption(name = "dir", description = "Root folder if provided otherwise a fake one is created in tmp-dir")
        protected String dir;

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
        private String tempDir = new File(System.getProperty("java.io.tmpdir"), "microwave_" + System.nanoTime()).getAbsolutePath();

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
        private boolean jaxrsProviderSetup;

        public Builder() { // load defaults
            loadFrom("microwave.properties");
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

        public void setLoginConfig(final LoginConfigBuilder loginConfig) {
            this.loginConfig = loginConfig;
        }

        public Collection<SecurityConstaintBuilder> getSecurityConstraints() {
            return securityConstraints;
        }

        public void setSecurityConstraints(final Collection<SecurityConstaintBuilder> securityConstraints) {
            this.securityConstraints = securityConstraints;
        }

        public Realm getRealm() {
            return realm;
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

        public void addCustomizer(final ConfigurationCustomizer configurationCustomizer) {
            configurationCustomizer.customize(this);
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
            for (final String key : config.stringPropertyNames()) {
                final String val = config.getProperty(key);
                if (val == null || val.trim().isEmpty()) {
                    continue;
                }
                final String newVal = strSubstitutor.replace(config.getProperty(key));
                if (!val.equals(newVal)) {
                    config.setProperty(key, newVal);
                }
            }


            final String http = config.getProperty("http");
            if (http != null) {
                httpPort = Integer.parseInt(http);
            }
            final String https = config.getProperty("https");
            if (https != null) {
                httpsPort = Integer.parseInt(https);
            }
            final String stop = config.getProperty("stop");
            if (stop != null) {
                stopPort = Integer.parseInt(stop);
            }
            final String host = config.getProperty("host");
            if (host != null) {
                this.host = host;
            }
            final String dir = config.getProperty("dir");
            if (dir != null) {
                this.dir = dir;
            }
            final String serverXml = config.getProperty("serverXml");
            if (serverXml != null) {
                setServerXml(serverXml);
            }
            final String keepServerXmlAsThis = config.getProperty("keepServerXmlAsThis");
            if (keepServerXmlAsThis != null) {
                this.keepServerXmlAsThis = Boolean.parseBoolean(keepServerXmlAsThis);
            }
            final String quickSession = config.getProperty("quickSession");
            if (quickSession != null) {
                this.quickSession = Boolean.parseBoolean(quickSession);
            }
            final String skipHttp = config.getProperty("skipHttp");
            if (skipHttp != null) {
                this.skipHttp = Boolean.parseBoolean(skipHttp);
            }
            final String ssl = config.getProperty("ssl");
            if (ssl != null) {
                this.ssl = Boolean.parseBoolean(ssl);
            }
            final String http2 = config.getProperty("http2");
            if (http2 != null) {
                this.http2 = Boolean.parseBoolean(http2);
            }
            final String deleteBaseOnStartup = config.getProperty("deleteBaseOnStartup");
            if (deleteBaseOnStartup != null) {
                this.deleteBaseOnStartup = Boolean.parseBoolean(deleteBaseOnStartup);
            }
            final String webResourceCached = config.getProperty("webResourceCached");
            if (webResourceCached != null) {
                this.webResourceCached = Boolean.parseBoolean(webResourceCached);
            }
            final String keystoreFile = config.getProperty("keystoreFile");
            if (keystoreFile != null) {
                this.keystoreFile = keystoreFile;
            }
            final String keystorePass = config.getProperty("keystorePass");
            if (keystorePass != null) {
                this.keystorePass = keystorePass;
            }
            final String keystoreType = config.getProperty("keystoreType");
            if (keystoreType != null) {
                this.keystoreType = keystoreType;
            }
            final String clientAuth = config.getProperty("clientAuth");
            if (clientAuth != null) {
                this.clientAuth = clientAuth;
            }
            final String keyAlias = config.getProperty("keyAlias");
            if (keyAlias != null) {
                this.keyAlias = keyAlias;
            }
            final String sslProtocol = config.getProperty("sslProtocol");
            if (sslProtocol != null) {
                this.sslProtocol = sslProtocol;
            }
            final String webXml = config.getProperty("webXml");
            if (webXml != null) {
                this.webXml = webXml;
            }
            final String tempDir = config.getProperty("tempDir");
            if (tempDir != null) {
                this.tempDir = tempDir;
            }
            final String conf = config.getProperty("conf");
            if (conf != null) {
                this.conf = conf;
            }
            for (final String prop : config.stringPropertyNames()) {
                if (prop.startsWith("properties.")) {
                    property(prop.substring("properties.".length()), config.getProperty(prop));
                } else if (prop.startsWith("users.")) {
                    user(prop.substring("users.".length()), config.getProperty(prop));
                } else if (prop.startsWith("roles.")) {
                    role(prop.substring("roles.".length()), config.getProperty(prop));
                } else if (prop.startsWith("connector.")) { // created in container
                    property(prop, config.getProperty(prop));
                } else if (prop.equals("realm")) {
                    final ObjectRecipe recipe = new ObjectRecipe(config.getProperty(prop));
                    for (final String realmConfig : config.stringPropertyNames()) {
                        if (realmConfig.startsWith("realm.")) {
                            recipe.setProperty(realmConfig.substring("realm.".length()), config.getProperty(realmConfig));
                        }
                    }
                    this.realm = Realm.class.cast(recipe.create());
                } else if (prop.equals("login")) {
                    final ObjectRecipe recipe = new ObjectRecipe(LoginConfigBuilder.class.getName());
                    for (final String nestedConfig : config.stringPropertyNames()) {
                        if (nestedConfig.startsWith("login.")) {
                            recipe.setProperty(nestedConfig.substring("login.".length()), config.getProperty(nestedConfig));
                        }
                    }
                    loginConfig = LoginConfigBuilder.class.cast(recipe.create());
                } else if (prop.equals("securityConstraint")) {
                    final ObjectRecipe recipe = new ObjectRecipe(SecurityConstaintBuilder.class.getName());
                    for (final String nestedConfig : config.stringPropertyNames()) {
                        if (nestedConfig.startsWith("securityConstraint.")) {
                            recipe.setProperty(nestedConfig.substring("securityConstraint.".length()), config.getProperty(nestedConfig));
                        }
                    }
                    securityConstraints.add(SecurityConstaintBuilder.class.cast(recipe.create()));
                } else if (prop.equals("configurationCustomizer.")) {
                    final String next = prop.substring("configurationCustomizer.".length());
                    if (next.contains(".")) {
                        continue;
                    }
                    final ObjectRecipe recipe = new ObjectRecipe(properties.getProperty(prop + ".class"));
                    for (final String nestedConfig : config.stringPropertyNames()) {
                        if (nestedConfig.startsWith(prop) && !prop.endsWith(".class")) {
                            recipe.setProperty(nestedConfig.substring(prop.length() + 1 /*dot*/), config.getProperty(nestedConfig));
                        }
                    }
                    addCustomizer(ConfigurationCustomizer.class.cast(recipe.create()));
                }
            }
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

    public interface ConfigurationCustomizer {
        void customize(Builder configuration);
    }

    private static class InternalTomcat extends Tomcat {
        private void server(final Server s) {
            server = s;
            if (service == null) {
                final Service[] services = server.findServices();
                if (services.length > 0) {
                    service = services[0];
                    if (service.getContainer() != null) {
                        engine = Engine.class.cast(service.getContainer());
                        final org.apache.catalina.Container[] hosts = engine.findChildren();
                        if (hosts.length > 0) {
                            host = Host.class.cast(hosts[0]);
                        }
                    }
                }
                if (service.findConnectors().length > 0) {
                    connector = service.findConnectors()[0];
                }
            }
        }

        public Connector getRawConnector() {
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
        private static final String AJP_KEY = "AJP";
        private static final String HOST_KEY = "host";
        private static final String APP_BASE_KEY = "app-base";
        private static final String DEFAULT_CONNECTOR_KEY = HTTP_KEY;
        private static final String KEYSTORE_KEY = "keystoreFile";

        public static final String DEFAULT_HTTP_PORT = "8080";
        public static final String DEFAULT_HTTPS_PORT = "8443";
        public static final String DEFAULT_STOP_PORT = "8005";
        public static final String DEFAULT_AJP_PORT = "8009";
        public static final String DEFAULT_HOST = "localhost";
        public static final String DEFAULT_APP_BASE = "webapps";
        public static final String DEFAULT_KEYSTORE = null;

        private final Map<String, String> values = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);

        public QuickServerXmlParser() { // ensure defaults are present
            this(true);
        }

        public QuickServerXmlParser(final boolean useDefaults) {
            if (useDefaults) {
                values.put(STOP_KEY, DEFAULT_STOP_PORT);
                values.put(HTTP_KEY, DEFAULT_HTTP_PORT);
                values.put(AJP_KEY, DEFAULT_AJP_PORT);
                values.put(HOST_KEY, DEFAULT_HOST);
                values.put(APP_BASE_KEY, DEFAULT_APP_BASE);
                values.put(KEYSTORE_KEY, DEFAULT_KEYSTORE);
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

                final String keystore = attributes.getValue("keystoreFile");
                if (null != keystore) {
                    values.put(KEYSTORE_KEY, keystore);
                }
            } else if ("Host".equalsIgnoreCase(localName)) {
                final String host = attributes.getValue("name");
                if (host != null) {
                    values.put(HOST_KEY, host);
                }

                final String appBase = attributes.getValue("appBase");
                if (appBase != null) {
                    values.put(APP_BASE_KEY, appBase);
                }
            }
        }

        public static QuickServerXmlParser parse(final File serverXml) {
            return parse(serverXml, true);
        }

        public static QuickServerXmlParser parse(final File serverXml, final boolean defaults) {
            final QuickServerXmlParser handler = new QuickServerXmlParser(defaults);
            try {
                final SAXParser parser = FACTORY.newSAXParser();
                parser.parse(serverXml, handler);
            } catch (final Exception e) {
                // no-op: using defaults
            }
            return handler;
        }

        public static QuickServerXmlParser parse(final String serverXmlContents) {
            final QuickServerXmlParser handler = new QuickServerXmlParser();
            try {
                final SAXParser parser = FACTORY.newSAXParser();
                parser.parse(new ByteArrayInputStream(serverXmlContents.getBytes()), handler);
            } catch (final Exception e) {
                // no-op: using defaults
            }
            return handler;
        }

        public String http() {
            return value(HTTP_KEY, DEFAULT_HTTP_PORT);
        }

        public String https() { // enough common to be exposed as method
            return securedValue(HTTP_KEY, DEFAULT_HTTPS_PORT);
        }

        public String ajp() {
            return value(AJP_KEY, DEFAULT_AJP_PORT);
        }

        public String stop() {
            return value(STOP_KEY, DEFAULT_STOP_PORT);
        }

        public String appBase() {
            return value(APP_BASE_KEY, DEFAULT_APP_BASE);
        }

        public String host() {
            return value(HOST_KEY, DEFAULT_HOST);
        }

        public String keystore() {
            return value(KEYSTORE_KEY, DEFAULT_KEYSTORE);
        }

        public String value(final String key, final String defaultValue) {
            final String val = values.get(key);
            if (val == null) {
                return defaultValue;
            }
            return val;
        }

        public String securedValue(final String key, final String defaultValue) {
            return value(key + SECURED_SUFFIX, defaultValue);
        }

        @Override
        public String toString() {
            return "QuickServerXmlParser: " + values;
        }
    }

}
