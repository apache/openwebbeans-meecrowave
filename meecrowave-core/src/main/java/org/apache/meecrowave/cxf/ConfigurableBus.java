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
package org.apache.meecrowave.cxf;

import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonMergePatch;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonPatch;
import javax.json.JsonPatchBuilder;
import javax.json.JsonPointer;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.spi.JsonProvider;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParserFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;

import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.bus.managers.ClientLifeCycleManagerImpl;
import org.apache.cxf.endpoint.ClientLifeCycleManager;
import org.apache.johnzon.core.AbstractJsonFactory;
import org.apache.johnzon.core.JsonGeneratorFactoryImpl;
import org.apache.johnzon.core.JsonParserFactoryImpl;
import org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider;
import org.apache.meecrowave.configuration.Configuration;

public class ConfigurableBus extends ExtensionManagerBus {
    public void initProviders(final Configuration builder,
                              final ClassLoader loader) {
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
                        .orElseGet(() -> {
                            // ensure both providers share the same memory reuse logic
                            final JsonProvider provider = JsonProvider.provider();
                            final JsonReaderFactory readerFactory = provider.createReaderFactory(
                                    new HashMap<String, Object>() {{
                                        put(JsonParserFactoryImpl.SUPPORTS_COMMENTS, builder.isJsonpSupportsComment());
                                        Optional.of(builder.getJsonpMaxStringLen()).filter(v -> v > 0)
                                                .ifPresent(s -> put(JsonParserFactoryImpl.MAX_STRING_LENGTH,
                                                        s));
                                        Optional.of(builder.getJsonpMaxReadBufferLen()).filter(v -> v > 0)
                                                .ifPresent(s -> put(JsonParserFactoryImpl.BUFFER_LENGTH,
                                                        s));
                                        ofNullable(builder.getJsonpBufferStrategy()).ifPresent(
                                                s -> put(AbstractJsonFactory.BUFFER_STRATEGY, s));
                                    }});
                            final JsonWriterFactory writerFactory = provider.createWriterFactory(
                                    new HashMap<String, Object>() {{
                                        put(JsonGenerator.PRETTY_PRINTING, builder.isJsonpPrettify());
                                        Optional.of(builder.getJsonpMaxWriteBufferLen()).filter(v -> v > 0)
                                                .ifPresent(v -> put(
                                                        JsonGeneratorFactoryImpl
                                                                .GENERATOR_BUFFER_LENGTH,
                                                        v));
                                        ofNullable(builder.getJsonpBufferStrategy()).ifPresent(
                                                s -> put(AbstractJsonFactory.BUFFER_STRATEGY, s));
                                    }});
                            return Stream.<Object>of(
                                    new ConfiguredJsonbJaxrsProvider(
                                            builder.getJsonbEncoding(), builder.isJsonbNulls(),
                                            builder.isJsonbIJson(), builder.isJsonbPrettify(),
                                            builder.getJsonbBinaryStrategy(), builder.getJsonbNamingStrategy(),
                                            builder.getJsonbOrderStrategy(),
                                            new DelegateJsonProvider(provider, readerFactory, writerFactory)))
                                    .collect(toList());
                        });

        if (builder.isJaxrsAutoActivateBeanValidation()) {
            try { // we don't need the jaxrsbeanvalidationfeature since bean validation cdi extension handles it normally
                loader.loadClass("javax.validation.Validation");
                final Object instance = loader.loadClass("org.apache.cxf.jaxrs.validation.ValidationExceptionMapper")
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

    public void addClientLifecycleListener() {
        ClientLifeCycleManager manager = getExtension(ClientLifeCycleManager.class);
        if (manager == null) {
            manager = new ClientLifeCycleManagerImpl();
            setExtension(manager, ClientLifeCycleManager.class);
        }
        manager.registerListener(new MeecrowaveClientLifecycleListener());
    }

    @Provider
    @Produces({MediaType.APPLICATION_JSON, "application/*+json"})
    @Consumes({MediaType.APPLICATION_JSON, "application/*+json"})
    public static class ConfiguredJsonbJaxrsProvider<T> extends JsonbJaxrsProvider<T> {
        private final Jsonb jsonb;

        private ConfiguredJsonbJaxrsProvider(final String encoding,
                                             final boolean nulls,
                                             final boolean iJson,
                                             final boolean pretty,
                                             final String binaryStrategy,
                                             final String namingStrategy,
                                             final String orderStrategy,
                                             final JsonProvider provider) {
            // ATTENTION this is only a workaround for MEECROWAVE-49 and shall get removed after Johnzon has a fix for it!
            // We add byte[] to the ignored types.
            super(singletonList("[B"));
            ofNullable(encoding).ifPresent(this::setEncoding);
            ofNullable(namingStrategy).ifPresent(this::setPropertyNamingStrategy);
            ofNullable(orderStrategy).ifPresent(this::setPropertyOrderStrategy);
            ofNullable(binaryStrategy).ifPresent(this::setBinaryDataStrategy);
            setNullValues(nulls);
            setIJson(iJson);
            setPretty(pretty);
            this.jsonb = JsonbBuilder.newBuilder()
                    .withProvider(provider)
                    .withConfig(config)
                    .build();
        }

        @Override
        protected Jsonb createJsonb() {
            return jsonb;
        }

        @Override
        public boolean isReadable(final Class<?> type, final Type genericType,
                                  final Annotation[] annotations, final MediaType mediaType) {
            return super.isReadable(type, genericType, annotations, mediaType) || JsonValue.class.isAssignableFrom(type);
        }

        @Override
        public boolean isWriteable(final Class<?> type, final Type genericType,
                                   final Annotation[] annotations, final MediaType mediaType) {
            return super.isWriteable(type, genericType, annotations, mediaType) || JsonValue.class.isAssignableFrom(type);
        }
    }

    private static class DelegateJsonProvider extends JsonProvider {
        private final JsonReaderFactory readerFactory;
        private final JsonWriterFactory writerFactory;
        private final JsonProvider provider;

        private DelegateJsonProvider(final JsonProvider provider, final JsonReaderFactory readerFactory, final JsonWriterFactory writerFactory) {
            this.provider = provider;
            this.readerFactory = readerFactory;
            this.writerFactory = writerFactory;
        }

        @Override
        public JsonWriterFactory createWriterFactory(final Map<String, ?> config) {
            return writerFactory;
        }

        @Override
        public JsonReaderFactory createReaderFactory(final Map<String, ?> config) {
            return readerFactory;
        }

        @Override
        public JsonParser createParser(final Reader reader) {
            return provider.createParser(reader);
        }

        @Override
        public JsonParser createParser(final InputStream in) {
            return provider.createParser(in);
        }

        @Override
        public JsonParserFactory createParserFactory(final Map<String, ?> config) {
            return provider.createParserFactory(config);
        }

        @Override
        public JsonGenerator createGenerator(final Writer writer) {
            return provider.createGenerator(writer);
        }

        @Override
        public JsonGenerator createGenerator(final OutputStream out) {
            return provider.createGenerator(out);
        }

        @Override
        public JsonGeneratorFactory createGeneratorFactory(final Map<String, ?> config) {
            return provider.createGeneratorFactory(config);
        }

        @Override
        public JsonReader createReader(final Reader reader) {
            return provider.createReader(reader);
        }

        @Override
        public JsonReader createReader(final InputStream in) {
            return provider.createReader(in);
        }

        @Override
        public JsonWriter createWriter(final Writer writer) {
            return provider.createWriter(writer);
        }

        @Override
        public JsonWriter createWriter(final OutputStream out) {
            return provider.createWriter(out);
        }

        @Override
        public JsonObjectBuilder createObjectBuilder() {
            return provider.createObjectBuilder();
        }

        @Override
        public JsonObjectBuilder createObjectBuilder(final JsonObject jsonObject) {
            return provider.createObjectBuilder(jsonObject);
        }

        @Override
        public JsonObjectBuilder createObjectBuilder(final Map<String, Object> map) {
            return provider.createObjectBuilder(map);
        }

        @Override
        public JsonArrayBuilder createArrayBuilder() {
            return provider.createArrayBuilder();
        }

        @Override
        public JsonArrayBuilder createArrayBuilder(final JsonArray initialData) {
            return provider.createArrayBuilder(initialData);
        }

        @Override
        public JsonArrayBuilder createArrayBuilder(final Collection<?> initialData) {
            return provider.createArrayBuilder(initialData);
        }

        @Override
        public JsonPointer createPointer(final String path) {
            return provider.createPointer(path);
        }

        @Override
        public JsonBuilderFactory createBuilderFactory(final Map<String, ?> config) {
            return provider.createBuilderFactory(config);
        }

        @Override
        public JsonString createValue(final String value) {
            return provider.createValue(value);
        }

        @Override
        public JsonNumber createValue(final int value) {
            return provider.createValue(value);
        }

        @Override
        public JsonNumber createValue(final long value) {
            return provider.createValue(value);
        }

        @Override
        public JsonNumber createValue(final double value) {
            return provider.createValue(value);
        }

        @Override
        public JsonNumber createValue(final BigDecimal value) {
            return provider.createValue(value);
        }

        @Override
        public JsonNumber createValue(final BigInteger value) {
            return provider.createValue(value);
        }

        @Override
        public JsonPatch createPatch(final JsonArray array) {
            return provider.createPatch(array);
        }

        @Override
        public JsonPatch createDiff(final JsonStructure source, final JsonStructure target) {
            return provider.createDiff(source, target);
        }

        @Override
        public JsonPatchBuilder createPatchBuilder() {
            return provider.createPatchBuilder();
        }

        @Override
        public JsonPatchBuilder createPatchBuilder(final JsonArray initialData) {
            return provider.createPatchBuilder(initialData);
        }

        @Override
        public JsonMergePatch createMergePatch(final JsonValue patch) {
            return provider.createMergePatch(patch);
        }

        @Override
        public JsonMergePatch createMergeDiff(final JsonValue source, final JsonValue target) {
            return provider.createMergeDiff(source, target);
        }
    }
}
