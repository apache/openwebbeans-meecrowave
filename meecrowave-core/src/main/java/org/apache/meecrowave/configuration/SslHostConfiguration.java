/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.meecrowave.configuration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.meecrowave.logging.tomcat.LogFacade;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.xbean.recipe.ObjectRecipe;
import org.apache.xbean.recipe.Option;

/**
 * Parses the attributes for the SSLHostConfig.
 *
 * This is a bit more complicated as the config of the certificates changed in Tomcat 10,
 * and we liked to keep backward compatibility.
 */
public class SslHostConfiguration {

    private static final Set<String> KNOWN_FLAT_CERTIFICATE_ATTRIBUTES;
    static {
        // fill all the setter which take a String as parameter
        KNOWN_FLAT_CERTIFICATE_ATTRIBUTES = new HashSet<>();
        final List<String> list = Arrays.stream(SSLHostConfigCertificate.class.getMethods())
                .filter(SslHostConfiguration::isStringSetter)
                .map(SslHostConfiguration::getSetterAttribute)
                .toList();
        KNOWN_FLAT_CERTIFICATE_ATTRIBUTES.addAll(list);
    }

    private static String getSetterAttribute(Method method) {
        final String name = method.getName();
        return Character.toLowerCase(name.charAt(3)) + name.substring(4);
    }

    private static boolean isStringSetter(Method m) {
        final String name = m.getName();
        return name.length() >= 4 && name.startsWith("set") && Character.isUpperCase(name.charAt(3))
                && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class;
    }

    public static final String DEFAULT_HOST_KEY = "__default__";

    private SslHostConfiguration() {
        // utility class ct
    }

    public static List<SSLHostConfig> buildSslHostConfig(Configuration configuration) {
        final List<SSLHostConfig> sslHostConfigs = new ArrayList<>();

        final Map<String, Map<String, String>> sslConfigProps = groupPropertiesBySslHost(configuration);

        if (!sslConfigProps.isEmpty()) {
            // first we register the default host
            if (sslConfigProps.containsKey(DEFAULT_HOST_KEY)) {
                sslHostConfigs.add(createSslHostConfig(sslConfigProps.get(DEFAULT_HOST_KEY)));
            }

            // now sort the rest and add it.
            sslConfigProps.keySet().stream()
                    .filter(h -> !DEFAULT_HOST_KEY.endsWith(h))
                    .map(Integer::parseInt)
                    .sorted()
                    .forEach(hostNr -> {
                        final SSLHostConfig sslHostConfig = createSslHostConfig(sslConfigProps.get(hostNr.toString()));
                        sslHostConfigs.add(sslHostConfig);
                        new LogFacade(SslHostConfiguration.class.getName())
                                .info("Created SSLHostConfig #" + hostNr + " (" + sslHostConfig.getHostName() + ")");
                    });
        }

        return sslHostConfigs;
    }

    /**
     * Group the connector.sslhostconfig properties by sslHost.
     *
     * @return key is the ssl host. entry is configKey=configValue map. the default host has '__default__' as key.
     */
    private static Map<String, Map<String, String>> groupPropertiesBySslHost(Configuration configuration) {
        Map<String, Map<String, String>> sslConfigProps = new HashMap<>();

        // First we sort all the configuration properties and group them by sslHost
        for (String key : configuration.getProperties().stringPropertyNames()) {
            if (key.startsWith("connector.sslhostconfig.")) {
                final String[] keyParts = key.split("\\.");
                if (keyParts.length == 3) {
                    // default
                    sslConfigProps.computeIfAbsent(DEFAULT_HOST_KEY, k -> new HashMap<>())
                            .put(keyParts[2], configuration.getProperties().getProperty(key));
                } else if (keyParts.length == 4) {
                    // numerated other sslhosts
                    // In this case the keyParts[2] is the 'number' and all config is keyParts[3]
                    String number = keyParts[2];
                    sslConfigProps.computeIfAbsent(number, k -> new HashMap<>())
                            .put(keyParts[3], configuration.getProperties().getProperty(key));
                }
            }
        }
        return sslConfigProps;
    }

    public static SSLHostConfig createSslHostConfig(Map<String, String> sslHostConfigProperties) {
        // step1: expand well known previously 'flat' stored certificate attributes
        // this is necessary for backward compatibility reasons
        final ObjectRecipe sslHostConfigReceipe = newRecipe(SSLHostConfig.class.getName());

        List<String> certificateConfig = new ArrayList<>();
        for (String key : sslHostConfigProperties.keySet()) {
            if (KNOWN_FLAT_CERTIFICATE_ATTRIBUTES.contains(key)) {
                certificateConfig.add(key);
            } else {
                sslHostConfigReceipe.setProperty(key, sslHostConfigProperties.get(key));
            }
        }

        final SSLHostConfig sslHostConfig = (SSLHostConfig) sslHostConfigReceipe.create();

        if (!certificateConfig.isEmpty()) {
            final SSLHostConfigCertificate sslConfig = sslHostConfig.getCertificates(true).iterator().next();

            for (String key : certificateConfig) {
                invokeSetter(sslConfig, key, sslHostConfigProperties.get(key));
            }
        }

        return sslHostConfig;
    }

    private static void invokeSetter(SSLHostConfigCertificate sslConfig, String key, String value) {
        String setterName = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);

        try {
            final Method m = SSLHostConfigCertificate.class.getMethod(setterName, String.class);
            m.invoke(sslConfig, value);
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static ObjectRecipe newRecipe(final String clazz) {
        final ObjectRecipe recipe = new ObjectRecipe(clazz);
        recipe.allow(Option.FIELD_INJECTION);
        recipe.allow(Option.PRIVATE_PROPERTIES);
        return recipe;
    }

}
