package dev.escalated.services;

import dev.escalated.models.Ticket;
import dev.escalated.models.TicketLink;
import dev.escalated.repositories.TicketLinkRepository;
import dev.escalated.repositories.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketLinkService {

    private final TicketLinkRepository ticketLinkRepository;
    private final TicketRepository ticketRepository;

    public TicketLinkService(TicketLinkRepository ticketLinkRepository,
                             TicketRepository ticketRepository) {
        this.ticketLinkRepository = ticketLinkRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public List<TicketLink> findByTicket(Long ticketId) {
        return ticketLinkRepository.findByTicketId(ticketId);
    }

    @Transactional
    public TicketLink create(Long sourceTicketId, Long targetTicketId, String linkType) {
        Ticket source = ticketRepository.findById(sourceTicketId)
                .orElseThrow(() -> new EntityNotFoundException("Source ticket not found: " + sourceTicketId));
        Ticket target = ticketRepository.findById(targetTicketId)
                .orElseThrow(() -> new EntityNotFoundException("Target ticket not found: " + targetTicketId));

        TicketLink link = new TicketLink();
        link.setSourceTicket(source);
        link.setTargetTicket(target);
        link.setLinkType(linkType != null ? linkType : "related");
        return ticketLinkRepository.save(link);
    }

    @Transactional
    public void delete(Long id) {
        ticketLinkRepository.deleteById(id);
    }
}
