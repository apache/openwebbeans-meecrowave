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
package org.apache.meecrowave.doc;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.doc.generator.ArquillianConfiguration;
import org.apache.meecrowave.doc.generator.CliConfiguration;
import org.apache.meecrowave.doc.generator.Configuration;
import org.apache.meecrowave.doc.generator.Downloads;
import org.apache.meecrowave.doc.generator.LetsEncryptConfiguration;
import org.apache.meecrowave.doc.generator.MavenConfiguration;
import org.apache.meecrowave.doc.generator.OAuth2Configuration;
import org.jbake.app.ConfigUtil;
import org.jbake.app.Oven;

import com.orientechnologies.orient.core.Orient;

public class JBake {
    private JBake() {
        // no-op
    }

    // if you want to switch off PDF generation use as arguments: src/main/jbake target/site-tmp true false
    public static void main(final String[] args) throws Exception {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "64"); // try to have parallelStream better than default

        final File source = args == null || args.length < 1 ? new File("src/main/jbake") : new File(args[0]);
        final File pdfSource = new File(source, "content");
        final File destination = args == null || args.length < 2 ? new File("target/site-tmp") : new File(args[1]);
        final boolean startHttp = args == null || args.length < 2 || Boolean.parseBoolean(args[2]); // by default we dev
        final boolean skipPdf = args != null && args.length > 3 && !Boolean.parseBoolean(args[3]); // by default...too slow sorry
        final boolean updateDownloads = args != null && args.length > 4 && Boolean.parseBoolean(args[4]); // grabs central

        // generation of dynamic content
        new Configuration().run();
        new CliConfiguration().run();
        new ArquillianConfiguration().run();
        new MavenConfiguration().run();
        new OAuth2Configuration().run();
        new LetsEncryptConfiguration().run();

        if (updateDownloads) {
            final ByteArrayOutputStream tableContent = new ByteArrayOutputStream();
            try (final PrintStream stream = new PrintStream(tableContent)) {
                Downloads.doMain(stream);
            }
            try (final Writer writer = new FileWriter(new File(source, "content/download.adoc"))) {
                writer.write("= Downloads\n"
                        + ":jbake-generated: true\n"
                        + ":jbake-date: 2017-07-24\n"
                        + ":jbake-type: page\n"
                        + ":jbake-status: published\n"
                        + ":jbake-meecrowavepdf:\n"
                        + ":jbake-meecrowavecolor: body-blue\n"
                        + ":icons: font\n"
                        + "\n"
                        + "License under Apache License v2 (ALv2).\n"
                        + "\n"
                        + "[.table.table-bordered,options=\"header\"]\n"
                        + "|===\n"
                        + "|Name|Version|Date|Size|Type|Links\n");
                writer.write(new String(tableContent.toByteArray(), StandardCharsets.UTF_8));
                writer.write("\n|===\n");
            }
        }

        final Runnable build = () -> {
            System.out.println("Building Meecrowave website in " + destination);
            final Orient orient = Orient.instance();
            try {
                orient.startup();

                final Oven oven = new Oven(source, destination, new CompositeConfiguration() {{
                    final CompositeConfiguration config = new CompositeConfiguration();
                    config.addConfiguration(new MapConfiguration(new HashMap<String, Object>() {{
                        put("asciidoctor.attributes", new ArrayList<String>() {{
                            add("source-highlighter=highlightjs");
                            add("highlightjs-theme=idea");
                            add("context_rootpath=/meecrowave");
                        }});
                    }}));
                    config.addConfiguration(ConfigUtil.load(source));
                    addConfiguration(config);
                }}, true);
                oven.setupPaths();

                System.out.println("  > baking");
                oven.bake();

                if (!skipPdf) {
                    System.out.println("  > pdfifying");
                    PDFify.generatePdf(pdfSource, destination);
                }

                System.out.println("  > done :)");
            } catch (final Exception e) {
                e.printStackTrace();
            } finally {
                orient.shutdown();
            }
        };

        build.run();
        if (startHttp) {
            final Path watched = source.toPath();
            final WatchService watchService = watched.getFileSystem().newWatchService();
            watched.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            final AtomicBoolean run = new AtomicBoolean(true);
            final AtomicLong render = new AtomicLong(-1);
            final Thread renderingThread = new Thread() {
                {
                    setName("jbake-renderer");
                }

                @Override
                public void run() {
                    long last = System.currentTimeMillis();
                    while (run.get()) {
                        if (render.get() > last) {
                            last = System.currentTimeMillis();
                            try {
                                build.run();
                            } catch (final Throwable oops) {
                                oops.printStackTrace();
                            }
                        }
                        try {
                            sleep(TimeUnit.SECONDS.toMillis(1));
                        } catch (final InterruptedException e) {
                            Thread.interrupted();
                            break;
                        }
                    }
                    System.out.println("Exiting renderer");
                }
            };
            final Thread watcherThread = new Thread() {
                {
                    setName("jbake-file-watcher");
                }

                @Override
                public void run() {
                    while (run.get()) {
                        try {
                            final WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                            if (key == null) {
                                continue;
                            }

                            for (final WatchEvent<?> event : key.pollEvents()) {
                                final WatchEvent.Kind<?> kind = event.kind();
                                if (kind != ENTRY_CREATE && kind != ENTRY_DELETE && kind != ENTRY_MODIFY) {
                                    continue; // unlikely but better to protect ourself
                                }

                                final Path updatedPath = Path.class.cast(event.context());
                                if (kind == ENTRY_DELETE || updatedPath.toFile().isFile()) {
                                    final String path = updatedPath.toString();
                                    if (!path.contains("___jb") && !path.endsWith("~")) {
                                        render.set(System.currentTimeMillis());
                                    }
                                }
                            }
                            key.reset();
                        } catch (final InterruptedException e) {
                            Thread.interrupted();
                            run.compareAndSet(true, false);
                        } catch (final ClosedWatchServiceException cwse) {
                            if (!run.get()) {
                                throw new IllegalStateException(cwse);
                            }
                        }
                    }
                    System.out.println("Exiting file watcher");
                }
            };

            renderingThread.start();
            watcherThread.start();

            final Runnable onQuit = () -> {
                run.compareAndSet(true, false);
                Stream.of(watcherThread, renderingThread).forEach(thread -> {
                    try {
                        thread.join();
                    } catch (final InterruptedException e) {
                        Thread.interrupted();
                    }
                });
                try {
                    watchService.close();
                } catch (final IOException ioe) {
                    // not important
                }
            };

            try (final Meecrowave container = new Meecrowave(new Meecrowave.Builder() {{
                setWebResourceCached(false);
            }}) {{
                start();
                deployWebapp("/meecrowave", destination);
            }}) {
                System.out.println("Started on http://localhost:" + container.getConfiguration().getHttpPort() + "/meecrowave");

                final Scanner console = new Scanner(System.in);
                String cmd;
                while (((cmd = console.nextLine())) != null) {
                    if ("quit".equals(cmd)) {
                        break;
                    } else if ("r".equals(cmd) || "rebuild".equals(cmd) || "build".equals(cmd) || "b".equals(cmd)) {
                        render.set(System.currentTimeMillis());
                    } else {
                        System.err.println("Ignoring " + cmd + ", please use 'build' or 'quit'");
                    }
                }
            }
            onQuit.run();
        }
    }
}
