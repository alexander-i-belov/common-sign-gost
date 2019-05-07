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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.NodeList;
import ru.i_novus.common.sign.Init;
import ru.i_novus.common.sign.api.SignAlgorithmType;
import ru.i_novus.common.sign.smev.Smev2RequestSigner;
import ru.i_novus.common.sign.util.CryptoFormatConverter;
import ru.i_novus.common.sign.util.CryptoUtil;

import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static ru.i_novus.common.sign.test.SoapUtil.getSoapMessageContent;

@Slf4j
public class Smev2SignTest {
    @BeforeClass
    public static void init() {
        Init.init();
    }

    @Test
    public void testSignSmev2Request2001() {
        testSignSmev2Request(SignAlgorithmType.ECGOST3410);
    }

    @Test
    public void testSignSmev2RequestGost2012_256() {
        testSignSmev2Request(SignAlgorithmType.ECGOST3410_2012_256);
    }

    @Test
    public void testSignSmev2RequestGost2012_512() {
        testSignSmev2Request(SignAlgorithmType.ECGOST3410_2012_512);
    }

    private void testSignSmev2Request(SignAlgorithmType algorithm) {
        for (String specName : algorithm.getAvailableParameterSpecificationNames()) {
            KeyPair keyPair = CryptoUtil.generateKeyPair(algorithm, specName);
            X509CertificateHolder certificateHolder = CryptoUtil.selfSignedCertificate(CryptoTest.TEST_CERTIFICATE_CN, keyPair, algorithm, null, null);
            testSignSmev2Request(keyPair.getPrivate(), CryptoFormatConverter.getInstance().getCertificateFromHolder(certificateHolder));
        }
    }

    @SneakyThrows
    private void testSignSmev2Request(PrivateKey privateKey, X509Certificate certificate) {
        SOAPMessage message = getSmev2Request();
        logger.info("SMEV2 Request message before signature: {}", getSoapMessageContent(message));
        Smev2RequestSigner.signSmevRequest(message, privateKey, certificate);

        logger.info("SMEV2 Request message after signature: {}", getSoapMessageContent(message));
        checkSignedMessage(message);
    }

    private SOAPMessage getSmev2Request() {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ru/i_novus/common/sign/test/smev/smev2Request.xml");
        return SoapUtil.constructMessage(inputStream, SOAPConstants.SOAP_1_1_PROTOCOL);
    }

    private void checkSignedMessage(SOAPMessage message) throws SOAPException {
        assertNotNull(message);
        NodeList nodes = message.getSOAPHeader().getElementsByTagNameNS("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd",
                "BinarySecurityToken");
        assertTrue(nodes.getLength() > 0);
    }
}
