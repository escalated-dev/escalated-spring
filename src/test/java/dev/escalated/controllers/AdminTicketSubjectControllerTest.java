package dev.escalated.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.controllers.admin.AdminTicketSubjectController;
import dev.escalated.dto.SerializedTicketSubjectDto;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketSubjectLink;
import dev.escalated.security.ApiTokenAuthenticationFilter;
import dev.escalated.services.TicketService;
import dev.escalated.services.TicketSubjectService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminTicketSubjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminTicketSubjectControllerTest {

    private static final String SUBJECT_TYPE = "com.example.FakeProject";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiTokenAuthenticationFilter apiTokenFilter;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private TicketSubjectService ticketSubjectService;

    @Test
    void attach_shouldReturnLinkAndSubjects() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        TicketSubjectLink link = new TicketSubjectLink();
        link.setId(5L);
        link.setSubjectType(SUBJECT_TYPE);
        link.setSubjectId("prj_1");

        when(ticketSubjectService.isApiTypeAllowed(SUBJECT_TYPE)).thenReturn(true);
        when(ticketService.findById(1L)).thenReturn(ticket);
        when(ticketSubjectService.attach(eq(ticket), eq(SUBJECT_TYPE), eq("prj_1"), eq("project")))
                .thenReturn(link);
        when(ticketSubjectService.serializeLinks(List.of(link))).thenReturn(List.of(
                new SerializedTicketSubjectDto(SUBJECT_TYPE, "prj_1", "project", "Title",
                        null, null, null, null, true)));

        mockMvc.perform(post("/escalated/api/admin/tickets/1/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + SUBJECT_TYPE + "\",\"id\":\"prj_1\",\"role\":\"project\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjects[0].type").value(SUBJECT_TYPE))
                .andExpect(jsonPath("$.subjects[0].id").value("prj_1"))
                .andExpect(jsonPath("$.subjects[0].missing").value(true));
    }

    @Test
    void attach_shouldRejectDisallowedType() throws Exception {
        when(ticketSubjectService.isApiTypeAllowed("bad")).thenReturn(false);

        mockMvc.perform(post("/escalated/api/admin/tickets/1/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"bad\",\"id\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detach_shouldReturnNoContent() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        when(ticketService.findById(1L)).thenReturn(ticket);

        mockMvc.perform(delete("/escalated/api/admin/tickets/1/subjects/9"))
                .andExpect(status().isNoContent());

        verify(ticketSubjectService).detach(ticket, 9L);
    }
}
