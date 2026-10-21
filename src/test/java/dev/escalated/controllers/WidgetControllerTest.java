package dev.escalated.controllers;

import dev.escalated.controllers.widget.WidgetController;
import dev.escalated.dto.GuestTicketDto;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.services.DepartmentService;
import dev.escalated.services.KnowledgeBaseService;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.TicketService;
import dev.escalated.security.ApiTokenAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WidgetController.class)
@AutoConfigureMockMvc(addFilters = false)
class WidgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiTokenAuthenticationFilter apiTokenFilter;

    @MockitoBean
    private TicketService ticketService;
    @MockitoBean
    private KnowledgeBaseService knowledgeBaseService;
    @MockitoBean
    private SatisfactionRatingService ratingService;
    @MockitoBean
    private DepartmentService departmentService;
    @MockitoBean
    private dev.escalated.services.GuestVerificationService verification;
    @MockitoBean
    private dev.escalated.services.GuestAccessGrants grants;

    @Test
    void createTicket_shouldReturnCreatedTicket() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setSubject("Widget Ticket");
        ticket.setTicketNumber("ESC-000001");
        ticket.setGuestAccessToken("abc123");

        when(ticketService.create(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(ticket);
        when(grants.issue(1L)).thenReturn(new dev.escalated.services.GuestAccessGrants.Grant(
                "eg1.grant", java.time.Instant.parse("2026-10-22T00:00:00Z")));

        mockMvc.perform(post("/escalated/api/widget/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Widget Ticket\",\"body\":\"Help!\",\"name\":\"Guest\",\"email\":\"guest@test.com\","
                                + "\"verification_id\":\"v\",\"verification_code\":\"12345678\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("ESC-000001"))
                .andExpect(jsonPath("$.guest_access_token").value("eg1.grant"))
                .andExpect(jsonPath("$.expires_at").value("2026-10-22T00:00:00Z"))
                .andExpect(jsonPath("$.subject").doesNotExist())
                .andExpect(jsonPath("$.guestAccessToken").doesNotExist());
    }

    @Test
    void getTicketByToken_shouldReturnTicket() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setSubject("Guest Ticket");
        ticket.setGuestAccessToken("token123");

        when(grants.resolve(org.mockito.ArgumentMatchers.eq("token123"), any()))
                .thenReturn(java.util.Optional.of(ticket));
        when(ticketService.guestView(1L)).thenReturn(GuestTicketDto.from(ticket));

        mockMvc.perform(get("/escalated/api/widget/tickets/token123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Guest Ticket"));
    }
}
