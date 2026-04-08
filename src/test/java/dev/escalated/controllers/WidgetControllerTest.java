package dev.escalated.controllers;

import dev.escalated.controllers.widget.WidgetController;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.services.KnowledgeBaseService;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    private TicketService ticketService;
    @MockBean
    private KnowledgeBaseService knowledgeBaseService;
    @MockBean
    private SatisfactionRatingService ratingService;

    @Test
    void createTicket_shouldReturnCreatedTicket() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setSubject("Widget Ticket");
        ticket.setTicketNumber("ESC-000001");
        ticket.setGuestAccessToken("abc123");

        when(ticketService.create(any(), any(), any(), any(), any(), any())).thenReturn(ticket);

        mockMvc.perform(post("/escalated/api/widget/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Widget Ticket\",\"body\":\"Help!\",\"name\":\"Guest\",\"email\":\"guest@test.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("Widget Ticket"))
                .andExpect(jsonPath("$.guestAccessToken").value("abc123"));
    }

    @Test
    void getTicketByToken_shouldReturnTicket() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setSubject("Guest Ticket");
        ticket.setGuestAccessToken("token123");

        when(ticketService.findByGuestToken("token123")).thenReturn(ticket);

        mockMvc.perform(get("/escalated/api/widget/tickets/token123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Guest Ticket"));
    }
}
