package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import hostapp.HostApplication;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * With no embed configuration the route table is exactly what it was before the embeddable admin
 * existed: every Escalated mapping, its security chain, filters and gate decisions, the servlet
 * filters and the {@code route()} names, compared with a snapshot taken from that code.
 *
 * <p>The live table is also written to {@code build/embed/default-routes.actual.json}, to diff.
 */
@SpringBootTest(classes = HostApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.newsletters.enabled=true",
})
class DefaultRouteTableTest {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired private ApplicationContext context;

    @Test
    void routeTableIsUnchangedWithoutEmbedConfig() throws Exception {
        Map<String, Object> live = RouteTableSnapshot.of(context);
        String actual = JSON.writeValueAsString(live) + "\n";
        Path out = Path.of("build", "embed", "default-routes.actual.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, actual);

        try (InputStream fixture = getClass().getResourceAsStream("/embed/default-routes.json")) {
            assertThat(fixture).as("src/test/resources/embed/default-routes.json").isNotNull();
            Object expected = JSON.readValue(fixture, Object.class);
            assertThat(JSON.readValue(actual, Object.class)).isEqualTo(expected);
        }
    }
}
