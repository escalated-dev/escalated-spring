package dev.escalated.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** A test identity provider: builds SAML responses and signs their assertion with a fixture key. */
final class SamlIdp {

    static final String ISSUER = "https://idp.example.com/saml";

    static {
        org.apache.xml.security.Init.init();
    }

    private SamlIdp() {
    }

    static String certificate(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/saml/" + name + "-cert.pem"));
    }

    static PrivateKey key(String name) throws Exception {
        String pem = Files.readString(Path.of("src/test/resources/saml/" + name + "-key.pem"))
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s+", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    /** The AuthnRequest ID inside a redirect-binding sign-in URL. */
    static String requestId(String location) throws Exception {
        Matcher query = Pattern.compile("SAMLRequest=([^&]+)").matcher(location);
        if (!query.find()) {
            throw new IllegalStateException("no SAMLRequest in " + location);
        }
        byte[] deflated = Base64.getDecoder().decode(java.net.URLDecoder.decode(query.group(1), StandardCharsets.UTF_8));
        Inflater inflater = new Inflater(true);
        inflater.setInput(deflated);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int read = inflater.inflate(buffer);
            if (read == 0 && inflater.needsInput()) {
                break;
            }
            out.write(buffer, 0, read);
        }
        Matcher id = Pattern.compile("ID=\"([^\"]+)\"").matcher(out.toString(StandardCharsets.UTF_8));
        if (!id.find()) {
            throw new IllegalStateException("no ID");
        }
        return id.group(1);
    }

    /** What the response says; each field can be changed by a test. */
    static final class Response {
        String issuer = ISSUER;
        String audience;
        String destination;
        String recipient;
        String inResponseTo;
        String email;
        String role = "";
        String assertionId = "_a" + java.util.UUID.randomUUID().toString().replace("-", "");
        Instant notBefore = Instant.now().minusSeconds(60);
        Instant notOnOrAfter = Instant.now().plusSeconds(300);
        String signWith = "idp";
        boolean sign = true;
        UnaryOperator<String> afterSigning = xml -> xml;

        String xml() {
            String irt = inResponseTo == null ? "" : " InResponseTo=\"" + inResponseTo + "\"";
            return "<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\""
                    + " xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" ID=\"_r" + assertionId + "\""
                    + " Version=\"2.0\" IssueInstant=\"" + Instant.now() + "\" Destination=\"" + destination + "\"" + irt + ">"
                    + "<saml:Issuer>" + issuer + "</saml:Issuer>"
                    + "<samlp:Status><samlp:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/></samlp:Status>"
                    + "<saml:Assertion ID=\"" + assertionId + "\" Version=\"2.0\" IssueInstant=\"" + Instant.now() + "\">"
                    + "<saml:Issuer>" + issuer + "</saml:Issuer>"
                    + "<saml:Subject><saml:NameID>" + email + "</saml:NameID>"
                    + "<saml:SubjectConfirmation Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
                    + "<saml:SubjectConfirmationData Recipient=\"" + recipient + "\" NotOnOrAfter=\"" + notOnOrAfter + "\"" + irt + "/>"
                    + "</saml:SubjectConfirmation></saml:Subject>"
                    + "<saml:Conditions NotBefore=\"" + notBefore + "\" NotOnOrAfter=\"" + notOnOrAfter + "\">"
                    + "<saml:AudienceRestriction><saml:Audience>" + audience + "</saml:Audience></saml:AudienceRestriction>"
                    + "</saml:Conditions>"
                    + "<saml:AttributeStatement>"
                    + "<saml:Attribute Name=\"email\"><saml:AttributeValue>" + email + "</saml:AttributeValue></saml:Attribute>"
                    + "<saml:Attribute Name=\"name\"><saml:AttributeValue>SSO User</saml:AttributeValue></saml:Attribute>"
                    + "<saml:Attribute Name=\"role\"><saml:AttributeValue>" + role + "</saml:AttributeValue></saml:Attribute>"
                    + "</saml:AttributeStatement></saml:Assertion></samlp:Response>";
        }

        /** The base64 SAMLResponse an IdP posts. */
        String encoded() throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml().getBytes(StandardCharsets.UTF_8)));
            if (sign) {
                Element assertion = (Element) document.getElementsByTagNameNS(
                        "urn:oasis:names:tc:SAML:2.0:assertion", "Assertion").item(0);
                assertion.setIdAttributeNS(null, "ID", true);
                XMLSignature signature = new XMLSignature(document, "", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                        Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
                // The signature goes after the assertion's Issuer, as SAML places it.
                assertion.insertBefore(signature.getElement(), assertion.getFirstChild().getNextSibling());
                Transforms transforms = new Transforms(document);
                transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
                transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
                signature.addDocument("#" + assertionId, transforms, "http://www.w3.org/2001/04/xmlenc#sha256");
                signature.sign(key(signWith));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(out));
            String signed = afterSigning.apply(out.toString(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signed.getBytes(StandardCharsets.UTF_8));
        }
    }
}
