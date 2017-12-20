package org.apache.meecrowave.jpa.internal;

import org.apache.meecrowave.jpa.api.EntityManagerScoped;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.PassivationCapable;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.SynchronizationType;
import javax.persistence.ValidationMode;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

@Vetoed
public class EntityManagerBean implements Bean<EntityManager>, PassivationCapable {
    private final Set<Type> types = new HashSet<>(singletonList(EntityManager.class));
    private final Set<Annotation> qualifiers = new HashSet<>();
    private final EntityManagerContext entityManagerContext;
    private final SynchronizationType synchronization;
    private final String id;
    private Supplier<EntityManager> instanceFactory;

    EntityManagerBean(final EntityManagerContext context, final String name, final SynchronizationType synchronization) {
        this.entityManagerContext = context;
        this.qualifiers.addAll(asList(new UnitLiteral(name, synchronization), AnyLiteral.INSTANCE));
        this.id = "meecrowave::jpa::entitymanager::" + name + "/" + synchronization.name();
        this.synchronization = synchronization;
    }

    void init(final PersistenceUnitInfo info, final BeanManager bm) {
        final PersistenceProvider provider;
        try {
            provider = PersistenceProvider.class.cast(
                    Thread.currentThread().getContextClassLoader().loadClass(info.getPersistenceProviderClassName()).newInstance());
        } catch (final InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Bad provider: " + info.getPersistenceProviderClassName());
        }
        final EntityManagerFactory factory = provider.createContainerEntityManagerFactory(info, new HashMap() {{
            put("javax.persistence.bean.manager", bm);
            if (ValidationMode.NONE != info.getValidationMode()) {
                ofNullable(findValidatorFactory(bm)).ifPresent(factory -> put("javax.persistence.validation.factory", factory));
            }
        }});
        instanceFactory = synchronization == SynchronizationType.SYNCHRONIZED ? factory::createEntityManager : () -> factory.createEntityManager(synchronization);
    }

    private Object findValidatorFactory(final BeanManager bm) {
        try {
            final Class<?> type = Thread.currentThread().getContextClassLoader().loadClass("javax.validation.ValidatorFactory");
            final Bean<?> bean = bm.resolve(bm.getBeans(type));
            if (bean == null || !bm.isNormalScope(bean.getScope())) {
                return null;
            }
            return bm.getReference(bean, type, bm.createCreationalContext(null));
        } catch (final NoClassDefFoundError | ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public EntityManager create(final CreationalContext<EntityManager> context) {
        final EntityManager entityManager = instanceFactory.get();
        if (entityManagerContext.isTransactional()) {
            entityManager.getTransaction().begin();
        }
        return entityManager;
    }

    @Override
    public void destroy(final EntityManager instance, final CreationalContext<EntityManager> context) {
        try {
            if (entityManagerContext.isTransactional()) {
                if (entityManagerContext.hasFailed()) {
                    instance.getTransaction().rollback();
                } else {
                    instance.getTransaction().commit();
                }
            }
        } finally {
            instance.close();
        }
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return emptySet();
    }

    @Override
    public Class<?> getBeanClass() {
        return EntityManager.class;
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public Set<Type> getTypes() {
        return types;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return qualifiers;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return EntityManagerScoped.class;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public String getId() {
        return id;
    }
}
