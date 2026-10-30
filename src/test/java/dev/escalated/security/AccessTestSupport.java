package dev.escalated.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Permission;
import dev.escalated.models.Role;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.PermissionRepository;
import dev.escalated.repositories.RoleRepository;
import jakarta.servlet.http.Cookie;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Helpers for the access-feature tests: staff users, roles, Inertia requests. */
final class AccessTestSupport {

    static final ObjectMapper JSON = new ObjectMapper();

    private AccessTestSupport() {
    }

    static AgentProfile staff(AgentProfileRepository agents, String kind, boolean admin, Role role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AgentProfile profile = new AgentProfile();
        profile.setName(kind + " " + unique);
        profile.setEmail(kind + "-" + unique + "@example.com");
        profile.setAgent(true);
        profile.setAdmin(admin);
        profile.setRole(role);
        return agents.save(profile);
    }

    static Role role(RoleRepository roles, PermissionRepository permissions, String... slugs) {
        Role role = new Role();
        role.setName("custom-" + UUID.randomUUID().toString().substring(0, 8));
        role.setPermissions(new HashSet<>());
        for (String slug : slugs) {
            Permission permission = permissions.findByName(slug).orElseThrow();
            role.getPermissions().add(permission);
        }
        return roles.save(role);
    }

    /** The CSRF token as Inertia's client sends it. */
    static RequestPostProcessor xsrf() {
        return request -> {
            String token = UUID.randomUUID().toString();
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    /** An Inertia write from a page, as {@code who}. */
    static MockHttpServletRequestBuilder page(MockHttpServletRequestBuilder request, AgentProfile who, String json) {
        return request.with(user(who.getEmail())).with(xsrf()).header("X-Inertia", "true")
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    /** A JSON API call, as {@code who}. */
    static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder request, AgentProfile who, String json) {
        return request.with(user(who.getEmail())).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    /** The {@code escalated} shared props of an Inertia page, as {@code who}. */
    static JsonNode shared(MockMvc mvc, String path, AgentProfile who) throws Exception {
        String body = mvc.perform(get(path).with(user(who.getEmail())).header("X-Inertia", "true")
                        .header("X-Inertia-Version", "build-1"))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("props").get("escalated");
    }
}
