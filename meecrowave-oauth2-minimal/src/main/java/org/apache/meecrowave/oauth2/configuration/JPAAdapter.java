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

import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.OAuthPermission;
import org.apache.cxf.rs.security.oauth2.common.UserSubject;
import org.apache.cxf.rs.security.oauth2.grants.code.ServerAuthorizationCodeGrant;
import org.apache.cxf.rs.security.oauth2.tokens.bearer.BearerAccessToken;
import org.apache.cxf.rs.security.oauth2.tokens.refresh.RefreshToken;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Properties;

import static java.util.Optional.ofNullable;

public class JPAAdapter {
    // no persistence.xml
    public static EntityManagerFactory createEntityManagerFactory(final OAuth2Options configuration) {
        return Persistence.createEntityManagerFactory("oauth2", new HashMap() {{
            put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
            put("openjpa.MetaDataFactory", "jpa(Types=" +
                    Client.class.getName() + ',' +
                    OAuthPermission.class.getName() + ',' +
                    UserSubject.class.getName() + ',' +
                    ServerAuthorizationCodeGrant.class.getName() + ',' +
                    BearerAccessToken.class.getName() + ',' +
                    RefreshToken.class.getName() + ")");

            // plain connection but not used cause of pooling
            /*
            put("openjpa.ConnectionDriverName", configuration.getJpaDriver());
            put("openjpa.ConnectionURL", configuration.getJpaDriver());
            put("openjpa.ConnectionUsername", configuration.getJpdaDatabaseUsername());
            put("openjpa.ConnectionPassword", configuration.getJpdaDatabasePassword());
            */
            /* cool...but what about pooling?
            put("javax.persistence.jdbc.driver", configuration.getJpaDriver());
            put("javax.persistence.jdbc.url", configuration.getJpaDatabaseUrl());
            put("javax.persistence.jdbc.user", configuration.getJpdaDatabaseUsername());
            put("javax.persistence.jdbc.password", configuration.getJpdaDatabasePassword());
            */
            // pooling support
            put("openjpa.ConnectionDriverName", System.getProperty(
                    "meecrowave.oauth2.datasourcetype", "org.apache.commons.dbcp2.BasicDataSource"));
            put("openjpa.ConnectionProperties",
                    "DriverClassName=" + configuration.getJpaDriver() + ',' +
                            "Url=" + configuration.getJpaDatabaseUrl() + ',' +
                            "Username=" + configuration.getJpdaDatabaseUsername() + ',' +
                            "Password=" + configuration.getJpdaDatabasePassword() + ',' +
                            "MaxActive=" + configuration.getJpaMaxActive() + ',' +
                            "MaxWaitMillis=" + configuration.getJpaMaxWait() + ',' +
                            "MaxIdle=" + configuration.getJpaMaxIdle() + ',' +
                            "TestOnBorrow=" + configuration.isJpaTestOnBorrow() + ',' +
                            "TestOnReturn=" + configuration.isJpaTestOnReturn() + ',' +
                            "TestWhileIdle=" + (configuration.getJpaValidationQuery() != null && !configuration.getJpaValidationQuery().isEmpty()) + ',' +
                            ofNullable(configuration.getJpaValidationQuery()).map(v -> "ValidationQuery=" + v + ',').orElse("") +
                            ofNullable(configuration.getJpaValidationInterval()).filter(it -> it > 0).map(v -> "MinEvictableIdleTimeMillis=" + v).orElse(""));

            ofNullable(configuration.getJpaProperties())
                    .map(p -> new Properties() {{
                        try {
                            load(new StringReader(p));
                        } catch (final IOException e) {
                            throw new IllegalArgumentException(e);
                        }
                    }})
                    .ifPresent(this::putAll);
        }});
    }
}
