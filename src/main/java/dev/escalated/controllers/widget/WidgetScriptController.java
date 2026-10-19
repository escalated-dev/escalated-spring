package dev.escalated.controllers.widget;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the standalone widget bundle, built by {@code @escalated-dev/escalated}
 * and vendored at {@value #BUNDLE}, so any site can load it with a
 * {@code <script>} tag pointing at this install. It is public and carries no
 * data, so every origin may read it.
 */
@RestController
@RequestMapping("/escalated/api/widget")
public class WidgetScriptController {

    public static final String BUNDLE = "META-INF/escalated/widget/escalated-widget.js";

    private volatile Script script;

    @GetMapping("/embed.js")
    public ResponseEntity<byte[]> script(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch)
            throws IOException {
        Script bundle = bundle();
        if (bundle == null) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/javascript; charset=utf-8");
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=3600");
        headers.set(HttpHeaders.ETAG, bundle.etag());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Cross-Origin-Resource-Policy", "cross-origin");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

        if (matches(ifNoneMatch, bundle.etag())) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        return ResponseEntity.ok().headers(headers).body(bundle.body());
    }

    private static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null) {
            return false;
        }
        for (String tag : ifNoneMatch.split(",")) {
            String candidate = tag.trim();
            if (candidate.startsWith("W/")) {
                candidate = candidate.substring(2);
            }
            if (candidate.equals(etag) || candidate.equals("*")) {
                return true;
            }
        }
        return false;
    }

    private Script bundle() throws IOException {
        Script loaded = script;
        if (loaded == null) {
            ClassPathResource resource = new ClassPathResource(BUNDLE);
            if (!resource.exists()) {
                return null;
            }
            byte[] body;
            try (InputStream in = resource.getInputStream()) {
                body = in.readAllBytes();
            }
            loaded = new Script(body, "\"" + sha256(body).substring(0, 32) + "\"");
            script = loaded;
        }
        return loaded;
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Script(byte[] body, String etag) {
    }
}
