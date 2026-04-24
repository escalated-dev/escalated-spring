package dev.escalated.services.email.inbound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Reference {@link AttachmentStorage} for hosts without cloud
 * storage — writes to the local filesystem under a configured root.
 * Files are prefixed with a UTC timestamp (including nanos) to avoid
 * collisions between uploads with the same original filename.
 *
 * <p>Host apps with durable cloud storage needs should implement
 * {@link AttachmentStorage} themselves and inject their S3 / GCS /
 * Azure adapter into {@link AttachmentDownloader} instead of using
 * this class.
 */
public class LocalFileAttachmentStorage implements AttachmentStorage {

    private static final DateTimeFormatter PREFIX = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    private final Path root;

    public LocalFileAttachmentStorage(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("Local file storage root is required.");
        }
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create storage root: " + root, e);
        }
        this.root = root;
    }

    public Path getRoot() {
        return root;
    }

    @Override
    public String put(String filename, byte[] content, String contentType) {
        Instant now = Instant.now();
        String prefix = PREFIX.format(now) + "-" + now.getNano();
        String storedName = prefix + "-" + filename;
        Path full = root.resolve(storedName);

        try {
            Files.write(full, content);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(full);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
            throw new RuntimeException("Cannot write file: " + full, e);
        }
        return full.toString();
    }
}
