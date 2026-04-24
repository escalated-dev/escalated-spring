package dev.escalated.services.email.inbound;

/**
 * Minimal contract for writing attachment bytes to a backend.
 * Implementations can persist to local filesystem
 * ({@link LocalFileAttachmentStorage}), S3, GCS, Azure Blob, etc.
 *
 * <p>{@link #put} returns a storage-specific path/key that can later
 * be used to retrieve the file and is persisted on
 * {@link dev.escalated.models.Attachment#getFilePath()}.
 */
public interface AttachmentStorage {

    /**
     * Persist the given content and return a storage-specific path
     * or key.
     */
    String put(String filename, byte[] content, String contentType);
}
