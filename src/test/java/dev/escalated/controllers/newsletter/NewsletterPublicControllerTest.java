package dev.escalated.controllers.newsletter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.NewsletterDeliveryRepository;
import dev.escalated.repositories.NewsletterRepository;
import dev.escalated.repositories.NewsletterTemplateRepository;
import dev.escalated.services.ApiTokenService;
import dev.escalated.services.newsletter.NewsletterRenderer;
import dev.escalated.services.newsletter.NewsletterTracker;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NewsletterPublicController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "escalated.newsletters.enabled=true")
class NewsletterPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsletterTracker tracker;
    @MockitoBean
    private NewsletterRenderer renderer;
    @MockitoBean
    private NewsletterDeliveryRepository deliveries;
    @MockitoBean
    private NewsletterRepository newsletters;
    @MockitoBean
    private NewsletterTemplateRepository templates;
    @MockitoBean
    private ContactRepository contacts;
    @MockitoBean
    private ApiTokenService apiTokenService;

    @Test
    void open_returnsPixelAndRecordsOpen() throws Exception {
        mockMvc.perform(get("/escalated/n/o/abc.gif"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        verify(tracker).recordOpen("abc");
    }

    @Test
    void view_returnsUnavailableHtmlWhenMissing() throws Exception {
        when(deliveries.findByTrackingToken(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/escalated/n/v/missing"))
                .andExpect(status().isOk());
    }
}
