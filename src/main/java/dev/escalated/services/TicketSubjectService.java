package dev.escalated.services;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.contracts.TicketSubject;
import dev.escalated.dto.SerializedTicketSubjectDto;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketSubjectLink;
import dev.escalated.repositories.TicketSubjectLinkRepository;
import dev.escalated.repositories.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketSubjectService {

    private final TicketSubjectLinkRepository linkRepository;
    private final TicketRepository ticketRepository;
    private final EscalatedProperties properties;
    private final ObjectProvider<TicketSubjectResolver> resolverProvider;

    public TicketSubjectService(TicketSubjectLinkRepository linkRepository,
                                TicketRepository ticketRepository,
                                EscalatedProperties properties,
                                ObjectProvider<TicketSubjectResolver> resolverProvider) {
        this.linkRepository = linkRepository;
        this.ticketRepository = ticketRepository;
        this.properties = properties;
        this.resolverProvider = resolverProvider;
    }

    private List<String> allowedTypes() {
        EscalatedProperties.TicketSubjectsProperties config = properties.getTicketSubjects();
        if (config == null || config.getTypes() == null) {
            return List.of();
        }
        return config.getTypes();
    }

    private void assertTypeAllowed(String subjectType) {
        List<String> allowed = allowedTypes();
        if (!allowed.isEmpty() && !allowed.contains(subjectType)) {
            throw new IllegalArgumentException(
                    "Subject type [" + subjectType + "] is not an allowed ticket subject.");
        }
    }

    /**
     * Returns true when the type may be attached via the agent/admin API
     * (non-empty allowlist and type is listed).
     */
    public boolean isApiTypeAllowed(String subjectType) {
        List<String> allowed = allowedTypes();
        return !allowed.isEmpty() && allowed.contains(subjectType);
    }

    @Transactional(readOnly = true)
    public List<TicketSubjectLink> list(Ticket ticket) {
        return linkRepository.findByTicketIdOrderByPositionAsc(ticket.getId());
    }

    @Transactional(readOnly = true)
    public List<TicketSubjectLink> listByTicketId(Long ticketId) {
        return linkRepository.findByTicketIdOrderByPositionAsc(ticketId);
    }

    @Transactional
    public TicketSubjectLink attach(Ticket ticket, String subjectType, Object subjectId, String role) {
        assertTypeAllowed(subjectType);
        String id = String.valueOf(subjectId);

        Optional<TicketSubjectLink> existing =
                linkRepository.findByTicketIdAndSubjectTypeAndSubjectId(ticket.getId(), subjectType, id);
        if (existing.isPresent()) {
            TicketSubjectLink link = existing.get();
            if (role != null) {
                link.setRole(role);
            }
            return linkRepository.save(link);
        }

        TicketSubjectLink link = new TicketSubjectLink();
        link.setTicket(ticket);
        link.setSubjectType(subjectType);
        link.setSubjectId(id);
        link.setRole(role);
        link.setPosition(linkRepository.findMaxPositionByTicketId(ticket.getId()) + 1);
        return linkRepository.save(link);
    }

    @Transactional
    public TicketSubjectLink attach(Long ticketId, String subjectType, Object subjectId, String role) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + ticketId));
        return attach(ticket, subjectType, subjectId, role);
    }

    @Transactional
    public void detach(Ticket ticket, Long linkId) {
        TicketSubjectLink link = linkRepository.findByIdAndTicketId(linkId, ticket.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ticket subject link #" + linkId + " not found"));
        linkRepository.delete(link);
    }

    @Transactional
    public void detachByKey(Ticket ticket, String subjectType, Object subjectId) {
        String id = String.valueOf(subjectId);
        linkRepository.findByTicketIdAndSubjectTypeAndSubjectId(ticket.getId(), subjectType, id)
                .ifPresent(linkRepository::delete);
    }

    /**
     * Replace all subjects on a ticket with the given items, preserving order.
     */
    @Transactional
    public List<TicketSubjectLink> sync(Ticket ticket, List<SyncItem> items) {
        linkRepository.deleteByTicketId(ticket.getId());

        List<TicketSubjectLink> links = new ArrayList<>();
        int position = 0;
        for (SyncItem item : items) {
            assertTypeAllowed(item.subjectType());
            TicketSubjectLink link = new TicketSubjectLink();
            link.setTicket(ticket);
            link.setSubjectType(item.subjectType());
            link.setSubjectId(String.valueOf(item.subjectId()));
            link.setRole(item.role());
            link.setPosition(position++);
            links.add(linkRepository.save(link));
        }
        return links;
    }

    @Transactional(readOnly = true)
    public List<SerializedTicketSubjectDto> serializeLinks(List<TicketSubjectLink> links) {
        TicketSubjectResolver resolver = resolverProvider.getIfAvailable();
        List<SerializedTicketSubjectDto> result = new ArrayList<>();
        for (TicketSubjectLink link : links) {
            TicketSubject resolved = resolver != null
                    ? resolver.resolve(link.getSubjectType(), link.getSubjectId())
                    : null;
            boolean presents = resolved != null;
            result.add(new SerializedTicketSubjectDto(
                    link.getSubjectType(),
                    link.getSubjectId(),
                    link.getRole(),
                    presents ? resolved.ticketSubjectTitle()
                            : link.getSubjectType() + "#" + link.getSubjectId(),
                    presents ? resolved.ticketSubjectSubtitle() : null,
                    presents ? resolved.ticketSubjectUrl() : null,
                    presents ? resolved.ticketSubjectColor() : null,
                    presents ? resolved.ticketSubjectIcon() : null,
                    !presents));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<SerializedTicketSubjectDto> serializeForTicket(Ticket ticket) {
        return serializeLinks(list(ticket));
    }

    @Transactional(readOnly = true)
    public List<SerializedTicketSubjectDto> serializeForTicketId(Long ticketId) {
        return serializeLinks(listByTicketId(ticketId));
    }

    public record SyncItem(String subjectType, Object subjectId, String role) {
        public SyncItem {
            Objects.requireNonNull(subjectType, "subjectType");
            Objects.requireNonNull(subjectId, "subjectId");
        }
    }
}
