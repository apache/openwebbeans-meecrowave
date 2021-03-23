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
package org.apache.meecrowave.letencrypt;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.meecrowave.logging.tomcat.LogFacade;
import org.apache.meecrowave.runner.Cli;
import org.apache.meecrowave.runner.cli.CliOption;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

// we depend on bouncycastle but user myst add it to be able to use that
// todo: check we can get rid of it and use jaxrs client instead of acme lib
public class LetsEncryptReloadLifecycle implements AutoCloseable, Runnable {

    private final AtomicReference<LogFacade> logger = new AtomicReference<>();

    private final AbstractHttp11Protocol<?> protocol;

    private final ScheduledExecutorService thread;

    private final ScheduledFuture<?> refreshTask;

    private final LetsEncryptConfig config;

    private final BiConsumer<String, String> challengeUpdater;

    public LetsEncryptReloadLifecycle(final LetsEncryptConfig config, final AbstractHttp11Protocol<?> protocol,
                                      final BiConsumer<String, String> challengeUpdater) {
        this.config = config;
        this.config.init();
        this.protocol = protocol;
        this.challengeUpdater = challengeUpdater;

        final SecurityManager s = System.getSecurityManager();
        final ThreadGroup group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.thread = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(final Runnable r) {
                final Thread newThread = new Thread(group, r, LetsEncryptReloadLifecycle.class.getName() + "_" + hashCode());
                newThread.setDaemon(false);
                newThread.setPriority(Thread.NORM_PRIORITY);
                newThread.setContextClassLoader(getClass().getClassLoader());
                return newThread;
            }
        });
        refreshTask = this.thread.scheduleAtFixedRate(this, 0L, config.getRefreshInterval(), TimeUnit.SECONDS);
    }

    @Override
    public synchronized void run() {
        final KeyPair userKeyPair = loadOrCreateKeyPair(config.getUserKeySize(), config.getUserKeyLocation());
        final KeyPair domainKeyPair = loadOrCreateKeyPair(config.getDomainKeySize(), config.getDomainKey());

        final Session session = new Session(config.getEndpoint());
        try {
            final Account account = new AccountBuilder().agreeToTermsOfService().useKeyPair(userKeyPair).create(session);
            final Order order = account.newOrder().domains(config.getDomains().trim().split(",")).create();
            final boolean updated = order.getAuthorizations().stream().map(authorization -> {
                try {
                    return authorize(authorization);
                } catch (final AcmeException e) {
                    getLogger().error(e.getMessage(), e);
                    return false;
                }
            }).reduce(false, (previous, val) -> previous || val);
            if (!updated) {
                return;
            }

            final CSRBuilder csrBuilder = new CSRBuilder();
            csrBuilder.addDomains(config.getDomains());
            csrBuilder.sign(domainKeyPair);

            try (final Writer writer = new BufferedWriter(new FileWriter(config.getDomainCertificate()))) {
                csrBuilder.write(writer);
            }

            order.execute(csrBuilder.getEncoded());

            try {
                int attempts = config.getRetryCount();
                while (order.getStatus() != Status.VALID && attempts-- > 0) {
                    if (order.getStatus() == Status.INVALID) {
                        throw new AcmeException("Order failed... Giving up.");
                    }
                    Thread.sleep(config.getRetryTimeoutMs());
                    order.update();
                }
            } catch (final InterruptedException ex) {
                getLogger().error(ex.getMessage());
                Thread.currentThread().interrupt();
                return;
            }

            final Certificate certificate = order.getCertificate();
            getLogger().info("Got new certificate " + certificate.getLocation() + " for domain(s) " + config.getDomains());

            try (final Writer writer = new BufferedWriter(new FileWriter(config.getDomainChain()))) {
                certificate.writeCertificate(writer);
            }

            protocol.reloadSslHostConfigs();
        } catch (final AcmeException | IOException ex) {
            getLogger().error(ex.getMessage(), ex);
        }
    }

    private LogFacade getLogger() {
        LogFacade logFacade = logger.get();
        if (logFacade == null) {
            logFacade = new LogFacade(getClass().getName());
            // ok to use 2 instances since the backing instance will be the same, so ignore returned value
            logger.compareAndSet(null, logFacade);
        }
        return logFacade;
    }

    @Override
    public void close() {
        ofNullable(refreshTask).ifPresent(t -> t.cancel(true));
        ofNullable(thread).ifPresent(ExecutorService::shutdownNow);
        try {
            thread.awaitTermination(5, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean authorize(final Authorization authorization) throws AcmeException {
        final Challenge challenge = httpChallenge(authorization);
        if (challenge == null) {
            throw new AcmeException("HTTP challenge is null");
        }
        if (challenge.getStatus() == Status.VALID) {
            return false;
        }

        challenge.trigger();

        try {
            int attempts = config.getRetryCount();
            while (challenge.getStatus() != Status.VALID && attempts-- > 0) {
                if (challenge.getStatus() == Status.INVALID) {
                    throw new AcmeException("Invalid challenge status, exiting refresh iteration");
                }

                Thread.sleep(config.getRetryTimeoutMs());
                challenge.update();
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        if (challenge.getStatus() != Status.VALID) {
            throw new AcmeException("Challenge for domain " + authorization.getIdentifier() + ", is invalid, exiting iteration");
        }
        return true;
    }

    private Challenge httpChallenge(final Authorization auth) throws AcmeException {
        final Http01Challenge challenge = auth.findChallenge(Http01Challenge.TYPE);
        if (challenge == null) {
            throw new AcmeException("Challenge is null");
        }

        challengeUpdater.accept("/.well-known/acme-challenge/" + challenge.getToken(), challenge.getAuthorization());
        return challenge;
    }

    private KeyPair loadOrCreateKeyPair(final int keySize, final File file) {
        if (file.exists()) {
            try (final PEMParser parser = new PEMParser(new FileReader(file))) {
                return new JcaPEMKeyConverter().getKeyPair(PEMKeyPair.class.cast(parser.readObject()));
            } catch (final IOException ex) {
                throw new IllegalStateException("Can't read PEM file: " + file, ex);
            }
        } else {
            try {
                final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(keySize);
                final KeyPair keyPair = keyGen.generateKeyPair();
                try (final JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(file))) {
                    writer.writeObject(keyPair);
                } catch (final IOException ex) {
                    throw new IllegalStateException("Can't read PEM file: " + file, ex);
                }
                return keyPair;
            } catch (final NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    public static class LetsEncryptConfig implements Cli.Options {

        @CliOption(name = "letsencrypt-refresh-interval", description = "Number of second between let'sencrypt refreshes")
        private long refreshInterval = 60;

        @CliOption(name = "letsencrypt-domains", description = "Comma separated list of domains to manage")
        private String domains;

        @CliOption(name = "letsencrypt-key-user-location", description = "Where the user key must be stored")
        private File userKeyLocation;

        @CliOption(name = "letsencrypt-key-user-size", description = "User key size")
        private int userKeySize = 2048;

        @CliOption(name = "letsencrypt-key-domain-location", description = "Where the domain key must be stored")
        private File domainKey;

        @CliOption(name = "letsencrypt-key-domain-size", description = "Domain key size")
        private int domainKeySize = 2048;

        @CliOption(name = "letsencrypt-certificate-domain-location", description = "Where the domain certificate must be stored")
        private File domainCertificate;

        @CliOption(name = "letsencrypt-chain-domain-location", description = "Where the domain chain must be stored")
        private File domainChain;

        @CliOption(name = "letsencrypt-endpoint", description = "Endpoint to use to get the certificates")
        private String endpoint;

        @CliOption(name = "letsencrypt-endpoint-staging", description = "Ignore if endpoint is set, otherwise it set the endpoint accordingly")
        private boolean staging = false;

        @CliOption(name = "letsencrypt-retry-timeout-ms", description = "How long to wait before retrying to get the certificate, default is 3s")
        private long retryTimeoutMs = 3000;

        @CliOption(name = "letsencrypt-retry-count", description = "How many retries to do")
        private int retryCount = 20;

        public void init() {
            if (userKeyLocation == null) {
                userKeyLocation = new File(System.getProperty("catalina.base"), "conf/letsencrypt/user.key");
            }
            if (domainKey == null) {
                domainKey = new File(System.getProperty("catalina.base"), "conf/letsencrypt/domain.key");
            }
            if (domainCertificate == null) {
                domainCertificate = new File(System.getProperty("catalina.base"), "conf/letsencrypt/domain.csr");
            }
            if (domainChain == null) {
                domainChain = new File(System.getProperty("catalina.base"), "conf/letsencrypt/domain.chain.csr");
            }
            if (endpoint == null) {
                if (isStaging()) {
                    endpoint = "https://acme-staging-v02.api.letsencrypt.org/directory";
                } else {
                    endpoint = "https://acme-v02.api.letsencrypt.org/directory";
                }
            }
            Stream.of(userKeyLocation, domainKey, domainCertificate, domainChain).map(File::getAbsoluteFile)
                    .map(File::getParentFile).filter(Objects::nonNull).distinct().forEach(File::mkdirs);
        }

        public boolean isStaging() {
            return staging;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public int getDomainKeySize() {
            return domainKeySize;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public long getRefreshInterval() {
            return refreshInterval;
        }

        public String getDomains() {
            return domains;
        }

        public File getUserKeyLocation() {
            return userKeyLocation;
        }

        public int getUserKeySize() {
            return userKeySize;
        }

        public File getDomainKey() {
            return domainKey;
        }

        public File getDomainCertificate() {
            return domainCertificate;
        }

        public File getDomainChain() {
            return domainChain;
        }

        public long getRetryTimeoutMs() {
            return retryTimeoutMs;
        }
    }
}
