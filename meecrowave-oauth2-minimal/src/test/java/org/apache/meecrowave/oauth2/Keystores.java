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
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.*;
import java.io.File;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

public final class Keystores {
    private Keystores() {
        // no-op
    }

    public static PublicKey create(final File keystore) throws Exception {
        final KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, "password".toCharArray());

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        final PrivateKey rootPrivateKey = keyPair.getPrivate();

        X500Name issuerName = new X500Name("OU=apache,CN=mwtest");

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuerName,
                BigInteger.valueOf(System.currentTimeMillis()),
                Date.from(Instant.now()), Date.from(Instant.now().plusMillis(1096 * 24 * 60 * 60)),
                issuerName, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(rootPrivateKey);
        X509CertificateHolder certHolder = builder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(certHolder);

        return keyPair.getPublic();

        //X TODO
        /*X TODO fixme
        CryptoUtils.installBouncyCastleProvider();


        final CertAndKeyGen keyGen = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
        keyGen.generate(2048);
        final PrivateKey rootPrivateKey = keyGen.getPrivateKey();

        X509Certificate rootCertificate = keyGen.getSelfCertificate(new X500Name("cn=root"), (long) 365 * 24 * 60 * 60);

        final CertAndKeyGen keyGen1 = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
        keyGen1.generate(2048);
        final PrivateKey middlePrivateKey = keyGen1.getPrivateKey();

        X509Certificate middleCertificate = keyGen1.getSelfCertificate(new X500Name("CN=MIDDLE"), (long) 365 * 24 * 60 * 60);

        //Generate leaf certificate
        final CertAndKeyGen keyGen2 = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
        keyGen2.generate(2048);
        final PrivateKey topPrivateKey = keyGen2.getPrivateKey();


        X509Certificate topCertificate = keyGen2.getSelfCertificate(new X500Name("cn=root"), (long) 365 * 24 * 60 * 60);

        rootCertificate = createSignedCertificate(rootCertificate, rootCertificate, rootPrivateKey);
        middleCertificate = createSignedCertificate(middleCertificate, rootCertificate, rootPrivateKey);
        topCertificate = createSignedCertificate(topCertificate, middleCertificate, middlePrivateKey);

        final X509Certificate[] chain = new X509Certificate[]{topCertificate, middleCertificate, rootCertificate};

        ks.setKeyEntry("alice", topPrivateKey, "pwd".toCharArray(), chain);


        keystore.getParentFile().mkdirs();
        try (final OutputStream os = new FileOutputStream(keystore)) {
            ks.store(os, "password".toCharArray());
        }

        return keyGen2.getPublicKey();
*/
    }

    private static X509Certificate createSignedCertificate(final X509Certificate cetrificate, final X509Certificate issuerCertificate,
                                                           final PrivateKey issuerPrivateKey) {
        return null;
/*X TODO fixme
        try {
            Principal issuer = issuerCertificate.getSubjectDN();
            String issuerSigAlg = issuerCertificate.getSigAlgName();

            byte[] inCertBytes = cetrificate.getTBSCertificate();
            X509CertInfo info = new X509CertInfo(inCertBytes);
            info.set(X509CertInfo.ISSUER, (X500Name) issuer);

            //No need to add the BasicContraint for leaf cert
            if (!cetrificate.getSubjectDN().getName().equals("CN=TOP")) {
                CertificateExtensions exts = new CertificateExtensions();
                BasicConstraintsExtension bce = new BasicConstraintsExtension(true, -1);
                exts.set(BasicConstraintsExtension.NAME, new BasicConstraintsExtension(false, bce.getExtensionValue()));
                info.set(X509CertInfo.EXTENSIONS, exts);
            }

            final X509CertImpl outCert = new X509CertImpl(info);
            outCert.sign(issuerPrivateKey, issuerSigAlg);

            return outCert;
        } catch (final Exception ex) {
            throw new IllegalStateException(ex);
        }
*/
    }
}
