package ru.i_novus.common.sign;

import org.apache.commons.lang3.StringUtils;
import org.apache.xml.security.c14n.CanonicalizationException;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.c14n.InvalidCanonicalizerException;
import org.apache.xpath.XPathAPI;
import ru.i_novus.common.sign.api.SignAlgorithmType;
import ru.i_novus.common.sign.exception.CommonSignFailureException;
import ru.i_novus.common.sign.util.CryptoFormatConverter;
import ru.i_novus.common.sign.util.CryptoUtil;

import javax.xml.namespace.QName;
import javax.xml.soap.*;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static ru.i_novus.common.sign.util.Base64Util.getBase64EncodedString;

public class GostXmlSignature {

    public static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    public static final String WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    public static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";
    public static final String BASE64_ENCODING = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";
    public static final String X509_V3_TYPE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3";

    private GostXmlSignature() {
        // не позволяет создать экземпляр класса, класс утилитный
    }

    public static void addSecurityElement(SOAPMessage message, String encodedCertificate, String actor, SignAlgorithmType signAlgorithmType) throws CommonSignFailureException {
        // Добавляем элемент Security
        SOAPElement security;

        try {

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

        } catch (SOAPException | RuntimeException ex) {
            throw new CommonSignFailureException(ex);
        }
    }

    public static void addSecurityElement(SOAPMessage message, X509Certificate certificate, String actor) throws CommonSignFailureException {
        addSecurityElement(message, CryptoFormatConverter.getInstance().getPEMEncodedCertificate(certificate), actor, SignAlgorithmType.findByCertificate(certificate));
    }

    public static void sign(SOAPMessage message, String encodedPrivateKey, SignAlgorithmType signAlgorithmType) throws CommonSignFailureException {
        PrivateKey privateKey = CryptoFormatConverter.getInstance().getPKFromPEMEncoded(signAlgorithmType, encodedPrivateKey);
        sign(message, privateKey, signAlgorithmType);
    }

    public static void sign(SOAPMessage message, PrivateKey privateKey, SignAlgorithmType signAlgorithmType) throws CommonSignFailureException {
        try {

            // Сохраняем изменения
            message.saveChanges();

            // Делаем такое преобразование, чтобы не поломался в последующем хэш для Body
            messageReset(message);

            SOAPHeader soapHeader = message.getSOAPHeader();
            SOAPBody soapBody = message.getSOAPBody();

            //  ВАЖНО: Считаем хэш после всех манипуляций с Body
            addDigestValue(soapHeader, soapBody, signAlgorithmType);

            byte[] signature = CryptoUtil.getSignature(Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS)
                    .canonicalizeSubtree(XPathAPI.selectSingleNode(message.getSOAPHeader(),
                            "//*[local-name()='SignedInfo']")), privateKey, signAlgorithmType);

            // ВАЖНО: Считаем подпись после всех манипуляций с SignedInfo
            ((SOAPElement) XPathAPI.selectSingleNode(message.getSOAPHeader(), "//*[local-name()='SignatureValue']"))
                    .addTextNode(getBase64EncodedString(signature));

        } catch (TransformerException | InvalidCanonicalizerException | CanonicalizationException | SOAPException | IOException | RuntimeException e) {
            throw new CommonSignFailureException(e);
        }
    }

    private static void messageReset(SOAPMessage message) throws IOException, SOAPException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            message.writeTo(outputStream);
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray())) {
                message.getSOAPPart().setContent(new StreamSource(inputStream));
            }
        }
    }

    private static void addDigestValue(SOAPHeader soapHeader, SOAPBody soapBody, SignAlgorithmType signAlgorithmType) throws TransformerException, InvalidCanonicalizerException, CanonicalizationException, SOAPException {
        ((SOAPElement) XPathAPI.selectSingleNode(soapHeader, "//*[local-name()='DigestValue']"))
                .addTextNode(CryptoUtil.getBase64Digest(
                        new String(Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS)
                                .canonicalizeSubtree(soapBody)), signAlgorithmType));
    }
}
