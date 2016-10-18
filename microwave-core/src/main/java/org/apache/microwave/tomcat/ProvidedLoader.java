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
package org.apache.microwave.tomcat;

import org.apache.catalina.Context;
import org.apache.catalina.Loader;

import java.beans.PropertyChangeListener;

public class ProvidedLoader implements Loader {
    private final ClassLoader delegate;
    private Context context;

    public ProvidedLoader(final ClassLoader loader) {
        this.delegate = loader == null ? ClassLoader.getSystemClassLoader() : loader;
    }

    @Override
    public void backgroundProcess() {
        // no-op
    }

    @Override
    public ClassLoader getClassLoader() {
        return delegate;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void setContext(final Context context) {
        this.context = context;
    }

    @Override
    public boolean modified() {
        return false;
    }

    @Override
    public boolean getDelegate() {
        return false;
    }

    @Override
    public void setDelegate(final boolean delegate) {
        // ignore
    }

    @Override
    public boolean getReloadable() {
        return false;
    }

    @Override
    public void setReloadable(final boolean reloadable) {
        // no-op
    }

    @Override
    public void addPropertyChangeListener(final PropertyChangeListener listener) {
        // no-op
    }

    @Override
    public void removePropertyChangeListener(final PropertyChangeListener listener) {
        // no-op
    }
}

