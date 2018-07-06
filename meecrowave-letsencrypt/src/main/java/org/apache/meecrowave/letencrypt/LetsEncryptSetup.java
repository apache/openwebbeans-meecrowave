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
package org.apache.meecrowave.letencrypt;

import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.logging.tomcat.LogFacade;

public class LetsEncryptSetup implements Meecrowave.MeecrowaveAwareInstanceCustomizer {
    private Meecrowave instance;

    @Override
    public void accept(final Tomcat tomcat) {
        final ProtocolHandler protocolHandler = tomcat.getConnector().getProtocolHandler();
        if (!AbstractHttp11Protocol.class.isInstance(protocolHandler)) {
            return;
        }

        final LetsEncryptReloadLifecycle.LetsEncryptConfig config = instance.getConfiguration()
            .getExtension(LetsEncryptReloadLifecycle.LetsEncryptConfig.class);
        if (config.getDomains() == null || config.getDomains().trim().isEmpty()) {
            return;
        }

        new LogFacade(getClass().getName()).info("Let's Encrypt extension activated");
        tomcat.getHost().getPipeline().addValve(new LetsEncryptValve(AbstractHttp11Protocol.class.cast(protocolHandler), config));
    }

    @Override
    public void setMeecrowave(final Meecrowave meecrowave) {
        instance = meecrowave;
    }
}
