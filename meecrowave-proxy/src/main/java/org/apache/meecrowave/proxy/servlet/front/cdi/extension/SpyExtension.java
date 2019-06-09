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
package org.apache.meecrowave.proxy.servlet.front.cdi.extension;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessObserverMethod;

import org.apache.meecrowave.proxy.servlet.front.cdi.event.AfterResponse;
import org.apache.meecrowave.proxy.servlet.front.cdi.event.BeforeRequest;
import org.apache.meecrowave.proxy.servlet.front.cdi.event.OnRequest;
import org.apache.meecrowave.proxy.servlet.front.cdi.event.OnResponse;

public class SpyExtension implements Extension {
    private boolean hasBeforeEvent;
    private boolean hasAfterEvent;
    private boolean hasOnRequestEvent;
    private boolean hasOnResponseEvent;

    void onBeforeObserver(@Observes final ProcessObserverMethod<BeforeRequest, ?> processObserverMethod) {
        hasBeforeEvent = true;
    }

    void onAfterObserver(@Observes final ProcessObserverMethod<AfterResponse, ?> processObserverMethod) {
        hasAfterEvent = true;
    }

    void onRequestObserver(@Observes final ProcessObserverMethod<OnRequest, ?> processObserverMethod) {
        hasOnRequestEvent = true;
    }

    void onResponseObserver(@Observes final ProcessObserverMethod<OnResponse, ?> processObserverMethod) {
        hasOnResponseEvent = true;
    }

    public boolean isHasOnRequestEvent() {
        return hasOnRequestEvent;
    }

    public boolean isHasOnResponseEvent() {
        return hasOnResponseEvent;
    }

    public boolean isHasBeforeEvent() {
        return hasBeforeEvent;
    }

    public boolean isHasAfterEvent() {
        return hasAfterEvent;
    }
}
