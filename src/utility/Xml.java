package utility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Shared XML parser hardening helpers.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Xml {

    /**
     * Xerces feature that rejects document type declarations completely.
     */
    private static final String DISALLOW_DOCTYPE =
            "http://apache.org/xml/features/disallow-doctype-decl";

    /**
     * SAX feature that disables external general entities.
     */
    private static final String EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";

    /**
     * SAX feature that disables external parameter entities.
     */
    private static final String EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";

    /**
     * Xerces feature that disables loading external DTDs.
     */
    private static final String LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    /**
     * Error handler that prevents parser diagnostics from being written to
     * standard error while preserving failure behavior.
     */
    private static final ErrorHandler THROWING_ERROR_HANDLER = new ErrorHandler() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void warning(SAXParseException exception) throws SAXParseException {
            throw exception;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void error(SAXParseException exception) throws SAXParseException {
            throw exception;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void fatalError(SAXParseException exception) throws SAXParseException {
            throw exception;
        }
    };

    /**
     * Prevents instantiation.
     */
    private Xml() {
        // utility
    }

    /**
     * Parses UTF-8 XML text with external entities, external DTD loading, and
     * XInclude disabled.
     *
     * @param xmlText XML source text
     * @param failureMessage message for the wrapping exception
     * @return parsed DOM document
     */
    public static Document parseUtf8Document(String xmlText, String failureMessage) {
        try {
            DocumentBuilderFactory factory = secureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(THROWING_ERROR_HANDLER);
            byte[] bytes = xmlText.getBytes(StandardCharsets.UTF_8);
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (ParserConfigurationException | SAXException | IOException | IllegalArgumentException ex) {
            throw new IllegalArgumentException(failureMessage, ex);
        }
    }

    /**
     * Creates a DOM factory with XXE and external-resource access disabled.
     *
     * @return hardened document-builder factory
     * @throws ParserConfigurationException when the active XML provider cannot
     *         be hardened
     */
    private static DocumentBuilderFactory secureDocumentBuilderFactory()
            throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(DISALLOW_DOCTYPE, true);
        factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
        factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
        factory.setFeature(LOAD_EXTERNAL_DTD, false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }
}
