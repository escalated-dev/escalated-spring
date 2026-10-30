package dev.escalated.services;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationRequest;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretPost;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.security.AccessFeatures;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Single sign-on through the provider an admin picks in Admin > Settings > SSO (ADR
 * 2026-10-29-configurable-access-features): SAML 2 (signatures through Apache Santuario), OAuth 2 /
 * OIDC authorization code with PKCE (Nimbus OAuth 2.0 SDK) and JWT (Nimbus JOSE). Each validate
 * method returns the same {@link Identity}, or throws {@link SsoException} with the reason, which is
 * logged and never shown.
 */
@Service
public class SsoService {

    /** Why a sign-in was refused: for the log, never for the user. */
    public static class SsoException extends RuntimeException {
        public SsoException(String message) {
            super(message);
        }
    }

    public record Identity(String email, String name, String role) {
    }

    /** The {@code sso_*} settings and their defaults, as escalated-laravel's SsoService. */
    public static final Map<String, String> DEFAULTS = defaults();

    public static final List<String> SECRET_KEYS = List.of("sso_jwt_secret", "sso_oauth_client_secret");
    public static final List<String> JWT_ALGORITHMS = List.of("HS256", "HS384", "HS512", "RS256", "RS384", "RS512");
    public static final List<String> PROVIDERS = List.of("none", "saml", "oauth", "jwt");

    /** How long an AuthnRequest ID stays answerable, in seconds. */
    public static final long SAML_REQUEST_TTL = 600;

    static final String SAML = "urn:oasis:names:tc:SAML:2.0:assertion";
    static final String SAMLP = "urn:oasis:names:tc:SAML:2.0:protocol";
    static final String DSIG = "http://www.w3.org/2000/09/xmldsig#";
    private static final String BEARER = "urn:oasis:names:tc:SAML:2.0:cm:bearer";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> ALLOWED_TRANSFORMS = Set.of(
            "http://www.w3.org/2000/09/xmldsig#enveloped-signature",
            "http://www.w3.org/2001/10/xml-exc-c14n#",
            "http://www.w3.org/2001/10/xml-exc-c14n#WithComments",
            "http://www.w3.org/TR/2001/REC-xml-c14n-20010315");

    private static final Logger log = LoggerFactory.getLogger(SsoService.class);

    static {
        org.apache.xml.security.Init.init();
    }

    private final SettingsService settings;
    private final SsoMessageIds messageIds;
    private final AccessFeatures features;
    private final EscalatedProperties properties;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();

    public SsoService(SettingsService settings, SsoMessageIds messageIds, AccessFeatures features,
                      EscalatedProperties properties, Environment environment) {
        this.settings = settings;
        this.messageIds = messageIds;
        this.features = features;
        this.properties = properties;
        this.environment = environment;
    }

    private static Map<String, String> defaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("sso_provider", "none");
        defaults.put("sso_entity_id", "");
        defaults.put("sso_url", "");
        defaults.put("sso_login_url", "");
        defaults.put("sso_logout_url", "");
        defaults.put("sso_metadata_url", "");
        defaults.put("sso_certificate", "");
        defaults.put("sso_attr_email", "email");
        defaults.put("sso_attr_name", "name");
        defaults.put("sso_attr_role", "role");
        defaults.put("sso_jwt_secret", "");
        defaults.put("sso_jwt_algorithm", "HS256");
        defaults.put("sso_oauth_authorize_url", "");
        defaults.put("sso_oauth_token_url", "");
        defaults.put("sso_oauth_userinfo_url", "");
        defaults.put("sso_oauth_client_id", "");
        defaults.put("sso_oauth_client_secret", "");
        defaults.put("sso_oauth_scopes", "openid profile email");
        return java.util.Collections.unmodifiableMap(defaults);
    }

    public boolean enabled() {
        return features.sso();
    }

    public Map<String, String> config() {
        Map<String, String> config = new LinkedHashMap<>();
        DEFAULTS.forEach((key, fallback) -> {
            String value;
            try {
                value = settings.get(key).orElse(null);
            } catch (RuntimeException unavailable) {
                value = null;
            }
            config.put(key, value == null ? fallback : value);
        });
        String legacy = config.get("sso_url").trim();
        if (!legacy.isEmpty() && config.get("sso_login_url").isBlank()) {
            config.put("sso_login_url", legacy);
        }
        return config;
    }

    public void save(Map<String, String> values) {
        values.forEach((key, value) -> {
            if (DEFAULTS.containsKey(key)) {
                settings.set(key, value == null ? "" : value, "sso");
            }
        });
    }

    public String provider() {
        String provider = config().get("sso_provider");
        return PROVIDERS.contains(provider) ? provider : "none";
    }

    // ── Install-level rules ─────────────────────────────────────────────

    private String setting(String configured, String env) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String value = environment.getProperty(env);
        return value == null || value.isBlank() ? null : value;
    }

    private boolean flag(Boolean configured, String env) {
        if (configured != null) {
            return configured;
        }
        String value = environment.getProperty(env);
        return value != null && Set.of("1", "true", "yes", "on").contains(value.trim().toLowerCase(Locale.ROOT));
    }

    public long clockSkew() {
        Integer configured = properties.getSso().getClockSkew();
        String raw = configured != null ? String.valueOf(configured) : environment.getProperty("ESCALATED_SSO_CLOCK_SKEW");
        try {
            long skew = raw == null ? 120 : Long.parseLong(raw.trim());
            return skew < 0 ? 120 : Math.min(skew, 300);
        } catch (NumberFormatException invalid) {
            return 120;
        }
    }

    public boolean allowIdpInitiated() {
        return flag(properties.getSso().getAllowIdpInitiated(), "ESCALATED_SSO_ALLOW_IDP_INITIATED");
    }

    public boolean createUsers() {
        return flag(properties.getSso().getCreateUsers(), "ESCALATED_SSO_CREATE_USERS");
    }

    public boolean syncRoles() {
        return flag(properties.getSso().getSyncRoles(), "ESCALATED_SSO_SYNC_ROLES");
    }

    public String redirectTo() {
        String configured = setting(properties.getSso().getRedirectTo(), "ESCALATED_SSO_REDIRECT_TO");
        return configured == null ? "/escalated/agent" : configured;
    }

    public String baseUrl(HttpServletRequest request) {
        String configured = setting(properties.getSso().getBaseUrl(), "ESCALATED_SSO_BASE_URL");
        String base = configured != null ? configured : ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        return base.replaceAll("/+$", "");
    }

    public String url(HttpServletRequest request, String path) {
        return baseUrl(request) + "/escalated/sso" + path;
    }

    public String spEntityId(HttpServletRequest request) {
        String configured = setting(properties.getSso().getSpEntityId(), "ESCALATED_SSO_SP_ENTITY_ID");
        return configured != null ? configured : url(request, "/saml/metadata");
    }

    public String jwtAudience(HttpServletRequest request) {
        String configured = setting(properties.getSso().getJwtAudience(), "ESCALATED_SSO_JWT_AUDIENCE");
        return configured != null ? configured : baseUrl(request);
    }

    /** The URLs an admin gives the identity provider. */
    public Map<String, String> endpoints(HttpServletRequest request) {
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("login", url(request, "/login"));
        endpoints.put("sp_entity_id", spEntityId(request));
        endpoints.put("metadata", url(request, "/saml/metadata"));
        endpoints.put("acs", url(request, "/saml/acs"));
        endpoints.put("oauth_redirect", url(request, "/oauth/callback"));
        endpoints.put("jwt_callback", url(request, "/jwt/callback"));
        return endpoints;
    }

    // ── SAML ────────────────────────────────────────────────────────────

    /**
     * The IdP sign-in URL for a new AuthnRequest (HTTP-Redirect binding). Its ID is stored server
     * side and must come back as {@code InResponseTo} within {@link #SAML_REQUEST_TTL} seconds.
     */
    public String samlLoginUrl(HttpServletRequest request) {
        Map<String, String> config = config();
        idpKey(config.get("sso_certificate"));
        String idpUrl = config.get("sso_login_url").trim();
        if (idpUrl.isEmpty()) {
            throw new SsoException("SAML refused: no IdP sign-in URL is configured.");
        }
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        String id = "_" + HexFormat.of().formatHex(bytes);
        messageIds.remember("saml_request", id, Instant.now().getEpochSecond() + SAML_REQUEST_TTL);
        String xml = "<samlp:AuthnRequest xmlns:samlp=\"" + SAMLP + "\" xmlns:saml=\"" + SAML + "\""
                + " ID=\"" + id + "\" Version=\"2.0\" IssueInstant=\"" + Instant.now() + "\""
                + " Destination=\"" + escape(idpUrl) + "\""
                + " ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\""
                + " AssertionConsumerServiceURL=\"" + escape(url(request, "/saml/acs")) + "\">"
                + "<saml:Issuer>" + escape(spEntityId(request)) + "</saml:Issuer>"
                + "<samlp:NameIDPolicy Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified\""
                + " AllowCreate=\"true\"/></samlp:AuthnRequest>";
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(xml.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        String encoded = URLEncoder.encode(Base64.getEncoder().encodeToString(out.toByteArray()), StandardCharsets.UTF_8);
        return idpUrl + (idpUrl.contains("?") ? "&" : "?") + "SAMLRequest=" + encoded;
    }

    public String samlMetadata(HttpServletRequest request) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\" entityID=\""
                + escape(spEntityId(request)) + "\">"
                + "<md:SPSSODescriptor AuthnRequestsSigned=\"false\" WantAssertionsSigned=\"true\""
                + " protocolSupportEnumeration=\"" + SAMLP + "\">"
                + "<md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified</md:NameIDFormat>"
                + "<md:AssertionConsumerService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\""
                + " Location=\"" + escape(url(request, "/saml/acs")) + "\" index=\"1\"/>"
                + "</md:SPSSODescriptor></md:EntityDescriptor>";
    }

    /**
     * Validates a base64 SAMLResponse posted to the ACS. Accepted only when the IdP certificate is
     * configured; the single assertion carries a signature by it over the whole assertion (a key in
     * KeyInfo is never trusted); Issuer is {@code sso_entity_id}; Audience names our SP entity ID;
     * Destination and the bearer Recipient are our ACS URL; the validity windows hold; InResponseTo
     * names a pending request (or IdP-initiated sign-in is on); and the assertion ID is new.
     */
    public Identity validateSamlResponse(HttpServletRequest request, String samlResponse) {
        Map<String, String> config = config();
        PublicKey key = idpKey(config.get("sso_certificate"));
        String entityId = config.get("sso_entity_id").trim();
        if (entityId.isEmpty()) {
            throw new SsoException("SAML refused: no IdP entity ID is configured.");
        }
        String spEntityId = spEntityId(request);
        String acs = url(request, "/saml/acs");
        long skew = clockSkew();
        long now = Instant.now().getEpochSecond();

        byte[] xml;
        try {
            xml = Base64.getMimeDecoder().decode(samlResponse == null ? "" : samlResponse.trim());
        } catch (IllegalArgumentException malformed) {
            throw new SsoException("SAML refused: the response is not base64.");
        }
        Document document = parse(xml);
        if (document.getElementsByTagNameNS(SAML, "EncryptedAssertion").getLength() > 0) {
            throw new SsoException("SAML refused: encrypted assertions are not supported.");
        }
        NodeList assertions = document.getElementsByTagNameNS(SAML, "Assertion");
        if (assertions.getLength() != 1) {
            throw new SsoException("SAML refused: the response must carry exactly one assertion.");
        }
        Element root = document.getDocumentElement();
        Element assertion = (Element) assertions.item(0);
        verifySignature(document, assertion, key);

        String issuer = text(first(assertion, SAML, "Issuer"));
        if (!entityId.equals(issuer)) {
            throw new SsoException("SAML refused: Issuer [" + issuer + "] is not [" + entityId + "].");
        }
        Element conditions = first(assertion, SAML, "Conditions");
        if (conditions == null) {
            throw new SsoException("SAML refused: the assertion has no Conditions.");
        }
        Long conditionsUntil = window(conditions, now, skew, "assertion");
        audience(conditions, spEntityId);

        boolean isResponse = is(root, SAMLP, "Response");
        if (isResponse && !acs.equals(root.getAttribute("Destination"))) {
            throw new SsoException("SAML refused: Destination [" + root.getAttribute("Destination") + "] is not [" + acs + "].");
        }
        String[] bearer = bearer(assertion, acs, now, skew);
        String inResponseTo = bearer[0];
        String outer = isResponse && !root.getAttribute("InResponseTo").isEmpty() ? root.getAttribute("InResponseTo") : null;
        if (outer != null && inResponseTo != null && !outer.equals(inResponseTo)) {
            throw new SsoException("SAML refused: the Response and assertion answer different requests.");
        }
        if (inResponseTo == null) {
            inResponseTo = outer;
        }
        if (inResponseTo != null) {
            if (!messageIds.consume("saml_request", inResponseTo)) {
                throw new SsoException("SAML refused: InResponseTo [" + inResponseTo + "] is not a pending request.");
            }
        } else if (!allowIdpInitiated()) {
            throw new SsoException("SAML refused: unsolicited response (no InResponseTo) and IdP-initiated sign-in is off.");
        }

        String assertionId = assertion.getAttribute("ID");
        long until = Math.max(conditionsUntil == null ? 0 : conditionsUntil, Long.parseLong(bearer[1])) + skew;
        if (assertionId.isEmpty() || !messageIds.claim("saml_assertion", assertionId, until)) {
            throw new SsoException("SAML refused: assertion [" + assertionId + "] was already used.");
        }

        Map<String, String> attributes = attributes(assertion);
        String email = attributes.getOrDefault(or(config.get("sso_attr_email"), "email"), "");
        if (email.isEmpty()) {
            String nameId = text(firstDescendant(assertion, SAML, "NameID"));
            if (EMAIL.matcher(nameId).matches()) {
                email = nameId;
            }
        }
        return identity(email, attributes.getOrDefault(or(config.get("sso_attr_name"), "name"), ""),
                attributes.getOrDefault(or(config.get("sso_attr_role"), "role"), ""));
    }

    /**
     * Checks the assertion's own signature with the configured key (never KeyInfo): exactly one
     * reference, to the whole assertion by its ID, which is unique in the document, through the
     * enveloped-signature and canonicalisation transforms only.
     */
    private static void verifySignature(Document document, Element assertion, PublicKey key) {
        List<Element> signatures = children(assertion, DSIG, "Signature");
        if (signatures.isEmpty()) {
            throw new SsoException("SAML refused: the assertion is not signed.");
        }
        String id = assertion.getAttribute("ID");
        if (id.isEmpty() || countIds(document.getDocumentElement(), id) != 1) {
            throw new SsoException("SAML refused: the assertion ID is missing or not unique.");
        }
        assertion.setIdAttributeNS(null, "ID", true);
        for (Element element : signatures) {
            try {
                XMLSignature signature = new XMLSignature(element, "", true);
                if (signature.getSignedInfo().getLength() != 1) {
                    continue;
                }
                Reference reference = signature.getSignedInfo().item(0);
                if (!("#" + id).equals(reference.getURI())) {
                    continue;
                }
                boolean plainTransforms = true;
                if (reference.getTransforms() != null) {
                    for (int i = 0; i < reference.getTransforms().getLength(); i++) {
                        if (!ALLOWED_TRANSFORMS.contains(reference.getTransforms().item(i).getURI())) {
                            plainTransforms = false;
                        }
                    }
                }
                if (plainTransforms && signature.checkSignatureValue(key)) {
                    return;
                }
            } catch (Exception invalid) {
                // Try the next signature; none verifying is refused below.
            }
        }
        throw new SsoException("SAML refused: the assertion signature does not verify against the IdP certificate.");
    }

    private static int countIds(Element element, String id) {
        int count = id.equals(element.getAttribute("ID")) ? 1 : 0;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element nested) {
                count += countIds(nested, id);
            }
        }
        return count;
    }

    private static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            if (document.getDocumentElement() == null) {
                throw new SsoException("SAML refused: malformed XML.");
            }
            return document;
        } catch (SsoException refused) {
            throw refused;
        } catch (Exception malformed) {
            throw new SsoException("SAML refused: malformed XML.");
        }
    }

    private static PublicKey idpKey(String certificate) {
        String raw = certificate == null ? "" : certificate.trim();
        if (raw.isEmpty()) {
            throw new SsoException("SAML refused: no IdP certificate is configured.");
        }
        if (raw.contains("PRIVATE KEY")) {
            throw new SsoException("SAML refused: the IdP certificate must not be a private key.");
        }
        String body = raw.replaceAll("-----(BEGIN|END) CERTIFICATE-----", "").replaceAll("\\s+", "");
        try {
            X509Certificate parsed = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(body)));
            return parsed.getPublicKey();
        } catch (Exception unreadable) {
            throw new SsoException("SAML refused: the IdP certificate could not be read.");
        }
    }

    private static boolean is(Node node, String namespace, String localName) {
        return node != null && namespace.equals(node.getNamespaceURI()) && localName.equals(node.getLocalName());
    }

    private static List<Element> children(Element parent, String namespace, String localName) {
        List<Element> found = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && is(element, namespace, localName)) {
                found.add(element);
            }
        }
        return found;
    }

    private static Element first(Element parent, String namespace, String localName) {
        List<Element> found = children(parent, namespace, localName);
        return found.isEmpty() ? null : found.get(0);
    }

    private static Element firstDescendant(Element parent, String namespace, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static String text(Element element) {
        return element == null || element.getTextContent() == null ? "" : element.getTextContent().trim();
    }

    private static long time(String value, String attribute) {
        try {
            return OffsetDateTime.parse(value).toEpochSecond();
        } catch (RuntimeException invalid) {
            try {
                return Instant.parse(value).getEpochSecond();
            } catch (RuntimeException alsoInvalid) {
                throw new SsoException("SAML refused: " + attribute + " is not a time.");
            }
        }
    }

    private static Long window(Element element, long now, long skew, String what) {
        String notBefore = element.getAttribute("NotBefore");
        if (!notBefore.isEmpty() && time(notBefore, "NotBefore") > now + skew) {
            throw new SsoException("SAML refused: the " + what + " is not yet valid.");
        }
        String notOnOrAfter = element.getAttribute("NotOnOrAfter");
        if (notOnOrAfter.isEmpty()) {
            return null;
        }
        long until = time(notOnOrAfter, "NotOnOrAfter");
        if (now >= until + skew) {
            throw new SsoException("SAML refused: the " + what + " has expired.");
        }
        return until;
    }

    private static void audience(Element conditions, String spEntityId) {
        List<Element> restrictions = children(conditions, SAML, "AudienceRestriction");
        if (restrictions.isEmpty()) {
            throw new SsoException("SAML refused: the assertion names no Audience.");
        }
        for (Element restriction : restrictions) {
            boolean named = children(restriction, SAML, "Audience").stream()
                    .anyMatch(audience -> spEntityId.equals(text(audience)));
            if (!named) {
                throw new SsoException("SAML refused: [" + spEntityId + "] is not an Audience of the assertion.");
            }
        }
    }

    /** {inResponseTo or null, notOnOrAfter} of the first bearer confirmation that holds. */
    private static String[] bearer(Element assertion, String acs, long now, long skew) {
        Element subject = first(assertion, SAML, "Subject");
        List<Element> bearers = new ArrayList<>();
        if (subject != null) {
            for (Element confirmation : children(subject, SAML, "SubjectConfirmation")) {
                if (BEARER.equals(confirmation.getAttribute("Method"))) {
                    bearers.add(confirmation);
                }
            }
        }
        if (bearers.isEmpty()) {
            throw new SsoException("SAML refused: no bearer SubjectConfirmation.");
        }
        SsoException firstFailure = null;
        for (Element confirmation : bearers) {
            try {
                Element data = first(confirmation, SAML, "SubjectConfirmationData");
                if (data == null) {
                    throw new SsoException("SAML refused: no SubjectConfirmationData.");
                }
                if (!acs.equals(data.getAttribute("Recipient"))) {
                    throw new SsoException("SAML refused: Recipient [" + data.getAttribute("Recipient") + "] is not [" + acs + "].");
                }
                Long until = window(data, now, skew, "subject confirmation");
                if (until == null) {
                    throw new SsoException("SAML refused: SubjectConfirmationData has no NotOnOrAfter.");
                }
                String inResponseTo = data.getAttribute("InResponseTo");
                return new String[] {inResponseTo.isEmpty() ? null : inResponseTo, String.valueOf(until)};
            } catch (SsoException refused) {
                if (firstFailure == null) {
                    firstFailure = refused;
                }
            }
        }
        throw firstFailure;
    }

    private static Map<String, String> attributes(Element assertion) {
        Map<String, String> found = new LinkedHashMap<>();
        NodeList nodes = assertion.getElementsByTagNameNS(SAML, "Attribute");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element attribute = (Element) nodes.item(i);
            Element value = firstDescendant(attribute, SAML, "AttributeValue");
            if (!attribute.getAttribute("Name").isEmpty() && value != null) {
                found.put(attribute.getAttribute("Name"), text(value));
            }
        }
        return found;
    }

    // ── OAuth 2 / OIDC ──────────────────────────────────────────────────

    /** Where to send the browser, and the {@code state} and PKCE verifier to keep in the session. */
    public record OAuthStart(URI url, String state, String verifier) {
    }

    /** The provider's authorization URL with a fresh {@code state} and a PKCE (S256) challenge. */
    public OAuthStart oauthStart(HttpServletRequest request) {
        Map<String, String> config = config();
        if (config.get("sso_oauth_authorize_url").isBlank() || config.get("sso_oauth_token_url").isBlank()
                || config.get("sso_oauth_client_id").isBlank()) {
            throw new SsoException("OAuth refused: the provider is not fully configured.");
        }
        State state = new State();
        CodeVerifier verifier = new CodeVerifier();
        try {
            URI url = new AuthorizationRequest.Builder(new ResponseType(ResponseType.Value.CODE),
                            new ClientID(config.get("sso_oauth_client_id")))
                    .endpointURI(URI.create(config.get("sso_oauth_authorize_url")))
                    .redirectionURI(URI.create(url(request, "/oauth/callback")))
                    .scope(Scope.parse(config.get("sso_oauth_scopes").replace(',', ' ')))
                    .state(state)
                    .codeChallenge(verifier, CodeChallengeMethod.S256)
                    .build()
                    .toURI();
            return new OAuthStart(url, state.getValue(), verifier.getValue());
        } catch (RuntimeException invalid) {
            throw new SsoException("OAuth refused: " + invalid.getMessage());
        }
    }

    /** Exchanges the code (with the PKCE verifier) server to server, then reads the userinfo profile. */
    public Identity oauthIdentity(HttpServletRequest request, String code, String verifier) {
        Map<String, String> config = config();
        Map<String, Object> profile;
        try {
            TokenRequest exchange = new TokenRequest(URI.create(config.get("sso_oauth_token_url")),
                    new ClientSecretPost(new ClientID(config.get("sso_oauth_client_id")),
                            new Secret(config.get("sso_oauth_client_secret"))),
                    new AuthorizationCodeGrant(new AuthorizationCode(code), URI.create(url(request, "/oauth/callback")),
                            new CodeVerifier(verifier)));
            TokenResponse answer = TokenResponse.parse(exchange.toHTTPRequest().send());
            if (!answer.indicatesSuccess()) {
                throw new SsoException("OAuth refused: the token exchange failed ("
                        + answer.toErrorResponse().getErrorObject().getCode() + ").");
            }
            AccessToken token = answer.toSuccessResponse().getTokens().getAccessToken();
            HTTPRequest userinfo = new HTTPRequest(HTTPRequest.Method.GET, URI.create(config.get("sso_oauth_userinfo_url")));
            userinfo.setAuthorization(token.toAuthorizationHeader());
            userinfo.setAccept("application/json");
            HTTPResponse response = userinfo.send();
            if (!response.indicatesSuccess()) {
                throw new SsoException("OAuth refused: userinfo answered " + response.getStatusCode() + ".");
            }
            profile = JSONObjectUtils.parse(response.getBody());
        } catch (SsoException refused) {
            throw refused;
        } catch (Exception failed) {
            throw new SsoException("OAuth refused: " + failed.getMessage());
        }
        Object verified = profile.get("email_verified");
        if (Boolean.FALSE.equals(verified) || "false".equals(String.valueOf(verified)) || "0".equals(String.valueOf(verified))) {
            throw new SsoException("OAuth refused: the provider says the email is not verified.");
        }
        return identity(claim(profile, or(config.get("sso_attr_email"), "email"), "email"),
                claim(profile, or(config.get("sso_attr_name"), "name"), "name"),
                claim(profile, or(config.get("sso_attr_role"), "role"), null));
    }

    // ── JWT ─────────────────────────────────────────────────────────────

    /** Where JWT sign-in starts: the configured login URL, told where to come back. */
    public String jwtLoginUrl(HttpServletRequest request) {
        Map<String, String> config = config();
        String url = (config.get("sso_login_url").isBlank() ? config.get("sso_url") : config.get("sso_login_url")).trim();
        if (url.isEmpty()) {
            throw new SsoException("JWT sign-in has no login URL configured.");
        }
        return url + (url.contains("?") ? "&" : "?") + "return_to="
                + URLEncoder.encode(url(request, "/jwt/callback"), StandardCharsets.UTF_8);
    }

    /**
     * Validates a JWT handed back by the identity provider: {@code alg} must be the configured
     * algorithm (so {@code none} and algorithm switching are refused); {@code exp} is required;
     * {@code nbf}/{@code iat} hold within the clock skew; {@code aud} names this install; and
     * {@code jti} is present and used once.
     */
    public Identity validateJwt(HttpServletRequest request, String token) {
        Map<String, String> config = config();
        String algorithm = or(config.get("sso_jwt_algorithm"), "HS256");
        String secret = config.get("sso_jwt_secret");
        if (!JWT_ALGORITHMS.contains(algorithm)) {
            throw new SsoException("JWT refused: unsupported algorithm " + algorithm + ".");
        }
        if (secret == null || secret.isBlank()) {
            throw new SsoException("JWT refused: no secret or public key is configured.");
        }
        JWTClaimsSet claims;
        try {
            JWT parsed = JWTParser.parse(token == null ? "" : token.trim());
            if (!(parsed instanceof SignedJWT signed)) {
                throw new SsoException("JWT refused: the token is not signed.");
            }
            if (!JWSAlgorithm.parse(algorithm).equals(signed.getHeader().getAlgorithm())) {
                throw new SsoException("JWT refused: alg " + signed.getHeader().getAlgorithm() + " is not " + algorithm + ".");
            }
            JWSVerifier verifier = algorithm.startsWith("HS")
                    ? new MACVerifier(secret.getBytes(StandardCharsets.UTF_8))
                    : new RSASSAVerifier(rsaPublicKey(secret));
            if (!signed.verify(verifier)) {
                throw new SsoException("JWT refused: the signature does not verify.");
            }
            claims = signed.getJWTClaimsSet();
        } catch (SsoException refused) {
            throw refused;
        } catch (Exception invalid) {
            throw new SsoException("JWT refused: " + invalid.getMessage());
        }
        long now = Instant.now().getEpochSecond();
        long skew = clockSkew();
        if (claims.getExpirationTime() == null) {
            throw new SsoException("JWT refused: no exp claim.");
        }
        long exp = claims.getExpirationTime().toInstant().getEpochSecond();
        if (now > exp + skew) {
            throw new SsoException("JWT refused: the token has expired.");
        }
        if (claims.getNotBeforeTime() != null && claims.getNotBeforeTime().toInstant().getEpochSecond() > now + skew) {
            throw new SsoException("JWT refused: the token is not valid yet.");
        }
        if (claims.getIssueTime() != null && claims.getIssueTime().toInstant().getEpochSecond() > now + skew) {
            throw new SsoException("JWT refused: the token was issued in the future.");
        }
        String audience = jwtAudience(request);
        if (claims.getAudience() == null || !claims.getAudience().contains(audience)) {
            throw new SsoException("JWT refused: aud does not name [" + audience + "].");
        }
        String jti = claims.getJWTID();
        if (jti == null || jti.isBlank()) {
            throw new SsoException("JWT refused: no jti claim.");
        }
        if (!messageIds.claim("jwt", jti, exp + skew)) {
            throw new SsoException("JWT refused: jti [" + jti + "] was already used.");
        }
        Map<String, Object> values = claims.getClaims();
        return identity(claim(values, or(config.get("sso_attr_email"), "email"), "email"),
                claim(values, or(config.get("sso_attr_name"), "name"), "name"),
                claim(values, or(config.get("sso_attr_role"), "role"), null));
    }

    private static RSAPublicKey rsaPublicKey(String pem) throws Exception {
        String raw = pem.trim();
        if (raw.contains("CERTIFICATE")) {
            return (RSAPublicKey) idpKey(raw);
        }
        String body = raw.replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s+", "");
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
    }

    // ── Shared ──────────────────────────────────────────────────────────

    private static String claim(Map<String, Object> values, String name, String fallback) {
        Object value = values.get(name);
        if (value == null && fallback != null) {
            value = values.get(fallback);
        }
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Identity identity(String email, String name, String role) {
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new SsoException("SSO refused: no valid email in the identity.");
        }
        return new Identity(email, name, role);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /** Logs a refusal. */
    public static void refused(SsoException reason) {
        log.warn("Escalated SSO sign-in refused: {}", reason.getMessage());
    }
}
