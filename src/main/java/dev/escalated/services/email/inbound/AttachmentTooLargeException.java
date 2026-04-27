package dev.escalated.services.email.inbound;

/**
 * Thrown by {@link AttachmentDownloader#download} when a downloaded
 * attachment exceeds {@link AttachmentDownloader.Options#getMaxBytes}.
 * The partial body is not persisted.
 */
public class AttachmentTooLargeException extends RuntimeException {

    private final String attachmentName;
    private final long actualBytes;
    private final long maxBytes;

    public AttachmentTooLargeException(String name, long actual, long max) {
        super("Attachment '" + name + "' is " + actual + " bytes, exceeds limit " + max + ".");
        this.attachmentName = name;
        this.actualBytes = actual;
        this.maxBytes = max;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public long getActualBytes() {
        return actualBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }
}
