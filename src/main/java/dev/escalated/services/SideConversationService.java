package dev.escalated.services;

import dev.escalated.models.SideConversation;
import dev.escalated.models.SideConversationReply;
import dev.escalated.repositories.SideConversationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SideConversationService {

    private final SideConversationRepository sideConversationRepository;

    public SideConversationService(SideConversationRepository sideConversationRepository) {
        this.sideConversationRepository = sideConversationRepository;
    }

    @Transactional(readOnly = true)
    public List<SideConversation> findByTicket(Long ticketId) {
        return sideConversationRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    @Transactional(readOnly = true)
    public SideConversation findById(Long id) {
        return sideConversationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Side conversation not found: " + id));
    }

    @Transactional
    public SideConversation create(Long ticketId, String subject, String participantEmails,
                                   String initialMessage, String authorName, String authorEmail) {
        SideConversation sc = new SideConversation();
        sc.setTicket(new dev.escalated.models.Ticket());
        sc.getTicket().setId(ticketId);
        sc.setSubject(subject);
        sc.setParticipantEmails(participantEmails);

        SideConversation saved = sideConversationRepository.save(sc);

        SideConversationReply reply = new SideConversationReply();
        reply.setSideConversation(saved);
        reply.setBody(initialMessage);
        reply.setAuthorName(authorName);
        reply.setAuthorEmail(authorEmail);
        saved.getReplies().add(reply);

        return sideConversationRepository.save(saved);
    }

    @Transactional
    public SideConversationReply addReply(Long sideConversationId, String body,
                                          String authorName, String authorEmail) {
        SideConversation sc = findById(sideConversationId);
        SideConversationReply reply = new SideConversationReply();
        reply.setSideConversation(sc);
        reply.setBody(body);
        reply.setAuthorName(authorName);
        reply.setAuthorEmail(authorEmail);
        sc.getReplies().add(reply);
        sideConversationRepository.save(sc);
        return reply;
    }

    @Transactional
    public void close(Long id) {
        SideConversation sc = findById(id);
        sc.setStatus("closed");
        sideConversationRepository.save(sc);
    }
}
