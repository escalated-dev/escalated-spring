package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import org.junit.jupiter.api.Test;

/**
 * {@code escalated.ui.embed.root-view} (host type B): an embedded admin page's first visit renders
 * in the host's own Thymeleaf layout ({@code templates/host/admin.html}, the template
 * {@code docs/embedding-admin.md} shows), its sidebar listing the admin menu; nothing else does.
 */
class EmbeddedAdminRootViewTest extends EmbedTestSupport {

    @Test
    void embeddedFirstVisitsRenderInTheHostsLayout() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        String html = mvc.perform(get("/admin/support/tickets").session(signedIn(admin.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("<aside class=\"host-sidebar\">", "<a href=\"/admin/orders\">Orders</a>");
        assertThat(html.replaceAll("\\s+", " "))
                .contains("<a href=\"/admin/support/tickets\" aria-current=\"page\">Tickets</a>");
        assertThat(html).contains("<a href=\"/admin/support/settings\">Settings</a>");
        assertThat(html).contains("<div id=\"app\" data-page=\"{&quot;component&quot;:&quot;Escalated/Admin/Tickets/Index&quot;");
        assertThat(html).contains("window.route=route").contains("<meta name=\"csrf-token\"");
    }

    @Test
    void otherPagesKeepTheBuiltInDocument() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        String html = mvc.perform(get("/escalated/agent/tickets").session(signedIn(admin.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("host-sidebar").contains("<script data-page=\"app\"");
    }
}
