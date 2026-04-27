package dev.escalated.services.email.inbound;

import dev.escalated.models.Attachment;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AttachmentRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.email.inbound.InboundEmailService.PendingAttachment;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches provider-hosted attachments surfaced by
 * {@link InboundEmailService.ProcessResult#pendingAttachmentDownloads()}
 * and persists them as {@link Attachment} rows tied to a ticket
 * (and optionally a reply).
 *
 * <p>Mailgun hosts larger attachments behind a URL instead of
 * inlining them in the webhook payload; host apps run this in a
 * background worker after {@link InboundEmailService#process} returns,
 * so the webhook response can go back to the provider immediately
 * regardless of download latency.
 *
 * <p>Host apps with durable cloud storage needs (S3, Azure Blob,
 * GCS) can implement {@link AttachmentStorage} themselves and pass
 * it to the constructor instead of the reference
 * {@link LocalFileAttachmentStorage}.
 */
public class AttachmentDownloader {

    private static final Logger log = LoggerFactory.getLogger(AttachmentDownloader.class);

    private final HttpClient httpClient;
    private final AttachmentStorage storage;
    private final AttachmentRepository attachments;
    private final TicketRepository tickets;
    private final ReplyRepository replies;
    private final Options options;

    public AttachmentDownloader(
            HttpClient httpClient,
            AttachmentStorage storage,
            AttachmentRepository attachments,
            TicketRepository tickets,
            ReplyRepository replies,
            Options options) {
        this.httpClient = httpClient != null
                ? httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.storage = storage;
        this.attachments = attachments;
        this.tickets = tickets;
        this.replies = replies;
        this.options = options != null ? options : new Options();
    }

    /**
     * Download one {@link PendingAttachment} and persist it.
     *
     * @throws AttachmentTooLargeException when the body exceeds
     *     {@link Options#maxBytes}.
     * @throws RuntimeException on any other failure (HTTP non-2xx,
     *     storage write error, etc.).
     */
    public Attachment download(PendingAttachment pending, Long ticketId, Long replyId) {
        if (pending.downloadUrl() == null || pending.downloadUrl().isBlank()) {
            throw new IllegalArgumentException("Pending attachment has no download URL.");
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(pending.downloadUrl()))
                .GET();
        if (options.basicAuth != null) {
            String token = Base64.getEncoder().encodeToString(
                    (options.basicAuth.username() + ":" + options.basicAuth.password()).getBytes());
            reqBuilder.header("Authorization", "Basic " + token);
        }

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Attachment download failed: " + pending.downloadUrl(), e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException(
                    "Attachment download failed: " + pending.downloadUrl() + " → HTTP " + status);
        }

        byte[] bytes = response.body();
        if (options.maxBytes > 0 && bytes.length > options.maxBytes) {
            throw new AttachmentTooLargeException(pending.name(), bytes.length, options.maxBytes);
        }

        String contentType = pending.contentType() != null && !pending.contentType().isBlank()
                ? pending.contentType()
                : response.headers().firstValue("Content-Type").orElse("application/octet-stream");

        String filename = safeFilename(pending.name());
        String path = storage.put(filename, bytes, contentType);

        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket #" + ticketId + " not found"));

        Attachment attachment = new Attachment();
        attachment.setTicket(ticket);
        if (replyId != null) {
            Reply reply = replies.findById(replyId)
                    .orElseThrow(() -> new IllegalArgumentException("Reply #" + replyId + " not found"));
            attachment.setReply(reply);
        }
        attachment.setFileName(filename);
        attachment.setFilePath(path);
        attachment.setFileSize((long) bytes.length);
        attachment.setMimeType(contentType);

        Attachment saved = attachments.save(attachment);
        log.info("[AttachmentDownloader] Persisted {} ({} bytes) for ticket #{}",
                filename, bytes.length, ticketId);
        return saved;
    }

    /**
     * Download a batch of {@link PendingAttachment}s. Continues past
     * per-attachment failures so a single bad URL doesn't prevent the
     * rest from persisting.
     */
    public List<Result> downloadAll(
            List<PendingAttachment> pending,
            Long ticketId,
            Long replyId) {
        List<Result> results = new ArrayList<>(pending.size());
        for (PendingAttachment p : pending) {
            try {
                Attachment saved = download(p, ticketId, replyId);
                results.add(new Result(p, saved, null));
            } catch (RuntimeException ex) {
                log.warn("[AttachmentDownloader] Failed to download {}: {}",
                        p.downloadUrl(), ex.getMessage());
                results.add(new Result(p, null, ex));
            }
        }
        return results;
    }

    /**
     * Strip path separators so a crafted attachment name like
     * {@code ../../etc/passwd} can't escape the storage root. Falls
     * back to {@code "attachment"} when the input is unusable.
     */
    static String safeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "attachment";
        }
        String normalized = name.replace('\\', '/').trim();
        Path p = Path.of(normalized);
        String base = Optional.ofNullable(p.getFileName()).map(Path::toString).orElse("");
        if (base.isBlank() || base.equals(".") || base.equals("..")) {
            return "attachment";
        }
        return base;
    }

    /**
     * Runtime configuration — size cap + optional HTTP basic auth.
     */
    public static class Options {
        private long maxBytes = 0;
        private BasicAuth basicAuth;

        public Options maxBytes(long max) {
            this.maxBytes = max;
            return this;
        }

        public Options basicAuth(String username, String password) {
            this.basicAuth = new BasicAuth(username, password);
            return this;
        }

        public long getMaxBytes() {
            return maxBytes;
        }

        public BasicAuth getBasicAuth() {
            return basicAuth;
        }
    }

    public record BasicAuth(String username, String password) {}

    /**
     * Per-attachment outcome from {@link #downloadAll}. {@code persisted}
     * is non-null on success; {@code error} is non-null on failure.
     */
    public record Result(PendingAttachment pending, Attachment persisted, Throwable error) {
        public boolean succeeded() {
            return persisted != null;
        }
    }
}
