package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Attachment;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AttachmentRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /escalated/api/attachments/{id}/download} through the real
 * security chain. It used to hand any signed-in user any attachment by
 * sequential id, including another customer's files and files on internal
 * notes. Now it follows the Laravel reference's AttachmentAccess.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class AttachmentDownloadAccessTest {

    private static final String DOWNLOAD = "/escalated/api/attachments/{id}/download";

    @Autowired private MockMvc mockMvc;
    @Autowired private TicketService ticketService;
    @Autowired private AttachmentRepository attachments;
    @Autowired private AgentProfileRepository agents;

    @TempDir Path files;

    private String alice;
    private String bob;
    private Attachment onAlicesTicket;
    private Attachment onAlicesPublicReply;
    private Attachment onAlicesInternalNote;
    private Attachment onBobsTicket;

    @BeforeEach
    void seed() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        alice = "alice-" + run + "@example.com";
        bob = "bob-" + run + "@example.com";
        Ticket alicesTicket = ticketService.create("Alice", "Mine", "Alice", alice, TicketPriority.MEDIUM, null);
        Ticket bobsTicket = ticketService.create("Bob", "Private", "Bob", bob, TicketPriority.MEDIUM, null);
        Reply publicReply = ticketService.addReply(alicesTicket.getId(), "Here you go", "Agent", "agent@example.com",
                "agent", false);
        Reply internalNote = ticketService.addReply(alicesTicket.getId(), "Staff only", "Agent", "agent@example.com",
                "agent", true);

        onAlicesTicket = attach(alicesTicket, null, "invoice.pdf", "application/pdf");
        onAlicesPublicReply = attach(null, publicReply, "steps.txt", "text/plain");
        onAlicesInternalNote = attach(null, internalNote, "fraud-check.txt", "text/plain");
        onBobsTicket = attach(bobsTicket, null, "medical.pdf", "application/pdf");
    }

    @Test
    void theRequesterDownloadsTheirOwnTicketsAttachments() throws Exception {
        mockMvc.perform(get(DOWNLOAD, onAlicesTicket.getId()).with(user(alice))).andExpect(status().isOk());
        mockMvc.perform(get(DOWNLOAD, onAlicesPublicReply.getId()).with(user(alice))).andExpect(status().isOk());
    }

    @Test
    void anotherCustomersAttachmentIsNotFound() throws Exception {
        mockMvc.perform(get(DOWNLOAD, onBobsTicket.getId()).with(user(alice))).andExpect(status().isNotFound());
    }

    @Test
    void anInternalNotesAttachmentIsNotFoundForTheRequester() throws Exception {
        mockMvc.perform(get(DOWNLOAD, onAlicesInternalNote.getId()).with(user(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffDownloadAnyAttachmentIncludingInternalNotes() throws Exception {
        AgentProfile agent = new AgentProfile();
        String unique = UUID.randomUUID().toString();
        agent.setName("Agent " + unique);
        agent.setEmail("staff-" + unique + "@example.com");
        agent.setAgent(true);
        agents.save(agent);

        mockMvc.perform(get(DOWNLOAD, onBobsTicket.getId()).with(user(agent.getEmail()))).andExpect(status().isOk());
        mockMvc.perform(get(DOWNLOAD, onAlicesInternalNote.getId()).with(user(agent.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownIdIsNotFound() throws Exception {
        mockMvc.perform(get(DOWNLOAD, 987654321L).with(user(alice))).andExpect(status().isNotFound());
    }

    @Test
    void anonymousCallersAreRefused() throws Exception {
        mockMvc.perform(get(DOWNLOAD, onAlicesTicket.getId())).andExpect(status().is4xxClientError());
    }

    @Test
    void anHtmlFileIsServedAsOpaqueBytesAndNeverInline() throws Exception {
        Ticket ticket = ticketService.create("Html", "x", "Alice", alice, TicketPriority.MEDIUM, null);
        Attachment html = attach(ticket, null, "page.html", "text/html");

        mockMvc.perform(get(DOWNLOAD, html.getId()).with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void theFilenameCannotBreakTheHeaderAndKeepsItsCharacters() throws Exception {
        Ticket ticket = ticketService.create("Name", "x", "Alice", alice, TicketPriority.MEDIUM, null);
        String name = "re\"port; filename=evil.html été.pdf";
        Attachment attachment = attach(ticket, null, name, "application/pdf");

        MockHttpServletResponse response = mockMvc.perform(get(DOWNLOAD, attachment.getId()).with(user(alice)))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String disposition = response.getHeader("Content-Disposition");
        ContentDisposition parsed = ContentDisposition.parse(disposition);
        assertThat(parsed.isAttachment()).isTrue();
        assertThat(parsed.getFilename()).isEqualTo(name);
        assertThat(disposition).contains("filename*=UTF-8''");
    }

    private Attachment attach(Ticket ticket, Reply reply, String name, String mimeType) throws Exception {
        Path file = Files.createTempFile(files, "attachment", ".bin");
        Files.writeString(file, "<script>alert(1)</script>", StandardCharsets.UTF_8);
        Attachment attachment = new Attachment();
        attachment.setTicket(ticket);
        attachment.setReply(reply);
        attachment.setFileName(name);
        attachment.setFilePath(file.toString());
        attachment.setFileSize(Files.size(file));
        attachment.setMimeType(mimeType);
        return attachments.save(attachment);
    }
}
