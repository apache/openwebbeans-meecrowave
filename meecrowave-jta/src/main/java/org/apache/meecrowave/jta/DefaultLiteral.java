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
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF Default
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.meecrowave.jta;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.util.AnnotationLiteral;
import java.lang.annotation.Annotation;

public class DefaultLiteral extends AnnotationLiteral<Default> implements Default {
    public static final DefaultLiteral INSTANCE = new DefaultLiteral();

    private static final String TOSTRING = "@javax.enterprise.inject.Default()";
    private static final long serialVersionUID = -8922048102786275371L;

    @Override
    public Class<? extends Annotation> annotationType() {
        return Default.class;
    }

    @Override
    public boolean equals(final Object other) {
        return Default.class.isInstance(other)
                || (AnnotationLiteral.class.isInstance(other) && AnnotationLiteral.class.cast(other).annotationType() == annotationType());
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String toString() {
        return TOSTRING;
    }
}
