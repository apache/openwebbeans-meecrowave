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
package org.apache.meecrowave.watching;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.catalina.Context;
import org.apache.meecrowave.logging.tomcat.LogFacade;

public class ReloadOnChangeController implements AutoCloseable, Runnable {
    private final Context context;
    private final long bouncing;
    private final Consumer<Context> redeployCallback;
    private final Collection<Path> paths = new ArrayList<>();
    private WatchService watchService;
    private Thread bouncer;
    private Thread watcher;
    private volatile boolean running = true;
    private volatile long redeployMarker = System.nanoTime();

    public ReloadOnChangeController(final Context context, final int watcherBouncing, final Consumer<Context> redeployCallback) {
        this.context = context;
        this.bouncing = (long) watcherBouncing;
        this.redeployCallback = ofNullable(redeployCallback).orElse(Context::reload);
    }

    public void register(final File folder) {
        paths.add(folder.toPath());
    }

    public void start() {
        if (paths.isEmpty()) {
            return;
        }
        try {
            watchService = paths.iterator().next().getFileSystem().newWatchService(); // assuming all share the same FS
            for (final Path p : paths) {
                p.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            }
        } catch (final IOException ex) {
            new LogFacade(ReloadOnChangeController.class.getName())
                    .warn("Hot reloading will not be available", ex);
        }

        watcher = new Thread(this);
        watcher.setName("meecrowave-watcher-controller");
        watcher.start();
    }

    protected synchronized void redeploy() {
        redeployCallback.accept(context);
    }

    @Override
    public void close() {
        if (!running) {
            return;
        }
        running = false;
        final long waitMs = bouncing * 2 + 5000 /*margin if redeploying, we can make it configurable later*/;
        if (bouncer != null) {
            try {
                bouncer.join(waitMs);
            } catch (final InterruptedException e) {
                Thread.interrupted();
            }
        }
        if (watcher != null) {
            try {
                watcher.join(waitMs);
            } catch (final InterruptedException e) {
                Thread.interrupted();
            }
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (final IOException ex) {
                new LogFacade(ReloadOnChangeController.class.getName())
                        .warn(ex.getMessage(), ex);
            }
        }
    }

    public boolean shouldRun() {
        return !paths.isEmpty();
    }

    @Override
    public void run() {
        if (watchService == null) {
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        bouncer = new Thread(() -> { // simple bouncing impl
            long last = redeployMarker;
            latch.countDown();

            boolean needsRedeploy = false;
            long antepenultiem = -1;
            while (running) {
                if (redeployMarker > last) {
                    antepenultiem = last;
                    last = redeployMarker;
                    needsRedeploy = true;
                } else if (needsRedeploy) {
                    antepenultiem = last;
                }

                try {
                    Thread.sleep(bouncing);
                } catch (final InterruptedException e) {
                    Thread.interrupted();
                    break;
                }

                if (needsRedeploy && last == antepenultiem) {
                    new LogFacade(ReloadOnChangeController.class.getName()).info("Redeploying " + context.getName());
                    redeploy();
                }
            }
        });
        bouncer.setName("meecrowave-watcher-redeployer");
        bouncer.start();
        try {
            latch.await(1, TimeUnit.MINUTES);
        } catch (final InterruptedException e) {
            Thread.interrupted();
            return;
        }

        paths.forEach(p -> {
            try {
                Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                        dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (final IOException e) {
                new LogFacade(ReloadOnChangeController.class.getName()).warn(e.getMessage());
            }
        });

        try {
            while (running) {
                final WatchKey watchKey = watchService.poll(bouncing, TimeUnit.MILLISECONDS);
                if (watchKey == null) {
                    Thread.sleep(bouncing);
                    continue;
                }

                boolean foundNew = false;
                for (final WatchEvent<?> event : watchKey.pollEvents()) {
                    final Path path = Path.class.cast(event.context());
                    final WatchEvent.Kind<?> kind = event.kind();
                    if (!isIgnored(kind, path)) {
                        foundNew = true;

                        final File file = path.toAbsolutePath().toFile();
                        if (file.isDirectory()) {
                            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                                try {
                                    path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                                            StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                                } catch (final IOException e) {
                                    new LogFacade(ReloadOnChangeController.class.getName()).warn(e.getMessage());
                                }
                            }
                        }
                        break;
                    }
                }

                if (foundNew) {
                    new LogFacade(ReloadOnChangeController.class.getName()).info("Marking to redeploy " + context.getName());
                    redeployMarker = System.nanoTime();
                }

                if (!watchKey.reset()) { // deletion
                    watchKey.cancel();
                }
            }
        } catch (final InterruptedException ie) {
            Thread.interrupted();
        }
    }

    private boolean isIgnored(final WatchEvent.Kind<?> kind, final Path path) {
        final String pathStr = path.toString();
        return pathStr.endsWith("___jb_tmp___") || pathStr.endsWith("___jb_old___") || pathStr.endsWith("~") || isResource(pathStr);
    }

    private boolean isResource(final String pathStr) {
        final int idx = pathStr.lastIndexOf('.');
        return idx > 0 && asList(".html", ".xhtml", ".js", ".ts", ".css", ".png", ".svg", ".jpg", ".jpeg").contains(pathStr.substring(idx));
    }
}
