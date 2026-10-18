package dev.escalated.security;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonErrorTokenLimitTest {

    @Test
    void jacksonTwoBoundsInvalidDataInputTokens() throws Exception {
        var factory = com.fasterxml.jackson.core.JsonFactory.builder()
                .errorReportConfiguration(com.fasterxml.jackson.core.ErrorReportConfiguration.builder()
                        .maxErrorTokenLength(16).build())
                .build();
        try (var parser = factory.createParser(invalidToken())) {
            var error = assertThrows(com.fasterxml.jackson.core.JsonParseException.class, parser::nextToken);
            assertTrue(error.getOriginalMessage().contains("Unrecognized token"));
            assertFalse(error.getOriginalMessage().contains("a".repeat(32)));
        }
    }

    @Test
    void jacksonThreeBoundsInvalidDataInputTokens() throws Exception {
        var factory = tools.jackson.core.json.JsonFactory.builder()
                .errorReportConfiguration(tools.jackson.core.ErrorReportConfiguration.builder()
                        .maxErrorTokenLength(16).build())
                .build();
        try (var parser = factory.createParser(tools.jackson.core.ObjectReadContext.empty(), invalidToken())) {
            var error = assertThrows(tools.jackson.core.exc.StreamReadException.class, parser::nextToken);
            assertTrue(error.getOriginalMessage().contains("Unrecognized token"));
            assertFalse(error.getOriginalMessage().contains("a".repeat(32)));
        }
    }

    private static DataInput invalidToken() {
        return new DataInputStream(new ByteArrayInputStream(("a".repeat(64) + " ").getBytes(StandardCharsets.UTF_8)));
    }
}
