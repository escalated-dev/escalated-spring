package dev.escalated.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.services.SettingsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

class WidgetOriginsTest {

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
        "https://Shop.Example.com, https://shop.example.com",
        "https://shop.example.com/, https://shop.example.com",
        "https://shop.example.com:443, https://shop.example.com",
        "http://localhost:5173, http://localhost:5173",
        "https://*.example.com, https://*.example.com",
        "https://*.example.com:8443, https://*.example.com:8443",
        "https://shop.example.com/checkout, NULL",
        "https://shop.example.com?x=1, NULL",
        "shop.example.com, NULL",
        "*, NULL",
        "https://*, NULL",
        "https://a.*.example.com, NULL",
        "ftp://shop.example.com, NULL",
        "https://user@shop.example.com, NULL",
    })
    void normalisesAdminEnteredOriginsAndRejectsAnythingElse(String entry, String expected) {
        assertThat(WidgetOrigins.normalizePattern(entry)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "https://shop.example.com, true",
        "https://SHOP.example.com:443, true",
        "http://shop.example.com, false",
        "https://shop.example.com:8443, false",
        "https://help.brand.test, true",
        "https://a.b.brand.test, true",
        "https://brand.test, false",
        "https://notbrand.test, false",
        "https://help.brand.test.evil.com, false",
        "http://localhost:5173, true",
        "http://localhost, false",
        "null, false",
    })
    void matchesExactOriginsPortsAndSubdomainWildcards(String origin, boolean allowed) {
        WidgetOrigins origins = origins(List.of("https://shop.example.com", "https://*.brand.test", "http://localhost:5173"), "");

        assertThat(origins.allows(origin)).isEqualTo(allowed);
    }

    @Test
    void mergesTheConfigListWithTheAdminSetting() {
        WidgetOrigins origins = origins(List.of("https://shop.example.com"),
                "https://blog.example.com, https://bad.example.com/path\n");

        assertThat(origins.configured()).containsExactly("https://shop.example.com", "https://blog.example.com");
        assertThat(origins.allows("https://blog.example.com")).isTrue();
        assertThat(origins.allows("https://bad.example.com")).isFalse();
    }

    @Test
    void reportsTheEntriesThatAreNotOrigins() {
        assertThat(WidgetOrigins.invalidEntries("https://a.example.com\n*, https://b.example.com/x\n\n"))
                .containsExactly("*", "https://b.example.com/x");
        assertThat(WidgetOrigins.invalidEntries("")).isEmpty();
        assertThat(WidgetOrigins.invalidEntries(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
        "NULL, NULL",
        "null, NULL",
        "https://help.example.com, NULL",
        "https://help.example.com:443, NULL",
        "https://shop.example.com, https://shop.example.com",
        "http://help.example.com, http://help.example.com",
    })
    void onlyReportsAnOriginThatNamesAnotherSite(String origin, String expected) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/escalated/api/widget/config");
        request.setScheme("https");
        request.setServerName("help.example.com");
        request.setServerPort(443);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }

        assertThat(WidgetOrigins.crossOrigin(request)).isEqualTo(expected);
    }

    private static WidgetOrigins origins(List<String> configured, String setting) {
        EscalatedProperties properties = new EscalatedProperties();
        properties.getWidget().setAllowedOrigins(configured);
        SettingsService settings = mock(SettingsService.class);
        when(settings.getOrDefault(WidgetOrigins.SETTING, "")).thenReturn(setting);
        return new WidgetOrigins(properties, settings);
    }
}
