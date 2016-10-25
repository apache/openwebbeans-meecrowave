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
package org.apache.microwave.arquillian;

import org.apache.catalina.Realm;
import org.apache.microwave.Microwave;
import org.apache.xbean.recipe.ObjectRecipe;
import org.jboss.arquillian.config.descriptor.api.Multiline;
import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class MicrowaveConfiguration implements ContainerConfiguration {
    private int httpPort = -1;
    private int httpsPort = 8443;
    private int stopPort = -1;
    private String host = "localhost";
    private String dir;
    private File serverXml;
    private boolean keepServerXmlAsThis;
    private Properties properties = new Properties();
    private boolean quickSession = true;
    private boolean skipHttp;
    private boolean ssl;
    private String keystoreFile;
    private String keystorePass;
    private String keystoreType = "JKS";
    private String clientAuth;
    private String keyAlias;
    private String sslProtocol;
    private String webXml;
    private boolean http2;
    private String tempDir = new File(System.getProperty("java.io.tmpdir"), "microwave_" + System.nanoTime()).getAbsolutePath();
    private boolean webResourceCached = true;
    private String conf;
    private boolean deleteBaseOnStartup = true;
    private String jaxrsMapping = "/*";
    private boolean cdiConversation;
    private boolean jaxrsProviderSetup = true;
    private boolean loggingGlobalSetup = true;
    private String users;
    private String roles;
    private String cxfServletParams;
    private String loginConfig;
    private String securityConstraints;
    private String realm;
    private String tomcatFilter;
    private boolean tomcatScanning = true;
    private boolean tomcatAutoSetup = true;
    private boolean useShutdownHook = false /*arquillian*/;
    private boolean useTomcatDefaults = true;

    @Override
    public void validate() throws ConfigurationException {
        // no-op
    }

    Microwave.Builder toMicrowaveConfiguration() {
        final Microwave.Builder builder = new Microwave.Builder();
        for (final Field field : MicrowaveConfiguration.class.getDeclaredFields()) {
            final String name = field.getName();
            if ("users".equals(name) || "roles".equals(name) || "cxfServletParams".equals(name)
                    || "loginConfig".equals(name) || "securityConstraints".equals(name)
                    || "realm".equals(name)) {
                continue; // specific syntax
            }

            try {
                final Field configField = Microwave.Builder.class.getDeclaredField(field.getName());
                if (!configField.getType().equals(field.getType())) {
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
                    configField.set(builder, value);
                }
            } catch (final NoSuchFieldException nsfe) {
                // ignored
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }

        if (httpPort < 0) {
            builder.randomHttpPort();
        }

        // for Map use properties
        if (users != null) {
            final Properties properties = new Properties() {{
                try {
                    load(new ByteArrayInputStream(users.getBytes(StandardCharsets.UTF_8)));
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }};
            builder.setUsers(properties.stringPropertyNames().stream().collect(toMap(identity(), properties::getProperty)));
        }
        if (roles != null) {
            final Properties properties = new Properties() {{
                try {
                    load(new ByteArrayInputStream(roles.getBytes(StandardCharsets.UTF_8)));
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }};
            builder.setRoles(properties.stringPropertyNames().stream().collect(toMap(identity(), properties::getProperty)));
        }
        if (cxfServletParams != null) {
            final Properties properties = new Properties() {{
                try {
                    load(new ByteArrayInputStream(cxfServletParams.getBytes(StandardCharsets.UTF_8)));
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }};
            builder.setCxfServletParams(properties.stringPropertyNames().stream().collect(toMap(identity(), properties::getProperty)));
        }

        // for other not simple type use the Cli syntax
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (realm != null) {
            try {
                int end = realm.indexOf(':');
                if (end < 0) {
                    builder.setRealm(Realm.class.cast(loader.loadClass(realm).newInstance()));
                } else {
                    final ObjectRecipe recipe = new ObjectRecipe(realm.substring(0, end));
                    Stream.of(realm.substring(end + 1, realm.length()).split(";"))
                            .map(v -> v.split("="))
                            .forEach(v -> recipe.setProperty(v[0], v[1]));
                    builder.setRealm(Realm.class.cast(recipe.create(loader)));
                }
            } catch (final Exception cnfe) {
                throw new IllegalArgumentException(realm);
            }
        }
        if (securityConstraints != null) {
            builder.setSecurityConstraints(Stream.of(securityConstraints.split("|"))
                    .map(item -> {
                        try {
                            final ObjectRecipe recipe = new ObjectRecipe(Microwave.SecurityConstaintBuilder.class);
                            Stream.of(item.split(";"))
                                    .map(v -> v.split("="))
                                    .forEach(v -> recipe.setProperty(v[0], v[1]));
                            return Microwave.SecurityConstaintBuilder.class.cast(recipe.create(loader));
                        } catch (final Exception cnfe) {
                            throw new IllegalArgumentException(securityConstraints);
                        }
                    }).collect(toList()));
        }
        if (loginConfig != null) {
            try {
                final ObjectRecipe recipe = new ObjectRecipe(Microwave.LoginConfigBuilder.class);
                Stream.of(loginConfig.split(";"))
                        .map(v -> v.split("="))
                        .forEach(v -> recipe.setProperty(v[0], v[1]));
                builder.setLoginConfig(Microwave.LoginConfigBuilder.class.cast(recipe.create(loader)));
            } catch (final Exception cnfe) {
                throw new IllegalArgumentException(loginConfig);
            }
        }

        return builder;
    }

    public String getCxfServletParams() {
        return cxfServletParams;
    }

    public void setCxfServletParams(final String cxfServletParams) {
        this.cxfServletParams = cxfServletParams;
    }

    public boolean isTomcatScanning() {
        return tomcatScanning;
    }

    public void setTomcatScanning(final boolean tomcatScanning) {
        this.tomcatScanning = tomcatScanning;
    }

    public boolean isTomcatAutoSetup() {
        return tomcatAutoSetup;
    }

    public void setTomcatAutoSetup(final boolean tomcatAutoSetup) {
        this.tomcatAutoSetup = tomcatAutoSetup;
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

    public void setSkipHttp(boolean skipHttp) {
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

    public String getUsers() {
        return users;
    }

    @Multiline
    public void setUsers(final String users) {
        this.users = users;
    }

    public String getRoles() {
        return roles;
    }

    @Multiline
    public void setRoles(final String roles) {
        this.roles = roles;
    }

    public String getLoginConfig() {
        return loginConfig;
    }

    public void setLoginConfig(final String loginConfig) {
        this.loginConfig = loginConfig;
    }

    public String getSecurityConstraints() {
        return securityConstraints;
    }

    public void setSecurityConstraints(final String securityConstraints) {
        this.securityConstraints = securityConstraints;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(final String realm) {
        this.realm = realm;
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
}
