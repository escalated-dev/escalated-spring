package dev.escalated.controllers;

import dev.escalated.security.AttachmentAccess;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Attachment downloads. Who may download what is {@link AttachmentAccess}'s
 * call; this controller only makes sure the bytes can never be rendered as a
 * page on the helpdesk's origin: always {@code Content-Disposition: attachment}
 * with an RFC 6266 filename, {@code nosniff}, and {@code application/octet-stream}
 * for anything but a few inert types.
 */
@RestController
@RequestMapping("/escalated/api/attachments")
public class AttachmentController {

    /** Types a browser will not execute even if the disposition were ignored. */
    private static final Set<String> SAFE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf", "text/plain");

    private final AttachmentAccess access;

    public AttachmentController(AttachmentAccess access) {
        this.access = access;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, Authentication authentication) {
        AttachmentAccess.Download download = access.authorize(id, authentication);

        Path filePath = Paths.get(download.filePath());
        if (!Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(contentType(download.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename(download.fileName()), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new FileSystemResource(filePath));
    }

    static MediaType contentType(String stored) {
        if (stored != null) {
            try {
                MediaType type = MediaType.parseMediaType(stored);
                String bare = (type.getType() + "/" + type.getSubtype()).toLowerCase(Locale.ROOT);
                if (SAFE_TYPES.contains(bare)) {
                    return "text/plain".equals(bare)
                            ? new MediaType("text", "plain", StandardCharsets.UTF_8)
                            : new MediaType(type.getType(), type.getSubtype());
                }
            } catch (RuntimeException ignored) {
                // An unparseable stored type is served as opaque bytes.
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /** The last path segment of the stored name, without control characters. */
    static String filename(String stored) {
        String name = stored == null ? "" : stored.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
        return name.isEmpty() ? "attachment" : name;
    }
}
