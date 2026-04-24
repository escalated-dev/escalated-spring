package dev.escalated.controllers;

import dev.escalated.controllers.admin.AdminSettingsController;
import dev.escalated.security.ApiTokenAuthenticationFilter;
import dev.escalated.services.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the two public-tickets settings endpoints on
 * {@link AdminSettingsController}. The controller delegates all
 * persistence to {@link SettingsService}; these tests use a
 * map-backed fake so we exercise the validation + mode-switch-cleanup
 * semantics end-to-end without booting the DB.
 */
@WebMvcTest(AdminSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminPublicTicketsSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiTokenAuthenticationFilter apiTokenFilter;

    @MockBean
    private SettingsService settingsService;

    private Map<String, String> store;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        // Fake SettingsService by routing get/set through the map.
        when(settingsService.getOrDefault(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> store.getOrDefault(inv.getArgument(0, String.class),
                        inv.getArgument(1, String.class)));
        org.mockito.Mockito.doAnswer(inv -> {
            store.put(inv.getArgument(0, String.class),
                    inv.getArgument(1, String.class));
            return null;
        }).when(settingsService)
                .set(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void get_defaultsToUnassigned_whenNoSettingsWritten() throws Exception {
        mockMvc.perform(get("/escalated/api/admin/settings/public-tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest_policy_mode").value("unassigned"))
                .andExpect(jsonPath("$.guest_policy_user_id").isEmpty())
                .andExpect(jsonPath("$.guest_policy_signup_url_template").value(""));
    }

    @Test
    void put_persistsGuestUserMode_andClearsTemplate() throws Exception {
        String body = """
            {"guest_policy_mode":"guest_user","guest_policy_user_id":42,
             "guest_policy_signup_url_template":"https://ignored.example"}
            """;

        mockMvc.perform(put("/escalated/api/admin/settings/public-tickets")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest_policy_mode").value("guest_user"))
                .andExpect(jsonPath("$.guest_policy_user_id").value(42))
                .andExpect(jsonPath("$.guest_policy_signup_url_template").value(""));

        // Template must be explicitly cleared on non-prompt_signup mode.
        verify(settingsService).set(eq("guest_policy_signup_url_template"), eq(""),
                eq("public_tickets"));
    }

    @Test
    void put_persistsPromptSignupMode_andClearsUserId() throws Exception {
        String body = """
            {"guest_policy_mode":"prompt_signup","guest_policy_user_id":99,
             "guest_policy_signup_url_template":"https://example.com/join?t={{token}}"}
            """;

        mockMvc.perform(put("/escalated/api/admin/settings/public-tickets")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest_policy_mode").value("prompt_signup"))
                .andExpect(jsonPath("$.guest_policy_user_id").isEmpty())
                .andExpect(jsonPath("$.guest_policy_signup_url_template")
                        .value("https://example.com/join?t={{token}}"));

        verify(settingsService).set(eq("guest_policy_user_id"), eq(""), eq("public_tickets"));
    }

    @Test
    void put_unknownMode_coercesToUnassigned() throws Exception {
        String body = """
            {"guest_policy_mode":"bogus","guest_policy_user_id":5,
             "guest_policy_signup_url_template":"ignored"}
            """;

        mockMvc.perform(put("/escalated/api/admin/settings/public-tickets")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest_policy_mode").value("unassigned"))
                .andExpect(jsonPath("$.guest_policy_user_id").isEmpty())
                .andExpect(jsonPath("$.guest_policy_signup_url_template").value(""));
    }

    @Test
    void put_truncatesLongSignupTemplate_at500Chars() throws Exception {
        String longTemplate = "x".repeat(1000);
        String body = "{\"guest_policy_mode\":\"prompt_signup\","
                + "\"guest_policy_signup_url_template\":\"" + longTemplate + "\"}";

        mockMvc.perform(put("/escalated/api/admin/settings/public-tickets")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(settingsService).set(eq("guest_policy_signup_url_template"), captor.capture(),
                eq("public_tickets"));
        // The captured value should be exactly 500 chars (truncated).
        org.junit.jupiter.api.Assertions.assertEquals(500, captor.getValue().length());
    }

    @Test
    void put_zeroUserId_clearsField() throws Exception {
        String body = "{\"guest_policy_mode\":\"guest_user\",\"guest_policy_user_id\":0}";

        mockMvc.perform(put("/escalated/api/admin/settings/public-tickets")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest_policy_user_id").isEmpty());

        verify(settingsService).set(eq("guest_policy_user_id"), eq(""), eq("public_tickets"));
    }

    @Test
    void put_modeSwitch_clearsStaleFields() throws Exception {
        // First, set guest_user with a user id.
        String body1 = "{\"guest_policy_mode\":\"guest_user\",\"guest_policy_user_id\":42}";
        mockMvc.perform(put("/escalated/api/admin/settings/public-tickets")
                        .contentType(MediaType.APPLICATION_JSON).content(body1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest_policy_user_id").value(42));

        // Now switch to unassigned — user_id must clear.
        String body2 = "{\"guest_policy_mode\":\"unassigned\"}";
        mockMvc.perform(put("/escalated/api/admin/settings/public-tickets")
                        .contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest_policy_mode").value("unassigned"))
                .andExpect(jsonPath("$.guest_policy_user_id").isEmpty());
    }

    @Test
    void get_returnsLatestAfterMultipleWrites() throws Exception {
        mockMvc.perform(put("/escalated/api/admin/settings/public-tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"guest_policy_mode\":\"guest_user\",\"guest_policy_user_id\":7}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/escalated/api/admin/settings/public-tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"guest_policy_mode\":\"guest_user\",\"guest_policy_user_id\":15}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/escalated/api/admin/settings/public-tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest_policy_mode").value("guest_user"))
                .andExpect(jsonPath("$.guest_policy_user_id").value(15));
    }
}
