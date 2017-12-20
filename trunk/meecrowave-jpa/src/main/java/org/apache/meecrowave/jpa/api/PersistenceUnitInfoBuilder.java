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
package org.apache.meecrowave.jpa.api;

import javax.enterprise.inject.Vetoed;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static javax.persistence.spi.PersistenceUnitTransactionType.RESOURCE_LOCAL;

@Vetoed
public class PersistenceUnitInfoBuilder {
    private String unitName;
    private String providerClass;
    private DataSource dataSource;
    private DataSource jtaDataSource;
    private List<String> mappingFiles = emptyList();
    private List<URL> jarFiles = emptyList();
    private URL rootUrl;
    private List<String> managedClasses = new ArrayList<>();
    private boolean excludeUnlistedClasses;
    private SharedCacheMode sharedCacheMode = SharedCacheMode.UNSPECIFIED;
    private ValidationMode validationMode = ValidationMode.AUTO;
    private Properties properties = new Properties();
    private String version = "2.0";
    private ClassLoader loader = Thread.currentThread().getContextClassLoader();
    private PersistenceUnitTransactionType transactionType = RESOURCE_LOCAL;

    public PersistenceUnitTransactionType getTransactionType() {
        return transactionType;
    }

    public PersistenceUnitInfoBuilder setTransactionType(final PersistenceUnitTransactionType transactionType) {
        this.transactionType = transactionType;
        return this;
    }

    public String getUnitName() {
        return unitName;
    }

    public PersistenceUnitInfoBuilder setUnitName(final String unitName) {
        this.unitName = unitName;
        return this;
    }

    public String getProviderClass() {
        return providerClass;
    }

    public PersistenceUnitInfoBuilder setProviderClass(final String providerClass) {
        this.providerClass = providerClass;
        return this;
    }

    public DataSource getJtaDataSource() {
        return jtaDataSource;
    }

    public PersistenceUnitInfoBuilder setJtaDataSource(final DataSource jtaDataSource) {
        this.jtaDataSource = jtaDataSource;
        return this;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public PersistenceUnitInfoBuilder setDataSource(final DataSource dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    public List<String> getMappingFiles() {
        return mappingFiles;
    }

    public PersistenceUnitInfoBuilder setMappingFiles(final List<String> mappingFiles) {
        this.mappingFiles = mappingFiles;
        return this;
    }

    public List<URL> getJarFiles() {
        return jarFiles;
    }

    public PersistenceUnitInfoBuilder setJarFiles(final List<URL> jarFiles) {
        this.jarFiles = jarFiles;
        return this;
    }

    public URL getRootUrl() {
        return rootUrl;
    }

    public PersistenceUnitInfoBuilder setRootUrl(final URL rootUrl) {
        this.rootUrl = rootUrl;
        return this;
    }

    public List<String> getManagedClasses() {
        return managedClasses;
    }

    public PersistenceUnitInfoBuilder addManagedClazz(final Class<?> clazz) {
        managedClasses.add(clazz.getName());
        return this;
    }

    public PersistenceUnitInfoBuilder setManagedClassNames(final List<String> managedClasses) {
        this.managedClasses = managedClasses;
        return this;
    }

    public PersistenceUnitInfoBuilder setManagedClasses(final List<Class<?>> managedClasses) {
        this.managedClasses = managedClasses.stream().map(Class::getName).collect(toList());
        return this;
    }

    public boolean isExcludeUnlistedClasses() {
        return excludeUnlistedClasses;
    }

    public PersistenceUnitInfoBuilder setExcludeUnlistedClasses(final boolean excludeUnlistedClasses) {
        this.excludeUnlistedClasses = excludeUnlistedClasses;
        return this;
    }

    public SharedCacheMode getSharedCacheMode() {
        return sharedCacheMode;
    }

    public PersistenceUnitInfoBuilder setSharedCacheMode(final SharedCacheMode sharedCacheMode) {
        this.sharedCacheMode = sharedCacheMode;
        return this;
    }

    public ValidationMode getValidationMode() {
        return validationMode;
    }

    public PersistenceUnitInfoBuilder setValidationMode(final ValidationMode validationMode) {
        this.validationMode = validationMode;
        return this;
    }

    public Properties getProperties() {
        return properties;
    }

    public PersistenceUnitInfoBuilder addProperty(final String key, final String value) {
        if (properties == null) {
            properties = new Properties();
        }
        properties.setProperty(key, value);
        return this;
    }

    public PersistenceUnitInfoBuilder setProperties(final Properties properties) {
        this.properties = properties;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public PersistenceUnitInfoBuilder setVersion(final String version) {
        this.version = version;
        return this;
    }

    public ClassLoader getLoader() {
        return loader;
    }

    public PersistenceUnitInfoBuilder setLoader(final ClassLoader loader) {
        this.loader = loader;
        return this;
    }

    public PersistenceUnitInfo toInfo() {
        if (providerClass == null) {
            providerClass = ServiceLoader.load(PersistenceProvider.class).iterator().next().getClass().getName();
        }
        requireNonNull(dataSource, "datasource not provided");
        return new PersistenceUnitInfo() {
            @Override
            public String getPersistenceUnitName() {
                return unitName;
            }

            @Override
            public String getPersistenceProviderClassName() {
                return providerClass;
            }

            @Override
            public PersistenceUnitTransactionType getTransactionType() {
                return transactionType;
            }

            @Override
            public DataSource getJtaDataSource() {
                return jtaDataSource;
            }

            @Override
            public DataSource getNonJtaDataSource() {
                return dataSource;
            }

            @Override
            public List<String> getMappingFileNames() {
                return mappingFiles;
            }

            @Override
            public List<URL> getJarFileUrls() {
                return jarFiles;
            }

            @Override
            public URL getPersistenceUnitRootUrl() {
                return rootUrl;
            }

            @Override
            public List<String> getManagedClassNames() {
                return managedClasses;
            }

            @Override
            public boolean excludeUnlistedClasses() {
                return excludeUnlistedClasses;
            }

            @Override
            public SharedCacheMode getSharedCacheMode() {
                return sharedCacheMode;
            }

            @Override
            public ValidationMode getValidationMode() {
                return validationMode;
            }

            @Override
            public Properties getProperties() {
                return properties;
            }

            @Override
            public String getPersistenceXMLSchemaVersion() {
                return version;
            }

            @Override
            public ClassLoader getClassLoader() {
                return loader;
            }

            @Override
            public void addTransformer(final ClassTransformer transformer) {
                // no-op: not supported
            }

            @Override
            public ClassLoader getNewTempClassLoader() {
                return new URLClassLoader(new URL[0], Thread.currentThread().getContextClassLoader());
            }
        };
    }
}
