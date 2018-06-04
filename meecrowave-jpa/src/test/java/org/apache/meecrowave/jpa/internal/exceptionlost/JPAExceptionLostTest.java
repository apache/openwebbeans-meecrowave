package org.apache.meecrowave.jpa.internal.exceptionlost;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.jpa.api.Jpa;
import org.apache.meecrowave.jpa.api.PersistenceUnitInfoBuilder;
import org.apache.meecrowave.jpa.api.Unit;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.apache.openjpa.persistence.ArgumentException;
import org.h2.Driver;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.sql.DataSource;

public class JPAExceptionLostTest {

    @Rule
    public final TestRule rule = new MeecrowaveRule(
            new Meecrowave.Builder()
                    .randomHttpPort()
                    .includePackages(this.getClass().getPackage().getName()), "")
            .inject(this);


    @Inject
    private BadService badService;

    @Test
    public void test_originalExceptionNotOverriden() {
        try {
            badService.save(new BadUser("test"));
            Assert.fail("should fail");
        } catch (Throwable throwable) {
            Assert.assertTrue("Original Exception has been lost",throwable instanceof ArgumentException);
            Assert.assertTrue(throwable.getMessage().contains("Unenhanced classes must have a public or protected no-args constructor."));
        }

    }

    public static class BadService {

        @Inject
        @Unit(name = "test")
        private EntityManager em;

        @Jpa(transactional = true)
        public BadUser save(final BadUser user) {
            em.persist(user);
            return user;
        }

        @Jpa(transactional = false) // no tx
        public BadUser find(final long id) {
            return em.find(BadUser.class, id);
        }

    }

    @ApplicationScoped
    public static class JpaConfig {
        @Produces
        public PersistenceUnitInfoBuilder unit(final DataSource ds) {
            return new PersistenceUnitInfoBuilder()
                    .setUnitName("test")
                    .setDataSource(ds)
                    .setExcludeUnlistedClasses(true)
                    .addManagedClazz(BadUser.class)
                    .addProperty("openjpa.RuntimeUnenhancedClasses", "supported")
                    .addProperty("openjpa.jdbc.SynchronizeMappings", "buildSchema");
        }

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
    public static class BadUser {
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

        public BadUser(String name) {
            this.name = name;
        }
    }

}
