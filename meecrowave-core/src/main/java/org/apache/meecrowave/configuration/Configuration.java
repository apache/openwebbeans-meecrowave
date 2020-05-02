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
package org.apache.meecrowave.configuration;

import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.servlet.ServletContainerInitializer;

import org.apache.catalina.Context;
import org.apache.catalina.Realm;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.lang.Substitutor;
import org.apache.meecrowave.runner.cli.CliOption;
import org.apache.meecrowave.service.Priotities;
import org.apache.webbeans.config.PropertyLoader;
import org.apache.xbean.recipe.ObjectRecipe;
import org.apache.xbean.recipe.Option;

public class Configuration {
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
    private Meecrowave.LoginConfigBuilder loginConfig;

    @CliOption(name = "security-constraint", description = "web.xml security constraint")
    private Collection<Meecrowave.SecurityConstaintBuilder> securityConstraints = new LinkedList<>();

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
    private String jsonpBufferStrategy = "QUEUE";

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

    @CliOption(name = "tomcat-default-setup-jsp-development", description = "Should JSP support if available be set in development mode")
    private boolean tomcatJspDevelopment = false;

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

    @CliOption(name = "servlet-container-initializer", description = "ServletContainerInitializer instances.")
    private Collection<ServletContainerInitializer> initializers = new ArrayList<>();

    @CliOption(name = "tomcat-antiresourcelocking", description = "Should Tomcat anti resource locking feature be activated on StandardContext.")
    private boolean antiResourceLocking;

    @CliOption(name = "tomcat-context-configurer", description = "Configurers for all webapps. The Consumer<Context> instances will be applied to all deployments.")
    private Collection<Consumer<Context>> contextConfigurers;

    public Configuration(final Configuration toCopy) {
        pidFile = toCopy.pidFile;
        watcherBouncing = toCopy.watcherBouncing;
        httpPort = toCopy.httpPort;
        httpsPort = toCopy.httpsPort;
        stopPort = toCopy.stopPort;
        host = toCopy.host;
        dir = toCopy.dir;
        serverXml = toCopy.serverXml;
        keepServerXmlAsThis = toCopy.keepServerXmlAsThis;
        properties = toCopy.properties;
        quickSession = toCopy.quickSession;
        skipHttp = toCopy.skipHttp;
        ssl = toCopy.ssl;
        keystoreFile = toCopy.keystoreFile;
        keystorePass = toCopy.keystorePass;
        keystoreType = toCopy.keystoreType;
        clientAuth = toCopy.clientAuth;
        keyAlias = toCopy.keyAlias;
        sslProtocol = toCopy.sslProtocol;
        webXml = toCopy.webXml;
        loginConfig = toCopy.loginConfig;
        securityConstraints = toCopy.securityConstraints;
        realm = toCopy.realm;
        users = toCopy.users;
        roles = toCopy.roles;
        http2 = toCopy.http2;
        connectors.addAll(toCopy.connectors);
        tempDir = toCopy.tempDir;
        webResourceCached = toCopy.webResourceCached;
        conf = toCopy.conf;
        deleteBaseOnStartup = toCopy.deleteBaseOnStartup;
        jaxrsMapping = toCopy.jaxrsMapping;
        cdiConversation = toCopy.cdiConversation;
        jaxrsProviderSetup = toCopy.jaxrsProviderSetup;
        jaxrsDefaultProviders = toCopy.jaxrsDefaultProviders;
        jaxrsAutoActivateBeanValidation = toCopy.jaxrsAutoActivateBeanValidation;
        jaxrsLogProviders = toCopy.jaxrsLogProviders;
        jsonpBufferStrategy = toCopy.jsonpBufferStrategy;
        jsonpMaxStringLen = toCopy.jsonpMaxStringLen;
        jsonpMaxReadBufferLen = toCopy.jsonpMaxReadBufferLen;
        jsonpMaxWriteBufferLen = toCopy.jsonpMaxWriteBufferLen;
        jsonpSupportsComment = toCopy.jsonpSupportsComment;
        jsonpPrettify = toCopy.jsonpPrettify;
        jsonbEncoding = toCopy.jsonbEncoding;
        jsonbNulls = toCopy.jsonbNulls;
        jsonbIJson = toCopy.jsonbIJson;
        jsonbPrettify = toCopy.jsonbPrettify;
        jsonbBinaryStrategy = toCopy.jsonbBinaryStrategy;
        jsonbNamingStrategy = toCopy.jsonbNamingStrategy;
        jsonbOrderStrategy = toCopy.jsonbOrderStrategy;
        loggingGlobalSetup = toCopy.loggingGlobalSetup;
        cxfServletParams = toCopy.cxfServletParams;
        tomcatScanning = toCopy.tomcatScanning;
        tomcatAutoSetup = toCopy.tomcatAutoSetup;
        tomcatJspDevelopment = toCopy.tomcatJspDevelopment;
        useShutdownHook = toCopy.useShutdownHook;
        tomcatFilter = toCopy.tomcatFilter;
        scanningIncludes = toCopy.scanningIncludes;
        scanningExcludes = toCopy.scanningExcludes;
        scanningPackageIncludes = toCopy.scanningPackageIncludes;
        scanningPackageExcludes = toCopy.scanningPackageExcludes;
        webSessionTimeout = toCopy.webSessionTimeout;
        webSessionCookieConfig = toCopy.webSessionCookieConfig;
        useTomcatDefaults = toCopy.useTomcatDefaults;
        tomcatWrapLoader = toCopy.tomcatWrapLoader;
        tomcatNoJmx = toCopy.tomcatNoJmx;
        sharedLibraries = toCopy.sharedLibraries;
        useLog4j2JulLogManager = toCopy.useLog4j2JulLogManager;
        injectServletContainerInitializer = toCopy.injectServletContainerInitializer;
        tomcatAccessLogPattern = toCopy.tomcatAccessLogPattern;
        meecrowaveProperties = toCopy.meecrowaveProperties;
        jaxwsSupportIfAvailable = toCopy.jaxwsSupportIfAvailable;
        defaultSSLHostConfigName = toCopy.defaultSSLHostConfigName;
        initializeClientBus = toCopy.initializeClientBus;
        extensions.putAll(toCopy.extensions);
        instanceCustomizers.addAll(toCopy.instanceCustomizers);
        initializers = toCopy.initializers;
        antiResourceLocking = toCopy.antiResourceLocking;
        contextConfigurers = toCopy.contextConfigurers;
    }

    public Configuration() { // load defaults
        extensions.put(Meecrowave.ValueTransformers.class, new Meecrowave.ValueTransformers());
        StreamSupport.stream(ServiceLoader.load(Meecrowave.ConfigurationCustomizer.class).spliterator(), false)
                .sorted(Priotities::sortByPriority)
                .forEach(c -> c.accept(this));
        loadFrom(meecrowaveProperties);
    }

    public Collection<Consumer<Tomcat>> getInstanceCustomizers() {
        return instanceCustomizers;
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

    public boolean isAntiResourceLocking() {
        return antiResourceLocking;
    }

    public void setAntiResourceLocking(final boolean antiResourceLocking) {
        this.antiResourceLocking = antiResourceLocking;
    }

    public Collection<Consumer<Context>> getGlobalContextConfigurers() {
        return ofNullable(contextConfigurers).orElseGet(Collections::emptySet);
    }

    public boolean isTomcatJspDevelopment() {
        return tomcatJspDevelopment;
    }

    public void setTomcatJspDevelopment(final boolean tomcatJspDevelopment) {
        this.tomcatJspDevelopment = tomcatJspDevelopment;
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

    public Meecrowave.LoginConfigBuilder getLoginConfig() {
        return loginConfig;
    }

    public void setLoginConfig(final Meecrowave.LoginConfigBuilder loginConfig) {
        this.loginConfig = loginConfig;
    }

    public Collection<Meecrowave.SecurityConstaintBuilder> getSecurityConstraints() {
        return securityConstraints;
    }

    public void setSecurityConstraints(final Collection<Meecrowave.SecurityConstaintBuilder> securityConstraints) {
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

    public boolean isTomcatWrapLoader() {
        return tomcatWrapLoader;
    }

    public void setTomcatWrapLoader(final boolean tomcatWrapLoader) {
        this.tomcatWrapLoader = tomcatWrapLoader;
    }

    public void addInstanceCustomizer(final Consumer<Tomcat> customizer) {
        instanceCustomizers.add(customizer);
    }

    public void addCustomizer(final Consumer<Configuration> configurationCustomizer) {
        configurationCustomizer.accept(this);
    }

    public void addGlobalContextCustomizer(final Consumer<Context> contextConfigurer) {
        if (contextConfigurers == null) {
            contextConfigurers = new ArrayList<>();
        }
        contextConfigurers.add(contextConfigurer);
    }

    public void addServletContextInitializer(final ServletContainerInitializer initializer) {
        initializers.add(initializer);
    }

    public Collection<ServletContainerInitializer> getInitializers() {
        return initializers;
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
        final Substitutor strSubstitutor = new Substitutor(emptyMap()) {
            @Override
            public String getOrDefault(final String key, final String or) {
                final String property = System.getProperty(key);
                return property == null ? config.getProperty(key, or) : or;
            }
        };

        final Meecrowave.ValueTransformers transformers = getExtension(Meecrowave.ValueTransformers.class);
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

        for (final Field field : Configuration.class.getDeclaredFields()) {
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
                        try (final ServerSocket serverSocket = new ServerSocket(0)) {
                            setHttpPort(serverSocket.getLocalPort());
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        }
                        toSet = null;
                    } else {
                        toSet = Integer.parseInt(val);
                    }
                } else if (field.getType() == boolean.class) {
                    toSet = Boolean.parseBoolean(val);
                } else if (field.getType() == File.class) {
                    toSet = new File(val);
                } else if (field.getType() == long.class) {
                    toSet = Long.parseLong(val);
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
                getProperties().setProperty(prop.substring("properties.".length()), config.getProperty(prop));
            } else if (prop.startsWith("users.")) {
                if (users == null) {
                    users = new HashMap<>();
                }
                users.put(prop.substring("users.".length()), config.getProperty(prop));
            } else if (prop.startsWith("roles.")) {
                if (roles == null) {
                    roles = new HashMap<>();
                }
                roles.put(prop.substring("roles.".length()), config.getProperty(prop));
            } else if (prop.startsWith("cxf.servlet.params.")) {
                if (cxfServletParams == null) {
                    cxfServletParams = new HashMap<>();
                }
                cxfServletParams.put(prop.substring("cxf.servlet.params.".length()), config.getProperty(prop));
            } else if (prop.startsWith("connector.")) { // created in container
                getProperties().setProperty(prop, config.getProperty(prop));
            } else if (prop.equals("realm")) {
                final ObjectRecipe recipe = newRecipe(config.getProperty(prop));
                for (final String realmConfig : config.stringPropertyNames()) {
                    if (realmConfig.startsWith("realm.")) {
                        recipe.setProperty(realmConfig.substring("realm.".length()), config.getProperty(realmConfig));
                    }
                }
                this.realm = Realm.class.cast(recipe.create());
            } else if (prop.equals("login")) {
                final ObjectRecipe recipe = newRecipe(Meecrowave.LoginConfigBuilder.class.getName());
                for (final String nestedConfig : config.stringPropertyNames()) {
                    if (nestedConfig.startsWith("login.")) {
                        recipe.setProperty(nestedConfig.substring("login.".length()), config.getProperty(nestedConfig));
                    }
                }
                loginConfig = Meecrowave.LoginConfigBuilder.class.cast(recipe.create());
            } else if (prop.equals("securityConstraint")) {
                final ObjectRecipe recipe = newRecipe(Meecrowave.SecurityConstaintBuilder.class.getName());
                for (final String nestedConfig : config.stringPropertyNames()) {
                    if (nestedConfig.startsWith("securityConstraint.")) {
                        recipe.setProperty(nestedConfig.substring("securityConstraint.".length()), config.getProperty(nestedConfig));
                    }
                }
                securityConstraints.add(Meecrowave.SecurityConstaintBuilder.class.cast(recipe.create()));
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
        final Meecrowave.ValueTransformers transformers = getExtension(Meecrowave.ValueTransformers.class);
        Class<?> type = instance.getClass();
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
                            } else if (t == long.class) {
                                f.set(instance, Long.parseLong(value));
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

    private Properties mergeProperties(final String resource, final List<Properties> sortedProperties) {
        Properties mergedProperties = new Properties();
        Properties master = null;
        for (final Properties p : sortedProperties)
        {
            if (Boolean.parseBoolean(p.getProperty("configuration.complete", "false"))) {
                if (master != null) {
                    throw new IllegalArgumentException("Ambiguous '" + resource + "', " +
                            "multiple " + resource + " with configuration.complete=true");
                }
                master = p;
            }
            mergedProperties.putAll(p);
        }

        if (master != null) {
            return master;
        }
        return mergedProperties;
    }

    public Configuration loadFrom(final String resource) {
        // load all of those files on the classpath, sorted by ordinal
        Properties config = PropertyLoader.getProperties(resource,
                sortedProperties -> mergeProperties(resource, sortedProperties),
                () -> {});
        if (config == null || config.isEmpty()) {
            final File file = new File(resource);
            if (file.exists()) {
                config = new Properties();
                try (InputStream is = new FileInputStream(file)) {
                    config.load(is);
                }
                catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        if (config != null) {
            loadFromProperties(config);
        }
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!Configuration.class.isInstance(o) /*tolerate builder*/) {
            return false;
        }
        final Configuration that = Configuration.class.cast(o);
        return watcherBouncing == that.watcherBouncing &&
                httpPort == that.httpPort &&
                httpsPort == that.httpsPort &&
                stopPort == that.stopPort &&
                keepServerXmlAsThis == that.keepServerXmlAsThis &&
                quickSession == that.quickSession &&
                skipHttp == that.skipHttp &&
                ssl == that.ssl &&
                http2 == that.http2 &&
                webResourceCached == that.webResourceCached &&
                deleteBaseOnStartup == that.deleteBaseOnStartup &&
                cdiConversation == that.cdiConversation &&
                jaxrsProviderSetup == that.jaxrsProviderSetup &&
                jaxrsAutoActivateBeanValidation == that.jaxrsAutoActivateBeanValidation &&
                jaxrsLogProviders == that.jaxrsLogProviders &&
                jsonpMaxStringLen == that.jsonpMaxStringLen &&
                jsonpMaxReadBufferLen == that.jsonpMaxReadBufferLen &&
                jsonpMaxWriteBufferLen == that.jsonpMaxWriteBufferLen &&
                jsonpSupportsComment == that.jsonpSupportsComment &&
                jsonpPrettify == that.jsonpPrettify &&
                jsonbNulls == that.jsonbNulls &&
                jsonbIJson == that.jsonbIJson &&
                jsonbPrettify == that.jsonbPrettify &&
                loggingGlobalSetup == that.loggingGlobalSetup &&
                tomcatScanning == that.tomcatScanning &&
                tomcatAutoSetup == that.tomcatAutoSetup &&
                tomcatJspDevelopment == that.tomcatJspDevelopment &&
                useShutdownHook == that.useShutdownHook &&
                useTomcatDefaults == that.useTomcatDefaults &&
                tomcatWrapLoader == that.tomcatWrapLoader &&
                tomcatNoJmx == that.tomcatNoJmx &&
                useLog4j2JulLogManager == that.useLog4j2JulLogManager &&
                injectServletContainerInitializer == that.injectServletContainerInitializer &&
                jaxwsSupportIfAvailable == that.jaxwsSupportIfAvailable &&
                initializeClientBus == that.initializeClientBus &&
                antiResourceLocking == that.antiResourceLocking &&
                Objects.equals(pidFile, that.pidFile) &&
                Objects.equals(host, that.host) &&
                Objects.equals(dir, that.dir) &&
                Objects.equals(serverXml, that.serverXml) &&
                Objects.equals(properties, that.properties) &&
                Objects.equals(keystoreFile, that.keystoreFile) &&
                Objects.equals(keystorePass, that.keystorePass) &&
                Objects.equals(keystoreType, that.keystoreType) &&
                Objects.equals(clientAuth, that.clientAuth) &&
                Objects.equals(keyAlias, that.keyAlias) &&
                Objects.equals(sslProtocol, that.sslProtocol) &&
                Objects.equals(webXml, that.webXml) &&
                Objects.equals(loginConfig, that.loginConfig) &&
                Objects.equals(securityConstraints, that.securityConstraints) &&
                Objects.equals(realm, that.realm) &&
                Objects.equals(users, that.users) &&
                Objects.equals(roles, that.roles) &&
                Objects.equals(connectors, that.connectors) &&
                Objects.equals(tempDir, that.tempDir) &&
                Objects.equals(conf, that.conf) &&
                Objects.equals(jaxrsMapping, that.jaxrsMapping) &&
                Objects.equals(jaxrsDefaultProviders, that.jaxrsDefaultProviders) &&
                Objects.equals(jsonpBufferStrategy, that.jsonpBufferStrategy) &&
                Objects.equals(jsonbEncoding, that.jsonbEncoding) &&
                Objects.equals(jsonbBinaryStrategy, that.jsonbBinaryStrategy) &&
                Objects.equals(jsonbNamingStrategy, that.jsonbNamingStrategy) &&
                Objects.equals(jsonbOrderStrategy, that.jsonbOrderStrategy) &&
                Objects.equals(cxfServletParams, that.cxfServletParams) &&
                Objects.equals(tomcatFilter, that.tomcatFilter) &&
                Objects.equals(scanningIncludes, that.scanningIncludes) &&
                Objects.equals(scanningExcludes, that.scanningExcludes) &&
                Objects.equals(scanningPackageIncludes, that.scanningPackageIncludes) &&
                Objects.equals(scanningPackageExcludes, that.scanningPackageExcludes) &&
                Objects.equals(webSessionTimeout, that.webSessionTimeout) &&
                Objects.equals(webSessionCookieConfig, that.webSessionCookieConfig) &&
                Objects.equals(sharedLibraries, that.sharedLibraries) &&
                Objects.equals(tomcatAccessLogPattern, that.tomcatAccessLogPattern) &&
                Objects.equals(meecrowaveProperties, that.meecrowaveProperties) &&
                Objects.equals(defaultSSLHostConfigName, that.defaultSSLHostConfigName) &&
                Objects.equals(extensions, that.extensions) &&
                Objects.equals(instanceCustomizers, that.instanceCustomizers) &&
                Objects.equals(initializers, that.initializers) &&
                Objects.equals(contextConfigurers, that.contextConfigurers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pidFile, watcherBouncing, httpPort, httpsPort, stopPort, host, dir, serverXml, keepServerXmlAsThis, properties, quickSession, skipHttp, ssl, keystoreFile, keystorePass, keystoreType, clientAuth, keyAlias, sslProtocol, webXml, loginConfig, securityConstraints, realm, users, roles, http2, connectors, tempDir, webResourceCached, conf, deleteBaseOnStartup, jaxrsMapping, cdiConversation, jaxrsProviderSetup, jaxrsDefaultProviders, jaxrsAutoActivateBeanValidation, jaxrsLogProviders, jsonpBufferStrategy, jsonpMaxStringLen, jsonpMaxReadBufferLen, jsonpMaxWriteBufferLen, jsonpSupportsComment, jsonpPrettify, jsonbEncoding, jsonbNulls, jsonbIJson, jsonbPrettify, jsonbBinaryStrategy, jsonbNamingStrategy, jsonbOrderStrategy, loggingGlobalSetup, cxfServletParams, tomcatScanning, tomcatAutoSetup, tomcatJspDevelopment, useShutdownHook, tomcatFilter, scanningIncludes, scanningExcludes, scanningPackageIncludes, scanningPackageExcludes, webSessionTimeout, webSessionCookieConfig, useTomcatDefaults, tomcatWrapLoader, tomcatNoJmx, sharedLibraries, useLog4j2JulLogManager, injectServletContainerInitializer, tomcatAccessLogPattern, meecrowaveProperties, jaxwsSupportIfAvailable, defaultSSLHostConfigName, initializeClientBus, extensions, instanceCustomizers, initializers, antiResourceLocking, contextConfigurers);
    }

    private static ObjectRecipe newRecipe(final String clazz) {
        final ObjectRecipe recipe = new ObjectRecipe(clazz);
        recipe.allow(Option.FIELD_INJECTION);
        recipe.allow(Option.PRIVATE_PROPERTIES);
        return recipe;
    }
}
