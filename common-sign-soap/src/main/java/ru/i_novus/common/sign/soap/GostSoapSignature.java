package ru.i_novus.common.sign.soap;

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

import org.apache.commons.lang3.StringUtils;
import org.apache.xml.security.c14n.CanonicalizationException;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.c14n.InvalidCanonicalizerException;
import org.apache.xpath.XPathAPI;
import ru.i_novus.common.sign.api.SignAlgorithmType;
import ru.i_novus.common.sign.util.CryptoFormatConverter;
import ru.i_novus.common.sign.util.CryptoUtil;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static ru.i_novus.common.sign.util.Base64Util.getBase64EncodedString;

public class GostSoapSignature {

    public static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    public static final String WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    public static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";
    public static final String BASE64_ENCODING = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";
    public static final String X509_V3_TYPE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3";

    private GostSoapSignature() {
        // не позволяет создать экземпляр класса, класс утилитный
    }

    public static void addSecurityElement(SOAPMessage message, String encodedCertificate, String actor, SignAlgorithmType signAlgorithmType) throws SOAPException {
        // Добавляем элемент Security
        SOAPElement security;
        if (StringUtils.isBlank(actor)) {
            security = message.getSOAPHeader().addChildElement("Security", "wsse");
        } else {
            security = message.getSOAPHeader().addHeaderElement(new QName(WSSE_NS, "Security", "wsse"));
            ((SOAPHeaderElement) security).setActor(actor);
        }
        // Добавляем элемент Signature
        SOAPElement signature = security.addChildElement("Signature", "ds");
        // Добавляем элемент SignedInfo
        SOAPElement signedInfo = signature.addChildElement("SignedInfo", "ds");
        // Добавляем элемент CanonicalizationMethod
        signedInfo.addChildElement("CanonicalizationMethod", "ds")
                .setAttribute("Algorithm", Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        // Добавляем элемент SignatureMethod
        signedInfo.addChildElement("SignatureMethod", "ds")
                .setAttribute("Algorithm", signAlgorithmType.getSignUri());
        // Добавляем элемент Reference
        SOAPElement referenceSignedInfo = signedInfo.addChildElement("Reference", "ds")
                .addAttribute(new QName("URI"), "#body");
        // Добавляем элементы Transforms и Transform
        referenceSignedInfo.addChildElement("Transforms", "ds")
                .addChildElement("Transform", "ds")
                .setAttribute("Algorithm", Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        // Добавляем элемент DigestMethod
        referenceSignedInfo.addChildElement("DigestMethod", "ds")
                .setAttribute("Algorithm", signAlgorithmType.getDigestUri());
        // Добавляем элемент DigestValue (значение хэша считаем позже)
        referenceSignedInfo.addChildElement("DigestValue", "ds");
        // Добавляем элемент SignatureValue (значение ЭЦП считаем позже)
        signature.addChildElement("SignatureValue", "ds");
        // Добавляем элементы KeyInfo, SecurityTokenReference и Reference
        signature.addChildElement("KeyInfo", "ds")
                .addChildElement("SecurityTokenReference", "wsse")
                .addChildElement("Reference", "wsse")
                .addAttribute(new QName("URI"), "#CertId")
                .addAttribute(new QName("ValueType"), X509_V3_TYPE);
        // Добавляем элемент BinarySecurityToken
        security.addChildElement("BinarySecurityToken", "wsse")
                .addAttribute(new QName("EncodingType"), BASE64_ENCODING)
                .addAttribute(new QName("ValueType"), X509_V3_TYPE)
                .addAttribute(new QName("wsu:Id"), "CertId")
                .addTextNode(encodedCertificate);
    }

    public static void addSecurityElement(SOAPMessage message, X509Certificate certificate, String actor)
            throws SOAPException {
        addSecurityElement(message, CryptoFormatConverter.getInstance().getPEMEncodedCertificate(certificate), actor, SignAlgorithmType.findByCertificate(certificate));
    }

    public static void sign(SOAPMessage message, String encodedPrivateKey, SignAlgorithmType signAlgorithmType) throws IOException,
            SOAPException, TransformerException, InvalidCanonicalizerException, CanonicalizationException, GeneralSecurityException {

        PrivateKey privateKey = CryptoFormatConverter.getInstance().getPKFromPEMEncoded(signAlgorithmType, encodedPrivateKey);
        sign(message, privateKey, signAlgorithmType);
    }

    public static void sign(SOAPMessage message, PrivateKey privateKey, SignAlgorithmType signAlgorithmType) throws IOException,
            SOAPException, TransformerException, InvalidCanonicalizerException, CanonicalizationException, GeneralSecurityException {
        // Сохраняем изменения
        message.saveChanges();
        // Делаем такое преобразование, чтобы не поломался в последующем хэш для Body
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            message.writeTo(outputStream);
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray())) {
                message.getSOAPPart().setContent(new StreamSource(inputStream));
            }
        }

        ByteArrayOutputStream tempBuffer = new ByteArrayOutputStream();

        //Считаем хэш после всех манипуляций с Body
        Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS).canonicalizeSubtree(message.getSOAPBody(), tempBuffer);
        final String digestValue = CryptoUtil.getBase64Digest(new String(tempBuffer.toByteArray()), signAlgorithmType);

        ((SOAPElement) XPathAPI.selectSingleNode(message.getSOAPHeader(), "//*[local-name()='DigestValue']"))
                .addTextNode(digestValue);

        //Считаем подпись после всех манипуляций с SignedInfo
        tempBuffer.reset();
        Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS)
                .canonicalizeSubtree(XPathAPI.selectSingleNode(message.getSOAPHeader(),
                        "//*[local-name()='SignedInfo']"), tempBuffer);
        byte[] signature = CryptoUtil.getSignature(tempBuffer.toByteArray(), privateKey, signAlgorithmType);

        ((SOAPElement) XPathAPI.selectSingleNode(message.getSOAPHeader(), "//*[local-name()='SignatureValue']"))
                .addTextNode(getBase64EncodedString(signature));
    }
}
