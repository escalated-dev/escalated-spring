package dev.escalated.services.email.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.escalated.models.Attachment;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AttachmentRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.email.inbound.InboundEmailService.PendingAttachment;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachmentDownloaderTest {

    private HttpClient httpClient;
    private RecordingStorage storage;
    private AttachmentRepository attachmentRepo;
    private TicketRepository ticketRepo;
    private ReplyRepository replyRepo;

    private Ticket ticket;
    private Reply reply;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        storage = new RecordingStorage();
        attachmentRepo = mock(AttachmentRepository.class);
        ticketRepo = mock(TicketRepository.class);
        replyRepo = mock(ReplyRepository.class);

        ticket = new Ticket();
        reply = new Reply();

        when(ticketRepo.findById(any())).thenReturn(Optional.of(ticket));
        when(replyRepo.findById(any())).thenReturn(Optional.of(reply));
        when(attachmentRepo.save(any(Attachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private AttachmentDownloader downloader(AttachmentDownloader.Options opts) {
        return new AttachmentDownloader(
                httpClient, storage, attachmentRepo, ticketRepo, replyRepo, opts);
    }

    private static PendingAttachment pending(String url, String name, String contentType) {
        return new PendingAttachment(name, contentType, null, url);
    }

    private void stubResponse(int status, byte[] body, String contentType) throws Exception {
        HttpResponse<byte[]> resp = new StubResponse(status, body, contentType);
        when(httpClient.<byte[]>send(any(), any())).thenReturn(resp);
    }

    @Test
    void downloadHappyPathPersistsAttachment() throws Exception {
        stubResponse(200, "hello pdf".getBytes(), "application/pdf");

        AttachmentDownloader d = downloader(new AttachmentDownloader.Options());
        Attachment a = d.download(
                pending("https://provider/x/report", "report.pdf", "application/pdf"),
                42L, null);

        assertThat(a.getFileName()).isEqualTo("report.pdf");
        assertThat(a.getMimeType()).isEqualTo("application/pdf");
        assertThat(a.getFileSize()).isEqualTo(9L);
        assertThat(a.getTicket()).isSameAs(ticket);
        assertThat(a.getReply()).isNull();
        assertThat(storage.lastPutContent).isEqualTo("hello pdf".getBytes());
        verify(attachmentRepo).save(any(Attachment.class));
    }

    @Test
    void downloadWithReplyIdSetsReply() throws Exception {
        stubResponse(200, new byte[]{1, 2, 3}, "application/octet-stream");

        AttachmentDownloader d = downloader(new AttachmentDownloader.Options());
        Attachment a = d.download(
                pending("https://x/y", "a", "application/octet-stream"),
                42L, 7L);

        assertThat(a.getReply()).isSameAs(reply);
    }

    @Test
    void download404ThrowsAndDoesNotPersist() throws Exception {
        stubResponse(404, new byte[0], "text/plain");

        AttachmentDownloader d = downloader(new AttachmentDownloader.Options());

        assertThatThrownBy(() -> d.download(
                pending("https://x/missing", "x", "text/plain"), 1L, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HTTP 404");

        verify(attachmentRepo, never()).save(any());
        assertThat(storage.putCount).isEqualTo(0);
    }

    @Test
    void downloadOverSizeLimitThrowsAttachmentTooLarge() throws Exception {
        stubResponse(200, new byte[100], "application/octet-stream");

        AttachmentDownloader d = downloader(new AttachmentDownloader.Options().maxBytes(10));

        assertThatExceptionOfType(AttachmentTooLargeException.class)
                .isThrownBy(() -> d.download(
                        pending("https://x/big", "big.bin", "application/octet-stream"),
                        1L, null))
                .matches(ex -> ex.getActualBytes() == 100 && ex.getMaxBytes() == 10);

        verify(attachmentRepo, never()).save(any());
    }

    @Test
    void downloadSendsBasicAuthHeader() throws Exception {
        stubResponse(200, new byte[]{1}, "application/octet-stream");

        AttachmentDownloader d = downloader(new AttachmentDownloader.Options()
                .basicAuth("api", "key-secret"));
        d.download(pending("https://x/y", "x", "application/octet-stream"), 1L, null);

        // Verify the request carried the header. We need to capture the
        // HttpRequest passed to send(); Mockito's ArgumentCaptor does that.
        org.mockito.ArgumentCaptor<HttpRequest> captor =
                org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        String auth = captor.getValue().headers().firstValue("Authorization").orElse("");
        assertThat(auth).startsWith("Basic ");
        String encoded = auth.substring("Basic ".length());
        String decoded = new String(java.util.Base64.getDecoder().decode(encoded));
        assertThat(decoded).isEqualTo("api:key-secret");
    }

    @Test
    void downloadMissingUrlThrows() {
        AttachmentDownloader d = downloader(new AttachmentDownloader.Options());

        assertThatThrownBy(() -> d.download(
                pending("", "x", "text/plain"), 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void downloadFallsBackToResponseContentType() throws Exception {
        stubResponse(200, new byte[]{1, 2, 3}, "image/png");

        AttachmentDownloader d = downloader(new AttachmentDownloader.Options());

        // ContentType on pending is empty — should pick up from response.
        Attachment a = d.download(
                pending("https://x/y", "img", ""),
                1L, null);

        assertThat(a.getMimeType()).isEqualTo("image/png");
    }

    @Test
    void safeFilenameStripsPathTraversal() {
        assertThat(AttachmentDownloader.safeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(AttachmentDownloader.safeFilename("/tmp/evil.txt")).isEqualTo("evil.txt");
        assertThat(AttachmentDownloader.safeFilename("")).isEqualTo("attachment");
        assertThat(AttachmentDownloader.safeFilename(null)).isEqualTo("attachment");
        assertThat(AttachmentDownloader.safeFilename("..")).isEqualTo("attachment");
        assertThat(AttachmentDownloader.safeFilename(".")).isEqualTo("attachment");
    }

    @Test
    void downloadAllContinuesPastFailures() throws Exception {
        // First call succeeds, second fails with 500, third succeeds.
        HttpResponse<byte[]> ok = new StubResponse(200, new byte[]{1}, "application/octet-stream");
        HttpResponse<byte[]> fail = new StubResponse(500, new byte[0], "text/plain");
        when(httpClient.<byte[]>send(any(), any()))
                .thenReturn(ok, fail, ok);

        AttachmentDownloader d = downloader(new AttachmentDownloader.Options());
        List<AttachmentDownloader.Result> results = d.downloadAll(
                List.of(
                        pending("https://x/1", "a", "application/octet-stream"),
                        pending("https://x/2", "b", "application/octet-stream"),
                        pending("https://x/3", "c", "application/octet-stream")
                ),
                1L, null);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).succeeded()).isTrue();
        assertThat(results.get(1).succeeded()).isFalse();
        assertThat(results.get(1).error()).isNotNull();
        assertThat(results.get(2).succeeded()).isTrue();
    }

    @Test
    void localFileStorageWritesFile(@TempDir Path tmp) {
        LocalFileAttachmentStorage fs = new LocalFileAttachmentStorage(tmp);
        String path = fs.put("hello.txt", "payload".getBytes(), "text/plain");

        assertThat(path).startsWith(tmp.toString());
        assertThat(path).endsWith("hello.txt");
        try {
            assertThat(Files.readString(Path.of(path))).isEqualTo("payload");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void localFileStorageProducesUniquePaths(@TempDir Path tmp) throws InterruptedException {
        LocalFileAttachmentStorage fs = new LocalFileAttachmentStorage(tmp);

        String p1 = fs.put("x.txt", new byte[]{1}, "text/plain");
        // Tiny delay so the nanos component of the prefix advances.
        Thread.sleep(1);
        String p2 = fs.put("x.txt", new byte[]{2}, "text/plain");

        assertThat(p1).isNotEqualTo(p2);
    }

    @Test
    void localFileStorageRejectsNullRoot() {
        assertThatThrownBy(() -> new LocalFileAttachmentStorage(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Minimal HttpResponse stub — implements only what
     * AttachmentDownloader reads. Avoids the ceremony of building a
     * real response via MockWebServer for a unit test.
     */
    private static class StubResponse implements HttpResponse<byte[]> {
        private final int status;
        private final byte[] body;
        private final String contentType;

        StubResponse(int status, byte[] body, String contentType) {
            this.status = status;
            this.body = body;
            this.contentType = contentType;
        }

        @Override public int statusCode() { return status; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            BiPredicate<String, String> filter = (k, v) -> true;
            return HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), filter);
        }
        @Override public byte[] body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://stub"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    /**
     * In-memory AttachmentStorage for tests. Records the last put call.
     */
    private static class RecordingStorage implements AttachmentStorage {
        byte[] lastPutContent;
        String lastPutFilename;
        String lastPutContentType;
        int putCount = 0;
        String returnPath = "/stored/path";

        @Override
        public String put(String filename, byte[] content, String contentType) {
            this.lastPutFilename = filename;
            this.lastPutContent = content;
            this.lastPutContentType = contentType;
            this.putCount++;
            return returnPath;
        }
    }
}
