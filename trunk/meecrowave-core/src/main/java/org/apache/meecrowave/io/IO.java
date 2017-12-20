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
package org.apache.meecrowave.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public final class IO {
    private IO() {
        // no-op
    }

    public static void delete(final File dir) {
        if (!dir.exists()) {
            return;
        }

        if (dir.isFile()) {
            retryDelete(dir);
            return;
        }

        Stream.of(ofNullable(dir.listFiles()).orElseGet(() -> new File[0]))
                .forEach(f -> {
                    if (f.isFile()) {
                        retryDelete(f);
                    } else {
                        delete(f);
                    }
                });
        retryDelete(dir);
    }

    private static void retryDelete(final File f) {
        for (int i = 0; i < 3; i++) {
            if (f.exists() && !f.delete()) {
                System.gc(); // win
                try {
                    Thread.sleep(50);
                } catch (final InterruptedException e) {
                    Thread.interrupted();
                }
                continue;
            }
            return;
        }
        throw new IllegalStateException("Can't delete " + f);
    }

    public static void copy(final InputStream is, final OutputStream os) throws IOException {
        byte[] buffer = new byte[16384];
        int count;
        while (-1 != (count = is.read(buffer))) {
            os.write(buffer, 0, count);
        }
    }

    public static String toString(final InputStream stream) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(stream, baos);
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void mkdirs(final File d) {
        if (!d.isDirectory() && !d.mkdirs()) {
            throw new IllegalStateException(d + " can't be created");
        }
    }
}
