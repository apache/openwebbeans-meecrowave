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
package org.app;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.meecrowave.jpa.api.Jpa;
import org.apache.meecrowave.jpa.api.Unit;
import org.h2.Driver;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.sql.DataSource;

@ApplicationScoped
public class JPADao {
    @Inject
    @Unit(name = "test")
    private EntityManager em;

    // tx by default
    public User save(final User user) {
        em.persist(user);
        return user;
    }

    @Jpa(transactional = false) // no tx
    public User find(final long id) {
        return em.find(User.class, id);
    }

    @ApplicationScoped
    public static class JpaConfig {
        @Produces
        @ApplicationScoped
        public DataSource dataSource() {
            final BasicDataSource source = new BasicDataSource();
            source.setDriver(new Driver());
            source.setUrl("jdbc:h2:mem:jpaextensiontest");
            return source;
        }
    }

    @Entity
    @Dependent
    public static class User {
        @Id
        @GeneratedValue
        private long id;

        private String name;

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }

}
