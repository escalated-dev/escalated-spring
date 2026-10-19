package dev.escalated.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WidgetMetadataTest {

    @Test
    void keepsAFlatMapOfShortScalars() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("order_id", "  A-1001 ");
        raw.put("seats", 3);
        raw.put("ratio", 0.5);
        raw.put("trial", true);
        raw.put("bad key", "x");
        raw.put("nested", Map.of("a", 1));
        raw.put("list", List.of(1));
        raw.put("nothing", null);
        raw.put("nan", Double.NaN);
        raw.put("k".repeat(65), "too long a key");

        assertThat(WidgetMetadata.sanitize(raw, (String) null))
                .containsExactly(Map.entry("order_id", "A-1001"), Map.entry("seats", 3),
                        Map.entry("ratio", 0.5), Map.entry("trial", true));
    }

    @Test
    void cutsLongValuesWithoutSplittingACharacter() {
        String value = "😀".repeat(501);

        String kept = (String) WidgetMetadata.sanitize(Map.of("note", value), (String) null).get("note");

        assertThat(kept.codePointCount(0, kept.length())).isEqualTo(500);
    }

    @Test
    void keepsOnlyAWebPageUrl() {
        assertThat(WidgetMetadata.sanitize(Map.of("page_url", "https://shop.example.com/a?b=1"), (String) null))
                .containsEntry("page_url", "https://shop.example.com/a?b=1");
        for (String url : List.of("javascript:alert(1)", "ftp://shop.example.com/x", "not a url", "https:///no-host")) {
            assertThat(WidgetMetadata.sanitize(Map.of("page_url", url), (String) null)).isNull();
        }
    }

    @Test
    void embeddedOnComesFromTheServerOnly() {
        assertThat(WidgetMetadata.sanitize(Map.of("embedded_on", "https://bank.example.com"), (String) null)).isNull();
        assertThat(WidgetMetadata.sanitize(Map.of("embedded_on", "https://bank.example.com"), "https://shop.example.com"))
                .isEqualTo(Map.of("embedded_on", "https://shop.example.com"));
        assertThat(WidgetMetadata.sanitize(null, "https://shop.example.com"))
                .isEqualTo(Map.of("embedded_on", "https://shop.example.com"));
    }

    @Test
    void nothingLeftIsNull() {
        assertThat(WidgetMetadata.sanitize(null, (String) null)).isNull();
        assertThat(WidgetMetadata.sanitize(Map.of(), (String) null)).isNull();
    }

    @Test
    void rejectsANonObjectOrMoreThanFiftyKeys() {
        assertThatThrownBy(() -> WidgetMetadata.sanitize(List.of("a"), (String) null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(422));
        assertThatThrownBy(() -> WidgetMetadata.sanitize("order=1", (String) null))
                .isInstanceOf(ResponseStatusException.class);

        Map<String, Object> many = new LinkedHashMap<>();
        for (int i = 0; i < 51; i++) {
            many.put("k" + i, i);
        }
        assertThatThrownBy(() -> WidgetMetadata.sanitize(many, (String) null))
                .isInstanceOf(ResponseStatusException.class);

        many.remove("k50");
        assertThat(WidgetMetadata.sanitize(many, (String) null)).hasSize(25).containsKey("k24");
    }
}
