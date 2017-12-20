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
package org.apache.meecrowave.jpa.internal;

import org.apache.meecrowave.jpa.api.Jpa;

import javax.enterprise.util.AnnotationLiteral;
import java.lang.annotation.Annotation;

class JpaLiteral extends AnnotationLiteral<Jpa> implements Jpa {
    public static final Annotation DEFAULT = new JpaLiteral(true);

    private final boolean transactional;
    private final int hash;

    JpaLiteral(final boolean transactional) {
        this.transactional = transactional;
        this.hash = hashCode();
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Jpa.class;
    }

    @Override
    public boolean transactional() {
        return transactional;
    }

    @Override
    public boolean equals(final Object other) {
        return Jpa.class.isInstance(other) && Jpa.class.cast(other).transactional() == transactional;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "@Jpa(transactional)" + transactional + ")";
    }
}
