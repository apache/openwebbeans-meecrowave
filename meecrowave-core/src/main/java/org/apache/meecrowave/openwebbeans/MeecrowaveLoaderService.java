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
package org.apache.meecrowave.openwebbeans;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.enterprise.inject.spi.Extension;

import org.apache.meecrowave.cxf.Cxfs;
import org.apache.webbeans.spi.LoaderService;

public class MeecrowaveLoaderService implements LoaderService {
    @Override
    public <T> List<T> load(final Class<T> serviceType) {
        return load(serviceType, Thread.currentThread().getContextClassLoader());
    }

    @Override
    public <T> List<T> load(final Class<T> serviceType, final ClassLoader classLoader) {
        if (Extension.class == serviceType) {
            return doLoad(serviceType, classLoader)
                    .map(e -> serviceType.cast(Cxfs.mapCdiExtensionIfNeeded(Extension.class.cast(e))))
                    .collect(toList());
        }
        return doLoad(serviceType, classLoader)
                .collect(toList());
    }

    private <T> Stream<T> doLoad(final Class<T> serviceType, final ClassLoader classLoader) {
        return StreamSupport.stream(ServiceLoader.load(serviceType, classLoader).spliterator(), false);
    }
}
