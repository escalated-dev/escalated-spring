package dev.escalated.controllers;

import dev.escalated.controllers.admin.AdminTicketController;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.services.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminTicketController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminTicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @Test
    void index_shouldReturnTickets() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setSubject("Test");
        ticket.setTicketNumber("ESC-000001");
        ticket.setStatus(TicketStatus.OPEN);

        Page<Ticket> page = new PageImpl<>(List.of(ticket));
        when(ticketService.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/escalated/api/admin/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subject").value("Test"));
    }

    @Test
    void show_shouldReturnTicket() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setSubject("Test Ticket");
        ticket.setTicketNumber("ESC-000001");

        when(ticketService.findById(1L)).thenReturn(ticket);

        mockMvc.perform(get("/escalated/api/admin/tickets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Test Ticket"));
    }

    @Test
    void store_shouldCreateTicket() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setSubject("New Ticket");
        ticket.setTicketNumber("ESC-000001");

        when(ticketService.create(any(), any(), any(), any(), any(), any())).thenReturn(ticket);

        mockMvc.perform(post("/escalated/api/admin/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"New Ticket\",\"body\":\"Body\",\"requester_name\":\"User\",\"requester_email\":\"user@test.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("New Ticket"));
    }

    @Test
    void changeStatus_shouldUpdateStatus() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus(TicketStatus.RESOLVED);

        when(ticketService.changeStatus(eq(1L), eq(TicketStatus.RESOLVED), any())).thenReturn(ticket);

        mockMvc.perform(post("/escalated/api/admin/tickets/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"actor_email\":\"admin@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
