package dev.escalated.services.email.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PostmarkInboundParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SAMPLE_PAYLOAD = """
        {
          "FromName": "Customer",
          "MessageID": "22c74902-a0c1-4511-804f2-341342852c90",
          "FromFull": {"Email": "customer@example.com", "Name": "Customer"},
          "To": "support+abc@support.example.com",
          "ToFull": [{"Email": "support+abc@support.example.com", "Name": ""}],
          "OriginalRecipient": "support+abc@support.example.com",
          "Subject": "[ESC-00042] Help",
          "TextBody": "Plain body",
          "HtmlBody": "<p>HTML body</p>",
          "Headers": [
            {"Name": "Message-ID", "Value": "<abc@mail.client>"},
            {"Name": "In-Reply-To", "Value": "<ticket-42@support.example.com>"},
            {"Name": "References", "Value": "<ticket-42@support.example.com>"}
          ],
          "Attachments": [
            {"Name": "report.pdf", "Content": "aGVsbG8=",
             "ContentType": "application/pdf", "ContentLength": 5}
          ]
        }
        """;

    private static Map<String, Object> parsePayload(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    @Test
    void parse_extractsCoreFields() throws Exception {
        PostmarkInboundParser parser = new PostmarkInboundParser();

        InboundMessage m = parser.parse(parsePayload(SAMPLE_PAYLOAD));

        assertThat(m.fromEmail()).isEqualTo("customer@example.com");
        assertThat(m.fromName()).isEqualTo("Customer");
        assertThat(m.toEmail()).isEqualTo("support+abc@support.example.com");
        assertThat(m.subject()).isEqualTo("[ESC-00042] Help");
        assertThat(m.bodyText()).isEqualTo("Plain body");
        assertThat(m.bodyHtml()).isEqualTo("<p>HTML body</p>");
    }

    @Test
    void parse_extractsThreadingHeaders() throws Exception {
        PostmarkInboundParser parser = new PostmarkInboundParser();

        InboundMessage m = parser.parse(parsePayload(SAMPLE_PAYLOAD));

        assertThat(m.inReplyTo()).isEqualTo("<ticket-42@support.example.com>");
        assertThat(m.references()).isEqualTo("<ticket-42@support.example.com>");
    }

    @Test
    void parse_extractsAttachments() throws Exception {
        PostmarkInboundParser parser = new PostmarkInboundParser();

        InboundMessage m = parser.parse(parsePayload(SAMPLE_PAYLOAD));

        assertThat(m.attachments()).hasSize(1);
        InboundAttachment a = m.attachments().get(0);
        assertThat(a.name()).isEqualTo("report.pdf");
        assertThat(a.contentType()).isEqualTo("application/pdf");
        assertThat(a.sizeBytes()).isEqualTo(5L);
        assertThat(new String(a.content())).isEqualTo("hello");
    }

    @Test
    void parse_handlesMinimalPayload() throws Exception {
        String json = """
            {
              "FromFull": {"Email": "a@b.com"},
              "ToFull": [{"Email": "c@d.com"}],
              "Subject": "minimal"
            }
            """;
        PostmarkInboundParser parser = new PostmarkInboundParser();

        InboundMessage m = parser.parse(parsePayload(json));

        assertThat(m.fromEmail()).isEqualTo("a@b.com");
        assertThat(m.fromName()).isNull();
        assertThat(m.toEmail()).isEqualTo("c@d.com");
        assertThat(m.subject()).isEqualTo("minimal");
        assertThat(m.bodyText()).isNull();
        assertThat(m.inReplyTo()).isNull();
        assertThat(m.attachments()).isEmpty();
    }

    @Test
    void name_isPostmark() {
        assertThat(new PostmarkInboundParser().name()).isEqualTo("postmark");
    }
}
