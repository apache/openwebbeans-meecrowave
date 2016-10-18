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
package org.apache.microwave.openwebbeans;

import org.apache.cxf.cdi.JAXRSCdiResourceExtension;
import org.apache.webbeans.service.DefaultLoaderService;
import org.apache.webbeans.spi.LoaderService;

import javax.enterprise.inject.spi.Extension;
import java.util.Iterator;
import java.util.List;

// just there to get rid of the original cxf extension, to remove with 3.1.9 hopefully
public class MicrowaveLoader implements LoaderService {
    private final LoaderService defaultService = new DefaultLoaderService();

    @Override
    public <T> List<T> load(final Class<T> serviceType) {
        return defaultService.load(serviceType);
    }

    @Override
    public <T> List<T> load(final Class<T> serviceType, final ClassLoader classLoader) {
        final List<T> load = defaultService.load(serviceType, classLoader);
        if (Extension.class == serviceType) {
            final Iterator<T> e = load.iterator();
            while (e.hasNext()) {
                if (JAXRSCdiResourceExtension.class == e.next().getClass()) {
                    e.remove();
                    break;
                }
            }
        }
        return load;
    }
}
