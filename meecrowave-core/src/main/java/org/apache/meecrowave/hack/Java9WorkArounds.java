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
package org.apache.meecrowave.hack;

import org.apache.meecrowave.io.IO;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static java.util.Collections.list;
import static java.util.stream.Collectors.toSet;

// no logger in this class!

/**
 * Workaround class providing an execute() method to enforce java.activation loading.
 *
 * For Meecrowave we could use private or package methods but it is open in case end users hit the same issues.
 */
public class Java9WorkArounds {
    private Java9WorkArounds() {
        // no-op
    }

    public static void execute() {
        final String version = System.getProperty("java.version", "-");
        final boolean j9hacks = !version.startsWith("1.") || version.startsWith("1.9.");
        if (!j9hacks) {
            return;
        }

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }

        try { // enforce to load javax.activation.* if we can't directly do it cause CXF needs it
            loader.loadClass("javax.activation.DataSource");
        } catch (final ClassNotFoundException cnfe) {
            final File file = new File(System.getProperty("java.home"), "jmods/java.activation.jmod");
            if (file.isFile()) {
                doLoad(file, findDefineClass(loader), loader, Stream.of(
                        "javax.activation.FileTypeMap",
                        "javax.activation.MimetypesFileTypeMap",
                        "javax.activation.CommandMap",
                        "javax.activation.MailcapCommandMap",
                        "module-info")
                        .map(e -> "classes/" + e.replace('.', '/') + ".class")
                        .collect(toSet()));
            } else {
                System.err.println("Cannot find " + file.getAbsolutePath() + " so javax.activation will not be available");
            }
        }
    }

    // use with caution but public for user packages like javax.transaction etc...
    public static void doLoad(final File file, final Method defineClass, final ClassLoader loader, final Collection<String> excluded) {
        try (final ZipFile zip = new ZipFile(file)) {
            list(zip.entries()).stream()
                    .filter(e -> e.getName().startsWith("classes/") && e.getName().endsWith(".class") && !excluded.contains(e.getName()))
                    .forEach(e -> {
                        try (final InputStream is = zip.getInputStream(e)) {
                            final ByteArrayOutputStream out = new ByteArrayOutputStream();
                            IO.copy(is, out);

                            final String name = e.getName().substring("classes/".length(), e.getName().length() - ".class".length()).replace("/", ".");
                            final byte[] bytes = out.toByteArray();
                            defineClass.invoke(loader, name, bytes, 0, bytes.length);

                            loader.loadClass(name);
                        } catch (final IOException | InvocationTargetException | ClassNotFoundException | IllegalAccessException e1) {
                            e1.printStackTrace();
                        }
                    });
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private static Method findDefineClass(final ClassLoader loader) {
        Method defineClass = null;
        Class<?> type = loader.getClass();
        do {
            try {
                defineClass = type.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            } catch (final NoSuchMethodException ignore) {
                // do nothing, we need to search the superclass
            }
            type = type.getSuperclass();
        } while (defineClass == null && type != Object.class);
        if (!defineClass.isAccessible()) {
            defineClass.setAccessible(true);
        }
        return defineClass;
    }
}
