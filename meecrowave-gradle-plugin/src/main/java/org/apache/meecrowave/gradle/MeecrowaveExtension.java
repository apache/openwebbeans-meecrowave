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

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

public class MeecrowaveExtension {
    private boolean skipMavenCentral;
    private String context = "";
    private File webapp;

    private int watcherBouncing;
    private int httpPort = 8080;
    private int httpsPort = 8443;
    private int stopPort = -1;
    private String host = "localhost";
    private String dir;
    private File serverXml;
    private boolean keepServerXmlAsThis;
    private Map<String, String> properties;
    private boolean quickSession;
    private boolean skipHttp;
    private boolean ssl;
    private boolean initializeClientBus = true;
    private String keystoreFile;
    private String keystorePass;
    private String keystoreType;
    private String clientAuth;
    private String keyAlias;
    private String sslProtocol;
    private String webXml;
    private String loginConfig;
    private Collection<String> securityConstraints = new LinkedList<>();
    private Map<String, String> users;
    private Map<String, String> roles;
    private Map<String, String> cxfServletParams;
    private boolean http2;
    private boolean tomcatScanning = true;
    private boolean tomcatAutoSetup = true;
    private String tempDir;
    private boolean webResourceCached = true;
    private String conf;
    private boolean deleteBaseOnStartup = true;
    private String jaxrsMapping;
    private boolean cdiConversation;
    private boolean skip;
    private boolean jaxrsProviderSetup = true;
    private boolean loggingGlobalSetup = true;
    private boolean useShutdownHook = true;
    private String tomcatFilter;
    private boolean useTomcatDefaults = true;
    private boolean jaxrsLogProviders = false;
    private boolean tomcatWrapLoader = false;
    private String jaxrsDefaultProviders;
    private String sharedLibraries;
    private boolean useLog4j2JulLogManager = true;
    private String jsonpBufferStrategy = "QUEUE";
    private int jsonpMaxStringLen = 10 * 1024 * 1024;
    private int jsonpMaxReadBufferLen = 64 * 1024;
    private int jsonpMaxWriteBufferLen = 64 * 1024;
    private boolean jsonpSupportsComment = false;
    private boolean jsonpPrettify = false;
    private String jsonbEncoding = "UTF-8";
    private boolean jsonbNulls = false;
    private boolean jsonbIJson = false;
    private boolean jsonbPrettify = false;
    private String jsonbBinaryStrategy;
    private String jsonbNamingStrategy;
    private String jsonbOrderStrategy;
    private String scanningIncludes;
    private String scanningExcludes;
    private String scanningPackageIncludes;
    private String scanningPackageExcludes;
    private boolean tomcatNoJmx = true;
    private boolean injectServletContainerInitializer = true;
    private String tomcatAccessLogPattern;
    private boolean jaxrsAutoActivateBeanValidation = true;
    private String meecrowaveProperties = "meecrowave.properties";
    private boolean jaxwsSupportIfAvailable = true;
    private String defaultSSLHostConfigName;
    private Integer webSessionTimeout;
    private String webSessionCookieConfig;
    private boolean tomcatJspDevelopment;
    private boolean antiResourceLocking;

    public boolean isAntiResourceLocking() {
        return antiResourceLocking;
    }

    public void setAntiResourceLocking(final boolean antiResourceLocking) {
        this.antiResourceLocking = antiResourceLocking;
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

    public boolean isJaxwsSupportIfAvailable() {
        return jaxwsSupportIfAvailable;
    }

    public void setJaxwsSupportIfAvailable(final boolean jaxwsSupportIfAvailable) {
        this.jaxwsSupportIfAvailable = jaxwsSupportIfAvailable;
    }

    public String getMeecrowaveProperties() {
        return meecrowaveProperties;
    }

    public void setMeecrowaveProperties(final String meecrowaveProperties) {
        this.meecrowaveProperties = meecrowaveProperties;
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

    public boolean isUseLog4j2JulLogManager() {
        return useLog4j2JulLogManager;
    }

    public void setUseLog4j2JulLogManager(final boolean useLog4j2JulLogManager) {
        this.useLog4j2JulLogManager = useLog4j2JulLogManager;
    }

    public String getSharedLibraries() {
        return sharedLibraries;
    }

    public void setSharedLibraries(final String sharedLibraries) {
        this.sharedLibraries = sharedLibraries;
    }

    public String getJaxrsDefaultProviders() {
        return jaxrsDefaultProviders;
    }

    public void setJaxrsDefaultProviders(final String jaxrsDefaultProviders) {
        this.jaxrsDefaultProviders = jaxrsDefaultProviders;
    }

    public boolean isTomcatWrapLoader() {
        return tomcatWrapLoader;
    }

    public void setTomcatWrapLoader(final boolean tomcatWrapLoader) {
        this.tomcatWrapLoader = tomcatWrapLoader;
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

    public boolean isUseShutdownHook() {
        return useShutdownHook;
    }

    public void setUseShutdownHook(final boolean useShutdownHook) {
        this.useShutdownHook = useShutdownHook;
    }

    public Map<String, String> getCxfServletParams() {
        return cxfServletParams;
    }

    public void setCxfServletParams(final Map<String, String> cxfServletParams) {
        this.cxfServletParams = cxfServletParams;
    }

    public boolean isTomcatScanning() {
        return tomcatScanning;
    }

    public void setTomcatScanning(final boolean tomcatScanning) {
        this.tomcatScanning = tomcatScanning;
    }

    public boolean isLoggingGlobalSetup() {
        return loggingGlobalSetup;
    }

    public void setLoggingGlobalSetup(final boolean loggingGlobalSetup) {
        this.loggingGlobalSetup = loggingGlobalSetup;
    }

    public boolean isJaxrsProviderSetup() {
        return jaxrsProviderSetup;
    }

    public void setJaxrsProviderSetup(final boolean jaxrsProviderSetup) {
        this.jaxrsProviderSetup = jaxrsProviderSetup;
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

    public boolean isSkipMavenCentral() {
        return skipMavenCentral;
    }

    public void setSkipMavenCentral(final boolean skipMavenCentral) {
        this.skipMavenCentral = skipMavenCentral;
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

    public boolean isTomcatAutoSetup() {
        return tomcatAutoSetup;
    }

    public void setTomcatAutoSetup(final boolean tomcatAutoSetup) {
        this.tomcatAutoSetup = tomcatAutoSetup;
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

    public String getScanningPackageIncludes() {
        return scanningPackageIncludes;
    }

    public void setScanningPackageIncludes(final String scanningPackageIncludes) {
        this.scanningPackageIncludes = scanningPackageIncludes;
    }

    public String getScanningPackageExcludes() {
        return scanningPackageExcludes;
    }

    public void setScanningPackageExcludes(String scanningPackageExcludes) {
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

    public int getWatcherBouncing() {
        return watcherBouncing;
    }

    public void setWatcherBouncing(final int watcherBouncing) {
        this.watcherBouncing = watcherBouncing;
    }

    public String getDefaultSSLHostConfigName() {
        return defaultSSLHostConfigName;
    }

    public void setDefaultSSLHostConfigName(String defaultSSLHostConfigName) {
        this.defaultSSLHostConfigName = defaultSSLHostConfigName;
    }

    public boolean isInitializeClientBus() {
        return initializeClientBus;
    }

    public void setInitializeClientBus(final boolean initializeClientBus) {
        this.initializeClientBus = initializeClientBus;
    }
}
