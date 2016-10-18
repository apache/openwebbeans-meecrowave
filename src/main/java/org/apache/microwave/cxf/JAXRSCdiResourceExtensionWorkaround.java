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
package org.apache.microwave.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.cdi.JAXRSCdiResourceExtension;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;

// just there to ensure we use a single bus even for ResourceUtils.createApplication, should be removed with 3.1.9 hopefully
public class JAXRSCdiResourceExtensionWorkaround extends JAXRSCdiResourceExtension {
    @Override
    public void load(@Observes final AfterDeploymentValidation event, final BeanManager beanManager) {
        final Bus bus = Bus.class.cast(beanManager.getReference(beanManager.resolve(beanManager.getBeans(Bus.class)), Bus.class, null));
        BusFactory.setThreadDefaultBus(bus); // cause app class will rely on that and would create multiple bus and then deployment would be broken
        try {
            super.load(event, beanManager);
        } finally {
            BusFactory.clearDefaultBusForAnyThread(bus);
        }
    }
}
