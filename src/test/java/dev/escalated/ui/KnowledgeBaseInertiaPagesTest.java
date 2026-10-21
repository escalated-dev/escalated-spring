package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.models.KnowledgeBaseCategory;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.KnowledgeBaseArticleRepository;
import dev.escalated.repositories.KnowledgeBaseCategoryRepository;
import dev.escalated.services.SettingsService;
import hostapp.HostApplication;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The knowledge base pages over the Inertia protocol: the admin articles and categories, and the
 * customer index, article and feedback. Shares the other UI tests' context; the knowledge base
 * switch is flipped on the bean for each test and restored after.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class KnowledgeBaseInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private KnowledgeBaseArticleRepository articles;
    @Autowired private KnowledgeBaseCategoryRepository categories;
    @Autowired private EscalatedProperties properties;
    @Autowired private SettingsService settings;

    private String unique;
    private AgentProfile admin;
    private AgentProfile agent;
    private boolean wasEnabled;

    @BeforeEach
    void seed() {
        wasEnabled = properties.getKnowledgeBase().isEnabled();
        properties.getKnowledgeBase().setEnabled(true);
        unique = UUID.randomUUID().toString().substring(0, 8);
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin = agents.save(admin);

        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent = agents.save(agent);
    }

    @AfterEach
    void restore() {
        properties.getKnowledgeBase().setEnabled(wasEnabled);
        settings.set("knowledge_base_public", "1", "general");
        settings.set("knowledge_base_feedback_enabled", "1", "general");
    }

    @Test
    void anAgentWhoIsNotAnAdminCannotManageArticles() throws Exception {
        for (String path : new String[] {"/escalated/admin/kb-articles", "/escalated/admin/kb-articles/create",
                "/escalated/admin/kb-categories"}) {
            mvc.perform(get(path).with(user(agent.getEmail()))).andExpect(status().isForbidden());
        }
    }

    @Test
    void categoriesAreListedCreatedNestedRenamedAndDeleted() throws Exception {
        write(post("/escalated/admin/kb-categories"),
                "{\"name\":\"Billing " + unique + "\",\"slug\":\"\",\"parent_id\":\"\",\"position\":2,"
                        + "\"description\":\"Money\"}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/kb-categories"));
        KnowledgeBaseCategory parent = categories.findBySlug("billing-" + unique).orElseThrow();
        write(post("/escalated/admin/kb-categories"),
                "{\"name\":\"Refunds " + unique + "\",\"parent_id\":" + parent.getId() + "}")
                .andExpect(status().isSeeOther());
        KnowledgeBaseCategory child = categories.findBySlug("refunds-" + unique).orElseThrow();

        JsonNode page = inertia(get("/escalated/admin/kb-categories"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/KnowledgeBase/Categories/Index");
        JsonNode row = find(page.get("props").get("categories"), parent.getId());
        assertThat(fieldNames(row)).contains("id", "name", "slug", "description", "parent_id", "position",
                "articles_count");
        assertThat(row.get("position").asInt()).isEqualTo(2);
        assertThat(find(page.get("props").get("categories"), child.getId()).get("parent_id").asLong())
                .isEqualTo(parent.getId());

        write(put("/escalated/admin/kb-categories/" + parent.getId()),
                "{\"name\":\"Payments " + unique + "\",\"slug\":\"payments-" + unique + "\",\"parent_id\":"
                        + child.getId() + "}")
                .andExpect(status().isSeeOther());
        assertThat(categories.findById(parent.getId()).orElseThrow().getName()).isEqualTo("Billing " + unique);

        write(put("/escalated/admin/kb-categories/" + parent.getId()),
                "{\"name\":\"Payments " + unique + "\",\"slug\":\"payments-" + unique + "\"}")
                .andExpect(status().isSeeOther());
        assertThat(categories.findById(parent.getId()).orElseThrow().getSlug()).isEqualTo("payments-" + unique);

        KnowledgeBaseArticle article = article("Filed " + unique, true, parent);
        write(delete("/escalated/admin/kb-categories/" + parent.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(categories.existsById(parent.getId())).isFalse();
        assertThat(categories.findById(child.getId()).orElseThrow().getParent()).isNull();
        assertThat(articles.findById(article.getId()).orElseThrow().getCategory()).isNull();
    }

    @Test
    void articlesAreListedFilteredCreatedEditedAndDeleted() throws Exception {
        KnowledgeBaseCategory category = category("Setup " + unique);
        write(post("/escalated/admin/kb-articles"),
                "{\"title\":\"Getting Started " + unique + "\",\"slug\":\"\",\"body\":\"<p>Hello</p>\","
                        + "\"status\":\"draft\",\"category_id\":" + category.getId() + "}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/kb-articles"));
        KnowledgeBaseArticle created = articles.findBySlug("getting-started-" + unique).orElseThrow();
        assertThat(created.isPublished()).isFalse();
        assertThat(created.getAuthorName()).isEqualTo(admin.getName());

        JsonNode page = inertia(get("/escalated/admin/kb-articles").param("search", unique));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/KnowledgeBase/Articles/Index");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("articles", "categories", "filters");
        assertThat(props.get("filters").get("search").asText()).isEqualTo(unique);
        assertThat(fieldNames(props.get("articles"))).contains("data", "current_page", "last_page", "links", "total");
        JsonNode row = props.get("articles").get("data").get(0);
        assertThat(fieldNames(row)).contains("id", "title", "slug", "status", "category", "view_count",
                "helpful_count", "created_at");
        assertThat(row.get("status").asText()).isEqualTo("draft");
        assertThat(row.get("category").get("name").asText()).isEqualTo("Setup " + unique);
        assertThat(fieldNames(find(props.get("categories"), category.getId()))).containsExactly("id", "name");
        assertThat(inertia(get("/escalated/admin/kb-articles").param("search", unique).param("status", "published"))
                .get("props").get("articles").get("total").asLong()).isZero();

        JsonNode form = inertia(get("/escalated/admin/kb-articles/" + created.getId() + "/edit"));
        assertThat(form.get("component").asText()).isEqualTo("Escalated/Admin/KnowledgeBase/Articles/Form");
        JsonNode article = form.get("props").get("article");
        assertThat(fieldNames(article)).contains("id", "title", "slug", "body", "status", "category_id");
        assertThat(article.get("body").asText()).isEqualTo("<p>Hello</p>");
        assertThat(article.get("category_id").asLong()).isEqualTo(category.getId());
        assertThat(inertia(get("/escalated/admin/kb-articles/create")).get("props").has("categories")).isTrue();

        write(put("/escalated/admin/kb-articles/" + created.getId()),
                "{\"title\":\"Getting Started " + unique + "\",\"slug\":\"start-" + unique + "\",\"body\":\"Hi\","
                        + "\"status\":\"published\",\"category_id\":null}")
                .andExpect(status().isSeeOther());
        KnowledgeBaseArticle published = articles.findById(created.getId()).orElseThrow();
        assertThat(published.isPublished()).isTrue();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getSlug()).isEqualTo("start-" + unique);
        assertThat(published.getCategory()).isNull();

        write(delete("/escalated/admin/kb-articles/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(articles.existsById(created.getId())).isFalse();
    }

    @Test
    void aTakenSlugOrBadStatusGoesBackWithErrors() throws Exception {
        article("Taken " + unique, true, null);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/kb-articles").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/kb-articles/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Taken " + unique + "\",\"status\":\"published\"}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/kb-articles/create"));
        assertThat(inertia(get("/escalated/admin/kb-articles/create").session(session)).get("props").get("errors")
                .get("slug").asText()).isEqualTo("The slug has already been taken.");

        mvc.perform(post("/escalated/admin/kb-articles").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/kb-articles/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Other " + unique + "\",\"status\":\"live\"}"))
                .andExpect(status().isSeeOther());
        assertThat(inertia(get("/escalated/admin/kb-articles/create").session(session)).get("props").get("errors")
                .get("status").asText()).isEqualTo("The selected status is invalid.");
    }

    @Test
    void customersBrowseSearchAndReadPublishedArticles() throws Exception {
        KnowledgeBaseCategory category = category("Accounts " + unique);
        KnowledgeBaseArticle shown = article("Reset password " + unique, true, category);
        KnowledgeBaseArticle sibling = article("Change email " + unique, true, category);
        KnowledgeBaseArticle draft = article("Secret draft " + unique, false, category);

        JsonNode index = customer(get("/escalated/kb").param("search", unique));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Customer/KnowledgeBase/Index");
        JsonNode props = index.get("props");
        assertThat(fieldNames(props)).contains("categories", "articles", "filters", "feedbackEnabled");
        assertThat(props.get("articles").get("total").asLong()).isEqualTo(2);
        JsonNode row = find(props.get("articles").get("data"), shown.getId());
        assertThat(fieldNames(row)).contains("id", "title", "slug", "view_count", "category");
        assertThat(row.get("category").get("name").asText()).isEqualTo("Accounts " + unique);
        assertThat(index.toString()).doesNotContain("Secret draft " + unique);
        JsonNode listedCategory = find(props.get("categories"), category.getId());
        assertThat(fieldNames(listedCategory)).contains("id", "name", "description", "articles_count");
        assertThat(listedCategory.get("articles_count").asInt()).isEqualTo(2);
        assertThat(customer(get("/escalated/kb").param("category", category.getId().toString()).param("search", "Reset"))
                .get("props").get("articles").get("total").asLong()).isEqualTo(1);

        JsonNode page = customer(get("/escalated/kb/" + shown.getSlug()));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Customer/KnowledgeBase/Article");
        JsonNode article = page.get("props").get("article");
        assertThat(fieldNames(article)).contains("id", "title", "slug", "body", "published_at", "view_count", "category");
        assertThat(article.get("category").get("id").asLong()).isEqualTo(category.getId());
        JsonNode related = page.get("props").get("related");
        assertThat(related.size()).isEqualTo(1);
        assertThat(fieldNames(related.get(0))).containsExactly("id", "title", "slug");
        assertThat(related.get(0).get("id").asLong()).isEqualTo(sibling.getId());
        assertThat(articles.findById(shown.getId()).orElseThrow().getViewCount()).isEqualTo(1);

        mvc.perform(get("/escalated/kb/" + draft.getSlug()).with(user("someone-" + unique + "@example.com"))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void feedbackIsCountedOnceAndCanBeSwitchedOff() throws Exception {
        KnowledgeBaseArticle article = article("Useful " + unique, true, null);
        String path = "/escalated/kb/" + article.getSlug();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post(path + "/feedback").session(session).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost" + path)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":true}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", path));
        mvc.perform(post(path + "/feedback").with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":false}"))
                .andExpect(status().isSeeOther());
        KnowledgeBaseArticle counted = articles.findById(article.getId()).orElseThrow();
        assertThat(counted.getHelpfulCount()).isEqualTo(1);
        assertThat(counted.getNotHelpfulCount()).isEqualTo(1);

        settings.set("knowledge_base_feedback_enabled", "0", "general");
        mvc.perform(post(path + "/feedback").with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":true}"))
                .andExpect(status().isNotFound());
        assertThat(customer(get(path)).get("props").get("feedbackEnabled").asBoolean()).isFalse();
    }

    @Test
    void theKnowledgeBaseIsPublicUntilTurnedOffAndAbsentWhenDisabled() throws Exception {
        KnowledgeBaseArticle article = article("Public " + unique, true, null);
        // Not signed in: readable while public (the default).
        MvcResult anonymous = mvc.perform(get("/escalated/kb/" + article.getSlug())
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk()).andReturn();
        assertThat(JSON.readTree(anonymous.getResponse().getContentAsString()).get("props").get("auth").get("user")
                .isNull()).isTrue();

        settings.set("knowledge_base_public", "0", "general");
        mvc.perform(get("/escalated/kb").header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isForbidden());
        customer(get("/escalated/kb"));

        properties.getKnowledgeBase().setEnabled(false);
        mvc.perform(get("/escalated/kb").with(user("someone-" + unique + "@example.com"))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isNotFound());
    }

    private KnowledgeBaseCategory category(String name) {
        KnowledgeBaseCategory category = new KnowledgeBaseCategory();
        category.setName(name);
        category.setSlug(KnowledgeBaseUiPages.slugify(name));
        return categories.save(category);
    }

    private KnowledgeBaseArticle article(String title, boolean published, KnowledgeBaseCategory category) {
        KnowledgeBaseArticle article = new KnowledgeBaseArticle();
        article.setTitle(title);
        article.setSlug(KnowledgeBaseUiPages.slugify(title));
        article.setContent("Body of " + title);
        article.setPublished(published);
        article.setPublishedAt(published ? Instant.now() : null);
        article.setCategory(category);
        return articles.save(article);
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.with(user(admin.getEmail())).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode customer(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(user("customer-" + unique + "@example.com"))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(user(admin.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode array, Long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return item;
            }
        }
        throw new AssertionError(id + " not in " + array);
    }
}
