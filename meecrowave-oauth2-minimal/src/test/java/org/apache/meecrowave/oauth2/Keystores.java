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
package org.apache.meecrowave.oauth2;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.*;
import java.io.File;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

public final class Keystores {
    private Keystores() {
        // no-op
    }

    public static PublicKey create(final File keystore) throws Exception {
        Security.setProperty("crypto.policy", "unlimited");

        final KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, "password".toCharArray());

        KeyPair rootKeyPair = generateKeyPair();
        X500Name rootIssuerName = new X500Name("OU=apache,CN=root");
        X509Certificate rootCertificate = getCertificate(rootKeyPair, rootIssuerName, rootKeyPair.getPrivate());

        KeyPair middleKeyPair = generateKeyPair();
        X500Name middleIssuerName = new X500Name("OU=apache,CN=middle");
        X509Certificate middleCertificate = getCertificate(middleKeyPair, middleIssuerName, rootKeyPair.getPrivate());

        KeyPair topKeyPair = generateKeyPair();
        X500Name topIssuerName = new X500Name("OU=apache,CN=top");
        X509Certificate topCertificate = getCertificate(topKeyPair, topIssuerName, middleKeyPair.getPrivate());


        final X509Certificate[] chain = new X509Certificate[]{topCertificate, middleCertificate, rootCertificate};
        ks.setKeyEntry("alice", topKeyPair.getPrivate(), "pwd".toCharArray(), chain);
        keystore.getParentFile().mkdirs();
        try (final OutputStream os = new FileOutputStream(keystore)) {
            ks.store(os, "password".toCharArray());
        }

        return topKeyPair.getPublic();
    }

    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    private static X509Certificate getCertificate(KeyPair certKeyPair, X500Name issuerName, PrivateKey signerKey)
            throws OperatorCreationException, CertificateException {
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuerName,
                BigInteger.valueOf(System.currentTimeMillis()),
                Date.from(Instant.now()), Date.from(Instant.now().plusMillis(1096 * 24 * 60 * 60)),
                issuerName, certKeyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(signerKey);
        X509CertificateHolder certHolder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

}
