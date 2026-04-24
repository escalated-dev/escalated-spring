package dev.escalated.services.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MessageIdUtil}. Mirrors the NestJS test suite
 * for {@code message-id.ts}. Pure functions — no Spring context.
 */
class MessageIdUtilTest {

    private static final String DOMAIN = "support.example.com";
    private static final String SECRET = "test-secret-long-enough-for-hmac";

    @Test
    void buildMessageId_initialTicket_usesTicketForm() {
        String id = MessageIdUtil.buildMessageId(42, null, DOMAIN);
        assertThat(id).isEqualTo("<ticket-42@support.example.com>");
    }

    @Test
    void buildMessageId_replyForm_appendsReplyId() {
        String id = MessageIdUtil.buildMessageId(42, 7L, DOMAIN);
        assertThat(id).isEqualTo("<ticket-42-reply-7@support.example.com>");
    }

    @Test
    void parseTicketIdFromMessageId_roundTripsBuiltId() {
        String initial = MessageIdUtil.buildMessageId(42, null, DOMAIN);
        String reply = MessageIdUtil.buildMessageId(42, 7L, DOMAIN);

        assertThat(MessageIdUtil.parseTicketIdFromMessageId(initial)).contains(42L);
        assertThat(MessageIdUtil.parseTicketIdFromMessageId(reply)).contains(42L);
    }

    @Test
    void parseTicketIdFromMessageId_acceptsValueWithoutBrackets() {
        assertThat(MessageIdUtil.parseTicketIdFromMessageId("ticket-99@example.com")).contains(99L);
    }

    @Test
    void parseTicketIdFromMessageId_returnsEmptyForUnrelatedInput() {
        assertThat(MessageIdUtil.parseTicketIdFromMessageId(null)).isEmpty();
        assertThat(MessageIdUtil.parseTicketIdFromMessageId("")).isEmpty();
        assertThat(MessageIdUtil.parseTicketIdFromMessageId("<random@mail.com>")).isEmpty();
        assertThat(MessageIdUtil.parseTicketIdFromMessageId("ticket-abc@example.com")).isEmpty();
    }

    @Test
    void buildReplyTo_isStableForSameInputs() {
        String first = MessageIdUtil.buildReplyTo(42, SECRET, DOMAIN);
        String again = MessageIdUtil.buildReplyTo(42, SECRET, DOMAIN);

        assertThat(first).isEqualTo(again);
        assertThat(first).matches("reply\\+42\\.[a-f0-9]{8}@support\\.example\\.com");
    }

    @Test
    void buildReplyTo_differentTicketsProduceDifferentSignatures() {
        String a = MessageIdUtil.buildReplyTo(42, SECRET, DOMAIN);
        String b = MessageIdUtil.buildReplyTo(43, SECRET, DOMAIN);

        // Local parts differ in both the ticket id and the signature.
        String aLocal = a.substring(0, a.indexOf('@'));
        String bLocal = b.substring(0, b.indexOf('@'));
        assertThat(aLocal).isNotEqualTo(bLocal);
    }

    @Test
    void verifyReplyTo_roundTripsBuiltAddress() {
        String address = MessageIdUtil.buildReplyTo(42, SECRET, DOMAIN);
        assertThat(MessageIdUtil.verifyReplyTo(address, SECRET)).contains(42L);
    }

    @Test
    void verifyReplyTo_acceptsLocalPartOnly() {
        String address = MessageIdUtil.buildReplyTo(42, SECRET, DOMAIN);
        String local = address.substring(0, address.indexOf('@'));
        assertThat(MessageIdUtil.verifyReplyTo(local, SECRET)).contains(42L);
    }

    @Test
    void verifyReplyTo_rejectsTamperedSignature() {
        // Build a valid address, then flip one signature char.
        String address = MessageIdUtil.buildReplyTo(42, SECRET, DOMAIN);
        int at = address.indexOf('@');
        String local = address.substring(0, at);
        String tampered = local.substring(0, local.length() - 1)
                + (local.charAt(local.length() - 1) == '0' ? '1' : '0')
                + address.substring(at);

        assertThat(MessageIdUtil.verifyReplyTo(tampered, SECRET)).isEmpty();
    }

    @Test
    void verifyReplyTo_rejectsWrongSecret() {
        String address = MessageIdUtil.buildReplyTo(42, SECRET, DOMAIN);
        assertThat(MessageIdUtil.verifyReplyTo(address, "different-secret")).isEmpty();
    }

    @Test
    void verifyReplyTo_rejectsMalformedInput() {
        Optional<Long> empty = Optional.empty();
        assertThat(MessageIdUtil.verifyReplyTo(null, SECRET)).isEqualTo(empty);
        assertThat(MessageIdUtil.verifyReplyTo("", SECRET)).isEqualTo(empty);
        assertThat(MessageIdUtil.verifyReplyTo("alice@example.com", SECRET)).isEqualTo(empty);
        assertThat(MessageIdUtil.verifyReplyTo("reply@example.com", SECRET)).isEqualTo(empty);
        assertThat(MessageIdUtil.verifyReplyTo("reply+abc.deadbeef@example.com", SECRET)).isEqualTo(empty);
    }

    @Test
    void verifyReplyTo_isCaseInsensitiveOnHex() {
        String address = MessageIdUtil.buildReplyTo(42, SECRET, DOMAIN);
        String upper = address.toUpperCase();
        assertThat(MessageIdUtil.verifyReplyTo(upper, SECRET)).contains(42L);
    }
}
