package org.apache.meecrowave.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.common.util.ClassUnwrapper;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Message;
import org.apache.johnzon.core.AbstractJsonFactory;
import org.apache.johnzon.core.JsonGeneratorFactoryImpl;
import org.apache.johnzon.core.JsonParserFactoryImpl;
import org.apache.johnzon.jaxrs.DelegateProvider;
import org.apache.johnzon.jaxrs.JsrMessageBodyReader;
import org.apache.johnzon.jaxrs.JsrMessageBodyWriter;
import org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider;
import org.apache.meecrowave.Meecrowave;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.proxy.InterceptorDecoratorProxyFactory;
import org.apache.webbeans.proxy.NormalScopeProxyFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.json.Json;
import javax.json.JsonStructure;
import javax.json.stream.JsonGenerator;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Named("cxf")
@ApplicationScoped
public class MeecrowaveBus implements Bus {
    private final Bus delegate = new ExtensionManagerBus();
    private NormalScopeProxyFactory normalScopeProxyFactory;
    private InterceptorDecoratorProxyFactory interceptorDecoratorProxyFactory;

    protected MeecrowaveBus() {
        // no-op: for proxies
    }

    @Inject
    public MeecrowaveBus(final ServletContext context) {
        WebBeansContext webBeansContext = WebBeansContext.currentInstance();
        normalScopeProxyFactory = webBeansContext.getNormalScopeProxyFactory();
        interceptorDecoratorProxyFactory = webBeansContext.getInterceptorDecoratorProxyFactory();

        setProperty(ClassUnwrapper.class.getName(), (ClassUnwrapper) this::getRealClass);

        final Meecrowave.Builder builder = Meecrowave.Builder.class.cast(context.getAttribute("meecrowave.configuration"));
        if (builder != null && builder.isJaxrsProviderSetup()) {
            final List<Object> providers =
                    ofNullable(builder.getJaxrsDefaultProviders())
                            .map(s -> Stream.of(s.split(" *, *"))
                                    .map(String::trim)
                                    .filter(p -> !p.isEmpty())
                                    .map(name -> {
                                        try {
                                            return Thread.currentThread().getContextClassLoader().loadClass(name).newInstance();
                                        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                                            throw new IllegalArgumentException(name + " can't be created");
                                        }
                                    })
                                    .collect(Collectors.<Object>toList()))
                            .orElseGet(() -> Stream.<Object>of(
                                    new ConfiguredJsonbJaxrsProvider(
                                            builder.getJsonbEncoding(), builder.isJsonbNulls(),
                                            builder.isJsonbIJson(), builder.isJsonbPrettify(),
                                            builder.getJsonbBinaryStrategy(), builder.getJsonbNamingStrategy(),
                                            builder.getJsonbOrderStrategy()),
                                    new ConfiguredJsrProvider(
                                            builder.getJsonpBufferStrategy(), builder.getJsonpMaxStringLen(),
                                            builder.getJsonpMaxReadBufferLen(), builder.getJsonpMaxWriteBufferLen(),
                                            builder.isJsonpSupportsComment(), builder.isJsonpPrettify()))
                                    .collect(toList()));

            if (builder.isJaxrsAutoActivateBeanValidation()) {
                try { // we don't need the jaxrsbeanvalidationfeature since bean validation cdi extension handles it normally
                    final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                    contextClassLoader.loadClass("javax.validation.Validation");
                    final Object instance = contextClassLoader.loadClass("org.apache.cxf.jaxrs.validation.ValidationExceptionMapper")
                                                       .getConstructor().newInstance();
                    instance.getClass().getGenericInterfaces(); // validate bval can be used, check NoClassDefFoundError javax.validation.ValidationException
                    providers.add(instance);
                } catch (final Exception | NoClassDefFoundError e) {
                    // no-op
                }
            }

            // client
            if (getProperty("org.apache.cxf.jaxrs.bus.providers") == null) {
                setProperty("skip.default.json.provider.registration", "true");
                setProperty("org.apache.cxf.jaxrs.bus.providers", providers);
            }
        }
    }


    /**
     * Unwrap all proxies and get the real underlying class
     * for detecting annotations, etc.
     * @param o
     * @return
     */
    protected Class<?> getRealClass(Object o) {
        final Class<?> aClass = o.getClass();
        if (aClass.getName().contains("$$")) {
            Class realClass = aClass.getSuperclass();
            if (realClass == Object.class || realClass.isInterface()) {
                // we have to dig deeper as we might have a producer method for an interface
                Class<?>[] interfaces = aClass.getInterfaces();
                if (interfaces.length > 0) {
                    Class<?> rootInterface = interfaces[0];
                    for (Class<?> anInterface : interfaces) {
                        if (rootInterface.isAssignableFrom(anInterface)) {
                            rootInterface = anInterface;
                        }
                    }
                    return rootInterface;
                }
            }
            return realClass;
        }
        return aClass;
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

    @Provider
    @Produces({MediaType.APPLICATION_JSON, "*/*+json"})
    @Consumes({MediaType.APPLICATION_JSON, "*/*+json"})
    public static class ConfiguredJsonbJaxrsProvider<T> extends JsonbJaxrsProvider<T> {
        private ConfiguredJsonbJaxrsProvider(final String encoding,
                                             final boolean nulls,
                                             final boolean iJson,
                                             final boolean pretty,
                                             final String binaryStrategy,
                                             final String namingStrategy,
                                             final String orderStrategy) {
            // ATTENTION this is only a workaround for MEECROWAVE-49 and shall get removed after Johnzon has a fix for it!
            // We add byte[] to the ignored types.
            super(Arrays.asList("[B"));

            ofNullable(encoding).ifPresent(this::setEncoding);
            ofNullable(namingStrategy).ifPresent(this::setPropertyNamingStrategy);
            ofNullable(orderStrategy).ifPresent(this::setPropertyOrderStrategy);
            ofNullable(binaryStrategy).ifPresent(this::setBinaryDataStrategy);
            setNullValues(nulls);
            setIJson(iJson);
            setPretty(pretty);
        }
    }

    @Provider
    @Produces({MediaType.APPLICATION_JSON, "application/*+json"})
    @Consumes({MediaType.APPLICATION_JSON, "application/*+json"})
    public static class ConfiguredJsrProvider extends DelegateProvider<JsonStructure> { // TODO: probably wire the encoding in johnzon
        private ConfiguredJsrProvider(final String bufferStrategy, final int maxStringLen,
                                      final int maxReadBufferLen, final int maxWriteBufferLen,
                                      final boolean supportsComment, final boolean pretty) {
            super(new JsrMessageBodyReader(Json.createReaderFactory(new HashMap<String, Object>() {{
                put(JsonParserFactoryImpl.SUPPORTS_COMMENTS, supportsComment);
                of(maxStringLen).filter(v -> v > 0).ifPresent(s -> put(JsonParserFactoryImpl.MAX_STRING_LENGTH, s));
                of(maxReadBufferLen).filter(v -> v > 0).ifPresent(s -> put(JsonParserFactoryImpl.BUFFER_LENGTH, s));
                ofNullable(bufferStrategy).ifPresent(s -> put(AbstractJsonFactory.BUFFER_STRATEGY, s));
            }}), false), new JsrMessageBodyWriter(Json.createWriterFactory(new HashMap<String, Object>() {{
                put(JsonGenerator.PRETTY_PRINTING, pretty);
                of(maxWriteBufferLen).filter(v -> v > 0).ifPresent(v -> put(JsonGeneratorFactoryImpl.GENERATOR_BUFFER_LENGTH, v));
                ofNullable(bufferStrategy).ifPresent(s -> put(AbstractJsonFactory.BUFFER_STRATEGY, s));
            }}), false));
        }
    }
}
