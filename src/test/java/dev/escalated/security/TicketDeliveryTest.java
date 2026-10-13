package dev.escalated.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.ChatSession;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.ChatSessionRepository;
import dev.escalated.services.ChatRoutingService;
import dev.escalated.services.ChatSessionService;
import dev.escalated.services.EmailService;
import dev.escalated.services.TicketService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.util.Optional;

class TicketDeliveryTest {
    @Test
    @SuppressWarnings("unchecked")
    void restrictedChatUsesAuthorizedDeliveryAndNeverSharedTopics() {
        TicketService tickets = mock(TicketService.class);
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        when(tickets.create(anyString(), anyString(), anyString(), anyString(), any(), isNull()))
                .thenReturn(ticket);
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        ChatRoutingService routing = mock(ChatRoutingService.class);
        when(routing.resolve(null)).thenReturn(new ChatRoutingService.RouteResult(null, null));
        SimpMessagingTemplate shared = mock(SimpMessagingTemplate.class);
        TicketAccess access = mock(TicketAccess.class);
        when(access.enabled()).thenReturn(true);
        ObjectProvider<AuthorizedTicketBroadcaster> provider = mock(ObjectProvider.class);
        AuthorizedTicketBroadcaster authorized = mock(AuthorizedTicketBroadcaster.class);
        when(provider.getIfAvailable()).thenReturn(authorized);
        ChatSessionService chat =
                new ChatSessionService(sessions, tickets, routing, Optional.of(shared));
        ReflectionTestUtils.setField(chat, "access", access);
        ReflectionTestUtils.setField(chat, "broadcasters", provider);
        ChatSession session = chat.start("Customer", "customer@example.test", "Help", null);
        verify(authorized).deliver(1L, "/topic/chat-queue", session);
        verifyNoInteractions(shared);
        when(provider.getIfAvailable()).thenReturn(null);
        chat.start("Customer", "customer@example.test", "Help", null);
        verifyNoInteractions(shared);
    }

    @Test
    void anIneligibleAssigneeReceivesNoEmail() {
        JavaMailSender mail = mock(JavaMailSender.class);
        TemplateEngine templates = mock(TemplateEngine.class);
        TicketAccess access = mock(TicketAccess.class);
        when(access.enabled()).thenReturn(true);
        EmailService email = new EmailService(mail, templates, new EscalatedProperties());
        ReflectionTestUtils.setField(email, "access", access);
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        AgentProfile agent = new AgentProfile();
        agent.setId(2L);
        agent.setEmail("agent@example.test");
        ticket.setAssignedAgent(agent);
        Reply reply = new Reply();
        reply.setAuthorType("customer");
        email.sendReplyNotification(ticket, reply);
        verify(access).canReceiveStaff(1L, 2L);
        verifyNoInteractions(mail, templates);
    }
}
