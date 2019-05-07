package ru.i_novus.common.sign.test;

/*-
 * -----------------------------------------------------------------
 * common-sign-gost
 * -----------------------------------------------------------------
 * Copyright (C) 2018 - 2019 I-Novus LLC
 * -----------------------------------------------------------------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------
 */

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import ru.i_novus.common.sign.api.SignAlgorithmType;
import ru.i_novus.common.sign.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

import static org.junit.Assert.assertNotNull;
import static ru.i_novus.common.sign.util.Base64Util.getBase64EncodedString;

@Slf4j
public class CryptoTest {
    public static final String TEST_CERTIFICATE_CN = "CN=Сотрудник ООО \"Ай-Новус\", O=Общество с ограниченной ответственностью \"Ай-Новус\", E=office@i-novus.ru, L=Казань, C=RU, STREET=ул. Сеченова 19Б";
    private static final byte[] TEST_DATA_TO_SIGN = getTestData();

    @BeforeClass
    public static void init() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testAllAlgorithms() throws IOException, GeneralSecurityException {
        String basePath = Files.createTempDirectory("keys").toString();
        for (SignAlgorithmType signAlgorithm : SignAlgorithmType.values()) {
            if (signAlgorithm.getAvailableParameterSpecificationNames().isEmpty()) {
                testOneAlgorithm(signAlgorithm, null, basePath);
            } else {
                for (String parameterSpecName : signAlgorithm.getAvailableParameterSpecificationNames()) {
                    testOneAlgorithm(signAlgorithm, parameterSpecName, basePath);
                }
            }
        }
    }

    private void testOneAlgorithm(final SignAlgorithmType signAlgorithm, final String parameterSpecName, final String basePath) throws IOException, GeneralSecurityException {
        KeyPair keyPair = CryptoUtil.generateKeyPair(signAlgorithm, parameterSpecName);
        checkKeyPair(keyPair);

        String keyPath = signAlgorithm.name() + (parameterSpecName == null ? "" : "_" + parameterSpecName) + ".key";
        String crtPath = signAlgorithm.name() + (parameterSpecName == null ? "" : "_" + parameterSpecName) + ".crt";
        X509CertificateHolder certificateHolder = selfSignedCertificate(keyPair, signAlgorithm);
        if (certificateHolder == null)
            throw new IllegalArgumentException("Signature algorithm '" + signAlgorithm.name() + "' is not supported");

        CryptoIO cryptoIO = CryptoIO.getInstance();
        keyPath = cryptoIO.writePKToDERFile(keyPair, Paths.get(basePath, keyPath));
        logger.info("Path to key: {}, algorithm {}", keyPath, signAlgorithm);
        crtPath = cryptoIO.writeCertToDERFile(certificateHolder, Paths.get(basePath, crtPath));
        logger.info("Path to certificate: {}, algorithm {}", crtPath, signAlgorithm);

        try {
            certificateHolder = cryptoIO.readCertFromDER(crtPath);
            PKCS8EncodedKeySpec keySpec = cryptoIO.readPkFromDER(keyPath);
            X509Certificate certificate = CryptoFormatConverter.getInstance().getCertificateFromHolder(certificateHolder);

            SignAlgorithmType algorithmType = SignAlgorithmType.findByAlgorithmName(certificate.getSigAlgName());
            if (TEST_DATA_TO_SIGN != null) {
                String signature = CryptoUtil.getBase64Signature(new String(TEST_DATA_TO_SIGN), getBase64EncodedString(keySpec.getEncoded()),
                        algorithmType);
            }

            logger.info("Path to certificates and keys: {}", basePath);
        } finally {
            Files.delete(Paths.get(keyPath));
            Files.delete(Paths.get(crtPath));
        }
    }

    private void checkKeyPair(KeyPair keyPair) {
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }

    private X509CertificateHolder selfSignedCertificate(KeyPair keyPair, SignAlgorithmType signAlgorithm) {
        return CryptoUtil.selfSignedCertificate(TEST_CERTIFICATE_CN, keyPair, signAlgorithm, null, null);
    }

    private static byte[] getTestData() {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ru/i_novus/common/sign/test/smev/getRequestRequest.xml");
        return CryptoIO.getInstance().inputStreamToByteArray(inputStream);
    }
}
