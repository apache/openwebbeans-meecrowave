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
package org.apache.meecrowave.jolokia;

import io.hawt.HawtioContextListener;
import io.hawt.web.AuthenticationFilter;
import io.hawt.web.CORSFilter;
import io.hawt.web.CacheHeadersFilter;
import io.hawt.web.ContextFormatterServlet;
import io.hawt.web.ExportContextServlet;
import io.hawt.web.GitServlet;
import io.hawt.web.JavaDocServlet;
import io.hawt.web.JolokiaConfiguredAgentServlet;
import io.hawt.web.LoginServlet;
import io.hawt.web.LogoutServlet;
import io.hawt.web.PluginServlet;
import io.hawt.web.ProxyServlet;
import io.hawt.web.RedirectFilter;
import io.hawt.web.SessionExpiryFilter;
import io.hawt.web.UploadServlet;
import io.hawt.web.UserServlet;
import io.hawt.web.XFrameOptionsFilter;
import io.hawt.web.keycloak.KeycloakServlet;
import org.apache.catalina.Globals;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.webresources.JarResourceSet;
import org.apache.commons.fileupload.servlet.FileCleanerCleanup;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.runner.Cli;
import org.apache.meecrowave.runner.cli.CliOption;

import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.Principal;
import java.security.Security;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Optional.ofNullable;

public class HawtioInitializer implements ServletContainerInitializer {
    @Override
    public void onStartup(final Set<Class<?>> set, final ServletContext servletContext) throws ServletException {
        try {
            servletContext.getClassLoader().loadClass("io.hawt.web.UserServlet");
        } catch (final ClassNotFoundException e) {
            servletContext.log("Hawt.io not available, skipping");
            return;
        }
        Delegate.doSetup(servletContext);
    }

    private static class Delegate {
        private Delegate() {
            // no-op
        }

        private static void doSetup(final ServletContext servletContext) {
            final Meecrowave.Builder config = Meecrowave.Builder.class.cast(servletContext.getAttribute("meecrowave.configuration"));
            final HawtioConfiguration configuration = config.getExtension(HawtioConfiguration.class);
            final JolokiaInitializer.JolokiaConfiguration jolokia = config.getExtension(JolokiaInitializer.JolokiaConfiguration.class);
            if (!configuration.isActive()) {
                return;
            }

            doSetupJaas(configuration, servletContext);

            final String mapping = ofNullable(configuration.getMapping()).orElse("/hawtio/");

            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-user", UserServlet.class);
                servlet.addMapping(mapping + "user/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-proxy", ProxyServlet.class);
                servlet.addMapping(mapping + "proxy/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-file-upload", UploadServlet.class);
                servlet.addMapping(mapping + "file-upload/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-login", LoginServlet.class);
                servlet.addMapping(mapping + "auth/login/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-logout", LogoutServlet.class);
                servlet.addMapping(mapping + "auth/logout/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-keycloak", KeycloakServlet.class);
                servlet.addMapping(mapping + "keycloak/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-exportContext", ExportContextServlet.class);
                servlet.addMapping(mapping + "exportContext/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-javadoc", JavaDocServlet.class);
                servlet.addMapping(mapping + "javadoc/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-plugin", PluginServlet.class);
                servlet.addMapping(mapping + "plugin/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-contextFormatter", ContextFormatterServlet.class);
                servlet.addMapping(mapping + "contextFormatter/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-git", GitServlet.class);
                servlet.addMapping(mapping + "git/*");
            }
            {
                final ServletRegistration.Dynamic servlet = servletContext.addServlet("hawtio-jolokia", JolokiaConfiguredAgentServlet.class);
                servlet.setInitParameter("mbeanQualifier", "qualifier=hawtio");
                servlet.setInitParameter("includeStackTrace", "false");
                servlet.setInitParameter("restrictorClass", "io.hawt.web.RBACRestrictor");
                servlet.addMapping(mapping + "jolokia/*");
            }
            {
                final WebResourceRoot root = WebResourceRoot.class.cast(servletContext.getAttribute(Globals.RESOURCES_ATTR));
                final String url = servletContext.getClassLoader().getResource("static/hawtio").toExternalForm();
                final int sep = url.lastIndexOf("!/");
                String jar = url;
                if (jar.startsWith("jar:")) {
                    jar = url.substring("jar:".length(), sep);
                }
                if (jar.startsWith("file:")) {
                    jar = jar.substring("file:".length());
                }
                root.addPostResources(new JarResourceSet(root, mapping, jar, "/static/hawtio/"));
            }

            servletContext.addListener(HawtioContextListener.class);
            servletContext.addListener(FileCleanerCleanup.class);

            {
                final FilterRegistration.Dynamic filter = servletContext.addFilter("hawtio-redirect", RedirectFilter.class);
                filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, mapping + "*");
            }
            {
                final FilterRegistration.Dynamic filter = servletContext.addFilter("hawtio-cacheheaders", CacheHeadersFilter.class);
                filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, mapping + "*");
            }
            {
                final FilterRegistration.Dynamic filter = servletContext.addFilter("hawtio-cors", CORSFilter.class);
                filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, mapping + "*");
            }
            {
                final FilterRegistration.Dynamic filter = servletContext.addFilter("hawtio-xframe", XFrameOptionsFilter.class);
                filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, mapping + "*");
            }
            {
                final FilterRegistration.Dynamic filter = servletContext.addFilter("hawtio-sessionexpiry", SessionExpiryFilter.class);
                filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, mapping + "*");
            }
            {
                final FilterRegistration.Dynamic filter = servletContext.addFilter("hawtio-authentication", AuthenticationFilter.class);
                filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false,
                        mapping + "auth/*", mapping + "upload/*", mapping + "javadoc/*", mapping + "jolokia/*",
                        ofNullable(jolokia.getMapping()).orElse("/jolokia/*"));
            }

            servletContext.log("Installed Hawt.io on " + mapping);
        }

        private static void doSetupJaas(final HawtioConfiguration configuration, final ServletContext servletContext) {
            if (!configuration.isJaas()) {
                return;
            }
            final String key = "login.configuration.provider";
            final String val = Security.getProperty(key);
            Security.setProperty(key, EmbeddedConfiguration.class.getName());
            servletContext.addListener(new ServletContextListener() {
                @Override
                public void contextDestroyed(final ServletContextEvent sce) {
                    Security.setProperty(key, val == null ? "sun.security.provider.ConfigFile" : val);
                }
            });
        }
    }

    public static class EmbeddedConfiguration extends Configuration {
        private final AppConfigurationEntry[] entries = new AppConfigurationEntry[]{
                new AppConfigurationEntry(EmbeddedLoginModule.class.getName(), AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, new HashMap<>())
        };

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(final String name) {
            return "karaf".equals(name) ? entries : null;
        }
    }

    public static class EmbeddedLoginModule implements LoginModule {
        private Subject subject;
        private CallbackHandler callbackHandler;

        private Principal principal;

        @Override
        public void initialize(final Subject subject, final CallbackHandler callbackHandler,
                               final Map<String, ?> sharedState, final Map<String, ?> options) {
            this.subject = subject;
            this.callbackHandler = callbackHandler;
        }

        @Override
        public boolean login() throws LoginException {
            final Callback[] callbacks = new Callback[2];
            callbacks[0] = new NameCallback("Username: ");
            callbacks[1] = new PasswordCallback("Password: ", false);
            try {
                callbackHandler.handle(callbacks);
            } catch (IOException | UnsupportedCallbackException e) {
                throw new LoginException(e.toString());
            }

            final BeanManager beanManager = CDI.current().getBeanManager();
            final HttpServletRequest request =
                    HttpServletRequest.class.cast(
                            beanManager.getReference(
                                    beanManager.resolve(beanManager.getBeans(HttpServletRequest.class)),
                                    HttpServletRequest.class,
                                    beanManager.createCreationalContext(null)));

            try {
                request.login(NameCallback.class.cast(callbacks[0]).getName(), new String(PasswordCallback.class.cast(callbacks[1]).getPassword()));
            } catch (final ServletException e) {
                throw new LoginException(e.getMessage());
            }

            principal = request.getUserPrincipal();

            return principal != null;
        }

        @Override
        public boolean commit() throws LoginException {
            if (!subject.getPrincipals().contains(principal)) {
                subject.getPrincipals().add(principal);
                if (GenericPrincipal.class.isInstance(principal)) {
                    final String roles[] = GenericPrincipal.class.cast(principal).getRoles();
                    for (final String role : roles) {
                        subject.getPrincipals().add(new GenericPrincipal(role, null, null));
                    }

                }
            }
            return true;
        }

        @Override
        public boolean abort() throws LoginException {
            principal = null;
            return true;
        }

        @Override
        public boolean logout() throws LoginException {
            subject.getPrincipals().remove(principal);
            return true;
        }
    }

    public static class HawtioConfiguration implements Cli.Options {
        @CliOption(name = "hawtio-mapping", description = "Hawt.io base endpoint")
        private String mapping;

        @CliOption(name = "hawtio-active", description = "Should Hawt.io be deployed if present")
        private boolean active = true;

        @CliOption(name = "hawtio-meecrowave-jaas", description = "Should meecrowave setup jaas")
        private boolean jaas = true;

        public boolean isJaas() {
            return jaas;
        }

        public void setJaas(final boolean jaas) {
            this.jaas = jaas;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(final boolean active) {
            this.active = active;
        }

        public String getMapping() {
            return mapping;
        }

        public void setMapping(final String mapping) {
            this.mapping = mapping;
        }
    }
}
