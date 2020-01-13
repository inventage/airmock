package com.inventage.airmock.kernel.util;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Response;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.*;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.parse.XMLParserException;
import org.opensaml.xml.util.Base64;
import org.opensaml.xml.util.XMLHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.util.List;

public final class SAMLUtils {

    public static final String SAML_RESPONSE_PARAM_KEY = "SAMLResponse";

    private static final Logger LOGGER = LoggerFactory.getLogger(SAMLUtils.class);

    static {
        try {
            // Initializes ParserPool and Marshallers and Unmarshallers
            DefaultBootstrap.bootstrap();
        }
        catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private SAMLUtils() {
        //NOP
    }

    /**
     * Checks if a given encoded Token is a SAML Token by trying to parse it.
     * @param encodedToken The base64 encoded token.
     * @return Whether or not the given encoded token is a SAML Token.
     */
    public static boolean isSAMLToken(String encodedToken) {
        try {
            getXMLObject(encodedToken);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the first assertion of a SAMLResponse and returns it.
     * @param encodedSamlObject The base64 encoded string SAMLResponse.
     * @return The first Assertion of the given SAMLRespone as a base64 encoded String.
     */
    public static String getEncodedAssertion(String encodedSamlObject) {
        try {
            final XMLObject xmlObject = getXMLObject(encodedSamlObject);

            if (xmlObject instanceof Assertion) {
                LOGGER.debug("getEncodedAssertion: XML Object was an Assertion");
                return getEncodedString((Assertion) xmlObject);
            }

            else if (xmlObject instanceof Response) {
                LOGGER.debug("getEncodedAssertion: XML Object was a Response");
                final Assertion samlAssertion = getAssertionFromResponse((Response) xmlObject);
                return getEncodedString(samlAssertion);
            }
            else {
                throw new IllegalArgumentException("was neither an assertion nor a response");
            }

        }
        catch (XMLParserException | UnmarshallingException | MarshallingException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Assertion getAssertionFromResponse(Response samlResponse) {
        final List<Assertion> assertions = samlResponse.getAssertions();

        if (assertions == null || assertions.isEmpty()) {
            LOGGER.error("getAssertionFromResponse: no assertions found in response");
            throw new IllegalArgumentException("no assertions found");
        }

        return assertions.get(0);
    }

    private static XMLObject getXMLObject(String encodedXMLObject) throws XMLParserException, UnmarshallingException {
        final BasicParserPool parser = new BasicParserPool();

        final byte[] encodedSamlObjectBytes = Base64.decode(encodedXMLObject);
        final Document encodedSamlDocument = parser.parse(new ByteArrayInputStream(encodedSamlObjectBytes));
        final Element encodedSamlElement = encodedSamlDocument.getDocumentElement();

        final UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
        final Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(encodedSamlElement);
        return unmarshaller.unmarshall(encodedSamlElement);
    }

    private static String getEncodedString(Assertion assertion) throws MarshallingException {
        final MarshallerFactory marshallerFactory = Configuration.getMarshallerFactory();
        final Marshaller marshaller = marshallerFactory.getMarshaller(assertion);

        final Element assertionElement = marshaller.marshall(assertion);
        final String assertionString = XMLHelper.nodeToString(assertionElement);
        return removeNewlines(Base64.encodeBytes(assertionString.getBytes()));
    }

    private static String removeNewlines(String input) {
        return input.replace("\n", "").replace("\r", "");
    }
}
