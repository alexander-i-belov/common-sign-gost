package org.apache.jcp.xml.dsig.internal.dom;

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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.SignatureException;
import java.security.spec.AlgorithmParameterSpec;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.XMLSignContext;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLValidateContext;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;
import org.w3c.dom.Element;

/**
 * An abstract class representing a SignatureMethod. Subclasses implement
 * a specific XML DSig signature algorithm.
 */
abstract class AbstractDOMSignatureMethod extends BaseStructure
        implements SignatureMethod {

    // denotes the type of signature algorithm
    enum Type { DSA, RSA, ECDSA, HMAC, ECGOST3410, ECGOST3410_2012 }

    /**
     * Verifies the passed-in signature with the specified key, using the
     * underlying Signature or Mac algorithm.
     *
     * @param key the verification key
     * @param si the SignedInfo
     * @param sig the signature bytes to be verified
     * @param context the XMLValidateContext
     * @return <code>true</code> if the signature verified successfully,
     *    <code>false</code> if not
     * @throws NullPointerException if <code>key</code>, <code>si</code> or
     *    <code>sig</code> are <code>null</code>
     * @throws InvalidKeyException if the key is improperly encoded, of
     *    the wrong type, or parameters are missing, etc
     * @throws SignatureException if an unexpected error occurs, such
     *    as the passed in signature is improperly encoded
     * @throws XMLSignatureException if an unexpected error occurs
     */
    abstract boolean verify(Key key, DOMSignedInfo si, byte[] sig,
                            XMLValidateContext context)
            throws InvalidKeyException, SignatureException, XMLSignatureException;

    /**
     * Signs the bytes with the specified key, using the underlying
     * Signature or Mac algorithm.
     *
     * @param key the signing key
     * @param si the SignedInfo
     * @param context the XMLSignContext
     * @return the signature
     * @throws NullPointerException if <code>key</code> or
     *    <code>si</code> are <code>null</code>
     * @throws InvalidKeyException if the key is improperly encoded, of
     *    the wrong type, or parameters are missing, etc
     * @throws XMLSignatureException if an unexpected error occurs
     */
    abstract byte[] sign(Key key, DOMSignedInfo si, XMLSignContext context)
            throws InvalidKeyException, XMLSignatureException;

    /**
     * Returns the java.security.Signature or javax.crypto.Mac standard
     * algorithm name.
     */
    abstract String getJCAAlgorithm();

    /**
     * Returns the type of signature algorithm.
     */
    abstract Type getAlgorithmType();

    /**
     * This method invokes the {@link #marshalParams marshalParams}
     * method to marshal any algorithm-specific parameters.
     */
    public void marshal(XmlWriter xwriter, String dsPrefix)
            throws MarshalException
    {
        xwriter.writeStartElement(dsPrefix, "SignatureMethod", XMLSignature.XMLNS);
        xwriter.writeAttribute("", "", "Algorithm", getAlgorithm());

        if (getParameterSpec() != null) {
            marshalParams(xwriter, dsPrefix);
        }
        xwriter.writeEndElement(); // "SignatureMethod"
    }

    /**
     * Marshals the algorithm-specific parameters to an Element and
     * appends it to the specified parent element. By default, this method
     * throws an exception since most SignatureMethod algorithms do not have
     * parameters. Subclasses should override it if they have parameters.
     *
     * @param xwriter XmlWriter instance to modify
     * @param paramsPrefix the algorithm parameters prefix to use
     * @throws MarshalException if the parameters cannot be marshalled
     */
    void marshalParams(XmlWriter xwriter, String paramsPrefix)
            throws MarshalException
    {
        throw new MarshalException("no parameters should " +
                "be specified for the " + getAlgorithm() +
                " SignatureMethod algorithm");
    }

    /**
     * Unmarshals <code>SignatureMethodParameterSpec</code> from the specified
     * <code>Element</code>. By default, this method throws an exception since
     * most SignatureMethod algorithms do not have parameters. Subclasses should
     * override it if they have parameters.
     *
     * @param paramsElem the <code>Element</code> holding the input params
     * @return the algorithm-specific <code>SignatureMethodParameterSpec</code>
     * @throws MarshalException if the parameters cannot be unmarshalled
     */
    SignatureMethodParameterSpec unmarshalParams(Element paramsElem)
            throws MarshalException
    {
        throw new MarshalException("no parameters should " +
                "be specified for the " + getAlgorithm() +
                " SignatureMethod algorithm");
    }

    /**
     * Checks if the specified parameters are valid for this algorithm. By
     * default, this method throws an exception if parameters are specified
     * since most SignatureMethod algorithms do not have parameters. Subclasses
     * should override it if they have parameters.
     *
     * @param params the algorithm-specific params (may be <code>null</code>)
     * @throws InvalidAlgorithmParameterException if the parameters are not
     *    appropriate for this signature method
     */
    void checkParams(SignatureMethodParameterSpec params)
            throws InvalidAlgorithmParameterException
    {
        if (params != null) {
            throw new InvalidAlgorithmParameterException("no parameters " +
                    "should be specified for the " + getAlgorithm() +
                    " SignatureMethod algorithm");
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }

        if (!(o instanceof SignatureMethod)) {
            return false;
        }
        SignatureMethod osm = (SignatureMethod)o;

        return getAlgorithm().equals(osm.getAlgorithm()) &&
                paramsEqual(osm.getParameterSpec());
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + getAlgorithm().hashCode();
        AlgorithmParameterSpec spec = getParameterSpec();
        if (spec != null) {
            result = 31 * result + spec.hashCode();
        }

        return result;
    }

    /**
     * Returns true if parameters are equal; false otherwise.
     *
     * Subclasses should override this method to compare algorithm-specific
     * parameters.
     */
    boolean paramsEqual(AlgorithmParameterSpec spec)
    {
        return getParameterSpec() == spec;
    }
}
