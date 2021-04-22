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
package org.apache.meecrowave.websocket;

import javax.enterprise.inject.spi.CDI;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class CDIServerConfigurator extends ServerEndpointConfig.Configurator {
    @Override
    public <T> T getEndpointInstance(final Class<T> clazz) throws InstantiationException {
        try {
            return CDI.current().select(clazz).get();
        } catch (final RuntimeException re) {
            return super.getEndpointInstance(clazz);
        }
    }

    @Override
    public String getNegotiatedSubprotocol(final List<String> supported, final List<String> requested) {
        return requested.stream().filter(supported::contains).findFirst().orElse("");
    }


    @Override
    public List<Extension> getNegotiatedExtensions(final List<Extension> installed,
                                                   final List<Extension> requested) {
        if (requested.isEmpty()) {
            return emptyList();
        }
        final Set<String> names = installed.stream().map(Extension::getName).collect(toSet());
        return requested.stream().filter(e -> names.contains(e.getName())).collect(toList());
    }

    @Override
    public boolean checkOrigin(final String originHeaderValue) {
        return true;
    }

    @Override
    public void modifyHandshake(final ServerEndpointConfig sec,
                                final HandshakeRequest request,
                                final HandshakeResponse response) {
        // no-op
    }
}
