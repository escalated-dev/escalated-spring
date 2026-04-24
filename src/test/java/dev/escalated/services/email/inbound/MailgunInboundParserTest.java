package dev.escalated.services.email.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MailgunInboundParserTest {

    private Map<String, Object> sampleFormData() {
        Map<String, Object> m = new HashMap<>();
        m.put("sender", "customer@example.com");
        m.put("from", "Customer <customer@example.com>");
        m.put("recipient", "support+abc@support.example.com");
        m.put("To", "support+abc@support.example.com");
        m.put("subject", "[ESC-00042] Help");
        m.put("body-plain", "Plain body");
        m.put("body-html", "<p>HTML body</p>");
        m.put("Message-Id", "<mailgun-incoming@mail.client>");
        m.put("In-Reply-To", "<ticket-42@support.example.com>");
        m.put("References", "<ticket-42@support.example.com>");
        m.put("attachments",
                "[{\"name\":\"report.pdf\",\"content-type\":\"application/pdf\",\"size\":5120,\"url\":\"https://mailgun.example/att/abc\"}]");
        return m;
    }

    @Test
    void nameIsMailgun() {
        assertThat(new MailgunInboundParser().name()).isEqualTo("mailgun");
    }

    @Test
    void parseExtractsCoreFields() {
        InboundMessage m = new MailgunInboundParser().parse(sampleFormData());

        assertThat(m.fromEmail()).isEqualTo("customer@example.com");
        assertThat(m.fromName()).isEqualTo("Customer");
        assertThat(m.toEmail()).isEqualTo("support+abc@support.example.com");
        assertThat(m.subject()).isEqualTo("[ESC-00042] Help");
        assertThat(m.bodyText()).isEqualTo("Plain body");
        assertThat(m.bodyHtml()).isEqualTo("<p>HTML body</p>");
    }

    @Test
    void parseExtractsThreadingHeaders() {
        InboundMessage m = new MailgunInboundParser().parse(sampleFormData());

        assertThat(m.inReplyTo()).isEqualTo("<ticket-42@support.example.com>");
        assertThat(m.references()).isEqualTo("<ticket-42@support.example.com>");
    }

    @Test
    void parseProviderHostedAttachments() {
        InboundMessage m = new MailgunInboundParser().parse(sampleFormData());

        assertThat(m.attachments()).hasSize(1);
        InboundAttachment a = m.attachments().get(0);
        assertThat(a.name()).isEqualTo("report.pdf");
        assertThat(a.contentType()).isEqualTo("application/pdf");
        assertThat(a.sizeBytes()).isEqualTo(5120L);
        assertThat(a.downloadUrl()).isEqualTo("https://mailgun.example/att/abc");
        // Mailgun hosts content — no inline bytes.
        assertThat(a.content()).isNull();
    }

    @Test
    void parseHandlesMalformedAttachmentsJson() {
        Map<String, Object> data = sampleFormData();
        data.put("attachments", "not json");

        InboundMessage m = new MailgunInboundParser().parse(data);

        assertThat(m.attachments()).isEmpty();
    }

    @Test
    void parseFallsBackSenderToFromOnMissing() {
        Map<String, Object> data = new HashMap<>();
        data.put("from", "only-from@example.com");
        data.put("recipient", "support@example.com");
        data.put("subject", "hi");

        InboundMessage m = new MailgunInboundParser().parse(data);

        assertThat(m.fromEmail()).isEqualTo("only-from@example.com");
    }

    @Test
    void parseExtractFromNameReturnsNullWithoutAngleBrackets() {
        Map<String, Object> data = new HashMap<>();
        data.put("sender", "bareemail@example.com");
        data.put("from", "bareemail@example.com");
        data.put("recipient", "support@example.com");
        data.put("subject", "hi");

        InboundMessage m = new MailgunInboundParser().parse(data);

        assertThat(m.fromName()).isNull();
    }

    @Test
    void parseStripsQuotesFromFromName() {
        Map<String, Object> data = new HashMap<>();
        data.put("sender", "jane@example.com");
        data.put("from", "\"Jane Doe\" <jane@example.com>");
        data.put("recipient", "support@example.com");
        data.put("subject", "hi");

        InboundMessage m = new MailgunInboundParser().parse(data);

        assertThat(m.fromName()).isEqualTo("Jane Doe");
    }
}
