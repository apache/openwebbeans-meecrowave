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
package org.apache.meecrowave.junit5;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.spi.ContextsService;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ScopesExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
            .create(ScopesExtension.class.getName());

    @Override
    public void beforeEach(final ExtensionContext extensionContext) {
        getScopes(extensionContext).ifPresent(scopes -> {
            final ContextsService contextsService = WebBeansContext.currentInstance().getContextsService();
            Stream.of(scopes).forEach(s -> contextsService.startContext(s, null));
            extensionContext.getStore(NAMESPACE).put(Holder.class, new Holder(scopes));
        });
    }

    @Override
    public void afterEach(final ExtensionContext extensionContext) {
        ofNullable(extensionContext.getStore(NAMESPACE).get(Holder.class, Holder.class)).map(h -> h.scopes).ifPresent(scopes -> {
            final ContextsService contextsService = WebBeansContext.currentInstance().getContextsService();
            final List<Class<? extends Annotation>> list = new ArrayList<>(asList(scopes));
            Collections.reverse(list);
            list.forEach(s -> contextsService.endContext(s, null));
        });
    }

    protected Optional<Class<? extends Annotation>[]> getScopes(final ExtensionContext context) {
        return context.getElement()
                .map(e -> ofNullable(e.getAnnotation(Scopes.class)).orElseGet(() -> context.getParent()
                        .flatMap(ExtensionContext::getElement).map(it -> it.getAnnotation(Scopes.class)).orElse(null)))
                .map(Scopes::scopes).filter(s -> s.length > 0);
    }

    private static class Holder {

        private final Class<? extends Annotation>[] scopes;

        private Holder(final Class<? extends Annotation>[] scopes) {
            this.scopes = scopes;
        }
    }
}
