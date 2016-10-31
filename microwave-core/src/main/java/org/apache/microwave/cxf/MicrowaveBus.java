package org.apache.microwave.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.common.util.ClassUnwrapper;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Message;
import org.apache.johnzon.jaxrs.JsrProvider;
import org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider;
import org.apache.microwave.Microwave;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;

@Named("cxf")
@ApplicationScoped
public class MicrowaveBus implements Bus {
    private final Bus delegate = new ExtensionManagerBus();

    protected MicrowaveBus() {
        // no-op: for proxies
    }

    @Inject
    public MicrowaveBus(final ServletContext context) {
        setProperty(ClassUnwrapper.class.getName(), (ClassUnwrapper) o -> {
            final Class<?> aClass = o.getClass();
            if (aClass.getName().contains("$$")) {
                return aClass.getSuperclass();
            }
            return aClass;
        });

        final Microwave.Builder builder = Microwave.Builder.class.cast(context.getAttribute("microwave.configuration"));
        if (builder.isJaxrsProviderSetup()) {
            final List<Object> providers =
                    ofNullable(builder.getJaxrsDefaultProviders())
                            .map(s -> Stream.of(s.split(" *, *"))
                                    .map(name -> {
                                        try {
                                            return Thread.currentThread().getContextClassLoader().loadClass(s).newInstance();
                                        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                                            throw new IllegalArgumentException(name + " can't be created");
                                        }
                                    })
                                    .collect(Collectors.<Object>toList()))
                            .orElseGet(() -> asList(/*new JohnzonProvider<>(),*/ new JsonbJaxrsProvider(), new JsrProvider()));

            // client
            if (getProperty("org.apache.cxf.jaxrs.bus.providers") == null) {
                setProperty("skip.default.json.provider.registration", "true");
                setProperty("org.apache.cxf.jaxrs.bus.providers", providers);
            }
        }
    }

    @Override
    public <T> T getExtension(final Class<T> extensionType) {
        return delegate.getExtension(extensionType);
    }

    @Override
    public <T> void setExtension(final T extension, final Class<T> extensionType) {
        delegate.setExtension(extension, extensionType);
    }

    @Override
    public boolean hasExtensionByName(final String name) {
        return delegate.hasExtensionByName(name);
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public void setId(final String i) {
        delegate.setId(i);
    }

    @Override
    public void shutdown(final boolean wait) {
        delegate.shutdown(wait);
    }

    @Override
    public void setProperty(final String s, final Object o) {
        delegate.setProperty(s, o);
    }

    @Override
    public Object getProperty(final String s) {
        return delegate.getProperty(s);
    }

    @Override
    public void setProperties(final Map<String, Object> properties) {
        delegate.setProperties(properties);
    }

    @Override
    public Map<String, Object> getProperties() {
        return delegate.getProperties();
    }

    @Override
    public Collection<Feature> getFeatures() {
        return delegate.getFeatures();
    }

    @Override
    public void setFeatures(final Collection<? extends Feature> features) {
        delegate.setFeatures(features);
    }

    @Override
    public BusState getState() {
        return delegate.getState();
    }

    @Override
    public List<Interceptor<? extends Message>> getInInterceptors() {
        return delegate.getInInterceptors();
    }

    @Override
    public List<Interceptor<? extends Message>> getOutInterceptors() {
        return delegate.getOutInterceptors();
    }

    @Override
    public List<Interceptor<? extends Message>> getInFaultInterceptors() {
        return delegate.getInFaultInterceptors();
    }

    @Override
    public List<Interceptor<? extends Message>> getOutFaultInterceptors() {
        return delegate.getOutFaultInterceptors();
    }
}
