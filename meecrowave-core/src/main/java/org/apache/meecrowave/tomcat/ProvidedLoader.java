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
package org.apache.meecrowave.tomcat;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.util.LifecycleBase;

import java.beans.PropertyChangeListener;

// used to not recreate another classloader,
// it has a small workaround cause tomcat set properties (clear*) on the classloader
// and AppLoader doesnt support it leading to warnings we don't want
public class ProvidedLoader extends LifecycleBase implements Loader {
    private static final ClassLoader MOCK = new TomcatSettersClassLoader(ProvidedLoader.class.getClassLoader());

    private final ClassLoader delegate;
    private Context context;

    public ProvidedLoader(final ClassLoader loader, final boolean wrap) {
        // use another classloader cause tomcat set properties on the classloader
        final ClassLoader impl = loader == null ? ClassLoader.getSystemClassLoader() : loader;
        this.delegate = wrap ? new TomcatSettersClassLoader(impl) : impl;
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

    @Override
    protected void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
    }

    @Override
    protected void initInternal() throws LifecycleException {
        // no-op
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        // no-op
    }

    // avoid warnings cause AppLoader doesn't support these setters but tomcat expects it
    public static class TomcatSettersClassLoader extends ClassLoader {
        private TomcatSettersClassLoader(final ClassLoader classLoader) {
            super(classLoader);
        }

        public void setClearReferencesObjectStreamClassCaches(final boolean ignored) {
            // no-op
        }

        public void setClearReferencesThreadLocals(final boolean ignored) {
            // no-op
        }

        public void setClearReferencesHttpClientKeepAliveThread(final boolean ignored) {
            // no-op
        }

        public void setClearReferencesRmiTargets(final boolean ignored) {
            // no-op
        }

        public void setClearReferencesStopThreads(final boolean ignored) {
            // no-op
        }

        public void setClearReferencesStopTimerThreads(final boolean ignored) {
            // no-op
        }
    }
}

