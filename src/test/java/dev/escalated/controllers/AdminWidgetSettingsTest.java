package dev.escalated.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.controllers.admin.AdminSettingsController;
import dev.escalated.security.ApiTokenAuthenticationFilter;
import dev.escalated.services.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code widget_allowed_origins} is saved through the ordinary settings
 * endpoint, and only when every entry is an origin the widget can match.
 */
@WebMvcTest(AdminSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminWidgetSettingsTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ApiTokenAuthenticationFilter apiTokenFilter;
    @MockitoBean private SettingsService settingsService;

    @Test
    void validOriginsAreSaved() throws Exception {
        mockMvc.perform(post("/escalated/api/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"widget_allowed_origins",
                                 "value":"https://shop.example.com\\nhttps://*.example.com, http://localhost:5173/",
                                 "group":"widget"}
                                """))
                .andExpect(status().isOk());

        verify(settingsService).set(eq("widget_allowed_origins"),
                eq("https://shop.example.com\nhttps://*.example.com, http://localhost:5173/"), eq("widget"));
    }

    @Test
    void anEmptyListIsSaved() throws Exception {
        mockMvc.perform(post("/escalated/api/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"widget_allowed_origins\",\"value\":\"\"}"))
                .andExpect(status().isOk());

        verify(settingsService).set(eq("widget_allowed_origins"), eq(""), any());
    }

    @Test
    void anEntryThatIsNotAnOriginIsRejected() throws Exception {
        for (String invalid : new String[] {
            "https://shop.example.com/checkout", "*", "shop.example.com", "ftp://shop.example.com",
            "https://user@shop.example.com", "https://shop.example.com?x=1", "https://a.*.example.com",
        }) {
            mockMvc.perform(post("/escalated/api/admin/settings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"widget_allowed_origins\",\"value\":\"https://ok.example.com\\n"
                                    + invalid + "\"}"))
                    .andExpect(status().is(422));
        }

        verify(settingsService, never()).set(eq("widget_allowed_origins"), anyString(), any());
    }
}
