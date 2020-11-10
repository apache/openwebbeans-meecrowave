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
package org.apache.meecrowave.oauth2.configuration;

import org.apache.cxf.Bus;
import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth2.grants.code.JCacheCodeDataProvider;
import org.apache.cxf.rs.security.oauth2.grants.code.ServerAuthorizationCodeGrant;
import org.apache.cxf.rs.security.oauth2.provider.JCacheOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.tokens.refresh.RefreshToken;

import javax.annotation.PreDestroy;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;
import javax.cache.spi.CachingProvider;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.util.Set;

import static java.util.Optional.ofNullable;
import static org.apache.cxf.jaxrs.utils.ResourceUtils.getClasspathResourceURL;

@ApplicationScoped
public class JCacheConfigurer {
    @Inject
    private Bus bus;

    @Inject
    private BeanManager bm;

    private CachingProvider provider;
    private CacheManager cacheManager;

    public void doSetup(final OAuth2Options options) {
        if (!options.getProvider().startsWith("jcache")) {
            return;
        }

        provider = Caching.getCachingProvider();

        final File file = new File(options.getJcacheConfigUri());
        URI configFileURI = file.isFile() ? file.toURI() : null;
        if (configFileURI == null) {
            try {
                configFileURI = getClasspathResourceURL(options.getJcacheConfigUri(), JCacheOAuthDataProvider.class, bus).toURI();
            } catch (final Exception ex) {
                configFileURI = provider.getDefaultURI();
            }
        }

        cacheManager = provider.getCacheManager(configFileURI, Thread.currentThread().getContextClassLoader());
        try {
            cacheManager.createCache(
                    JCacheOAuthDataProvider.CLIENT_CACHE_KEY,
                    configure(new MutableConfiguration<String, Client>().setTypes(String.class, Client.class), options));
            if (!options.isJcacheStoreJwtKeyOnly()/* && options.isUseJwtFormatForAccessTokens()*/) {
                cacheManager.createCache(
                        JCacheOAuthDataProvider.ACCESS_TOKEN_CACHE_KEY,
                        configure(new MutableConfiguration<String, ServerAccessToken>().setTypes(String.class, ServerAccessToken.class), options));
            } else {
                cacheManager.createCache(
                        JCacheOAuthDataProvider.ACCESS_TOKEN_CACHE_KEY,
                        configure(new MutableConfiguration<String, String>().setTypes(String.class, String.class), options));
            }
            cacheManager.createCache(
                    JCacheOAuthDataProvider.REFRESH_TOKEN_CACHE_KEY,
                    configure(new MutableConfiguration<String, RefreshToken>().setTypes(String.class, RefreshToken.class), options));
            if (options.isAuthorizationCodeSupport()) {
                cacheManager.createCache(
                        JCacheCodeDataProvider.CODE_GRANT_CACHE_KEY,
                        configure(new MutableConfiguration<String, ServerAuthorizationCodeGrant>().setTypes(String.class, ServerAuthorizationCodeGrant.class), options));
            }
        } catch (final CacheException ce) {
            // already created
        }
    }

    private <T> MutableConfiguration<String, T> configure(final MutableConfiguration<String, T> configuration, final OAuth2Options opts) {
        ofNullable(opts.getJcacheLoader())
                .map(n -> lookup(CacheLoader.class, n))
                .ifPresent(l -> configuration.setCacheLoaderFactory(new FactoryBuilder.SingletonFactory<CacheLoader<String, T>>(l)));
        ofNullable(opts.getJcacheWriter())
                .map(n -> lookup(CacheWriter.class, n))
                .ifPresent(w -> configuration.setCacheWriterFactory(new FactoryBuilder.SingletonFactory<CacheWriter<String, T>>(w)));
        return configuration
                .setStoreByValue(opts.isJcacheStoreValue())
                .setStatisticsEnabled(opts.isJcacheStatistics())
                .setManagementEnabled(opts.isJcacheJmx());
    }

    private <U> U lookup(final Class<U> type, final String name) {
        final Set<Bean<?>> nameSet = bm.getBeans(name);
        Bean<?> bean = bm.resolve(nameSet);
        if (bean == null) {
            try {
                final Class<?> beanType = Thread.currentThread().getContextClassLoader().loadClass(name.trim());
                bean = bm.resolve(bm.getBeans(beanType));
                if (bean == null) {
                    return type.cast(beanType.newInstance());
                }
            } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return type.cast(bm.getReference(bean, type, bm.createCreationalContext(null)));
    }

    @PreDestroy
    private void destroy() {
        cacheManager.close();
        provider.close();
    }
}
