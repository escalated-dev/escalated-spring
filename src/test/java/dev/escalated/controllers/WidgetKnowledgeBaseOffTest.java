package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.services.KnowledgeBaseService;
import hostapp.HostApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * With {@code escalated.knowledge-base.enabled=false} (the default) the
 * widget's article routes answer 404, as Laravel's
 * {@code ensureKnowledgeBaseAvailable} does. They used to serve every
 * published article regardless.
 */
@SpringBootTest(classes = HostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class WidgetKnowledgeBaseOffTest {

    @LocalServerPort private int port;
    @Autowired private KnowledgeBaseService knowledgeBase;

    @Test
    void publishedArticlesAreHiddenWhileTheKnowledgeBaseIsOff() throws Exception {
        String word = "kboff" + UUID.randomUUID().toString().substring(0, 8);
        KnowledgeBaseArticle article = knowledgeBase.createArticle(
                "Reset " + word, "reset-" + word, "<p>Steps</p>", "Steps", null, "Ada");
        knowledgeBase.publishArticle(article.getId());

        for (String path : new String[] {
            "/escalated/api/widget/articles?q=" + word,
            "/escalated/api/widget/articles/reset-" + word,
            "/escalated/api/widget/kb/search?query=" + word,
        }) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(path).isEqualTo(404);
            assertThat(response.body()).as(path).doesNotContain(word);
        }
    }
}
