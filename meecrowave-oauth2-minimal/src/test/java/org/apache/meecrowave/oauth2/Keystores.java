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

import org.apache.cxf.rt.security.crypto.CryptoUtils;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.BasicConstraintsExtension;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

public final class Keystores {
    private Keystores() {
        // no-op
    }

    public static PublicKey create(final File keystore) throws Exception {
        CryptoUtils.installBouncyCastleProvider();

        final KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, "password".toCharArray());

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
    }

    private static X509Certificate createSignedCertificate(final X509Certificate cetrificate, final X509Certificate issuerCertificate,
                                                           final PrivateKey issuerPrivateKey) {
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
    }
}
