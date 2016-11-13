package org.apache.meecrowave.jpa.internal;

import org.apache.meecrowave.jpa.api.Unit;

import javax.enterprise.util.AnnotationLiteral;
import javax.persistence.SynchronizationType;

class UnitLiteral extends AnnotationLiteral<Unit> implements Unit {
    private final String name;
    private final SynchronizationType synchronization;

    UnitLiteral(final String name, final SynchronizationType synchronization) {
        this.name = name;
        this.synchronization = synchronization;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public SynchronizationType synchronization() {
        return synchronization;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UnitLiteral that = UnitLiteral.class.cast(o);
        return name.equals(that.name) && synchronization.equals(that.synchronization);

    }

    @Override
    public int hashCode() {
        return name.hashCode() + 31 * synchronization.hashCode();
    }

    @Override
    public String toString() {
        return "@Unit(name=" + name + ",synchronization=" + synchronization.name() + ")";
    }
}
