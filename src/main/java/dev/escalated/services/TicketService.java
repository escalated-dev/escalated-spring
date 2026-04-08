package dev.escalated.services;

import dev.escalated.events.TicketEvent;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Reply;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketActivity;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketActivityRepository;
import dev.escalated.repositories.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ReplyRepository replyRepository;
    private final TagRepository tagRepository;
    private final TicketActivityRepository activityRepository;
    private final AgentProfileRepository agentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SlaService slaService;
    private final AuditLogService auditLogService;

    public TicketService(TicketRepository ticketRepository,
                         ReplyRepository replyRepository,
                         TagRepository tagRepository,
                         TicketActivityRepository activityRepository,
                         AgentProfileRepository agentRepository,
                         ApplicationEventPublisher eventPublisher,
                         SlaService slaService,
                         AuditLogService auditLogService) {
        this.ticketRepository = ticketRepository;
        this.replyRepository = replyRepository;
        this.tagRepository = tagRepository;
        this.activityRepository = activityRepository;
        this.agentRepository = agentRepository;
        this.eventPublisher = eventPublisher;
        this.slaService = slaService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Ticket findById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + id));
    }

    @Transactional(readOnly = true)
    public Ticket findByTicketNumber(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + ticketNumber));
    }

    @Transactional(readOnly = true)
    public Page<Ticket> findAll(Pageable pageable) {
        return ticketRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> findByStatus(TicketStatus status, Pageable pageable) {
        return ticketRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> findByAssignedAgent(Long agentId, Pageable pageable) {
        return ticketRepository.findByAssignedAgentId(agentId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> findByRequesterEmail(String email, Pageable pageable) {
        return ticketRepository.findByRequesterEmail(email, pageable);
    }

    @Transactional
    public Ticket create(String subject, String body, String requesterName, String requesterEmail,
                         TicketPriority priority, Long departmentId) {
        Ticket ticket = new Ticket();
        ticket.setSubject(subject);
        ticket.setBody(body);
        ticket.setRequesterName(requesterName);
        ticket.setRequesterEmail(requesterEmail);
        ticket.setPriority(priority != null ? priority : TicketPriority.MEDIUM);
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setGuestAccessToken(UUID.randomUUID().toString());

        if (departmentId != null) {
            ticket.setDepartment(new dev.escalated.models.Department());
            ticket.getDepartment().setId(departmentId);
        }

        slaService.applySlaPolicy(ticket);

        Ticket saved = ticketRepository.save(ticket);

        logActivity(saved, "created", "Ticket created", requesterEmail, null, null);
        auditLogService.log("create", "Ticket", saved.getId(), requesterEmail, null, null);
        eventPublisher.publishEvent(new TicketEvent(this, saved, TicketEvent.Type.CREATED, requesterEmail));

        return saved;
    }

    @Transactional
    public Ticket update(Long id, String subject, String body, TicketPriority priority) {
        Ticket ticket = findById(id);
        if (subject != null) {
            ticket.setSubject(subject);
        }
        if (body != null) {
            ticket.setBody(body);
        }
        if (priority != null && priority != ticket.getPriority()) {
            String oldPriority = ticket.getPriority().name();
            ticket.setPriority(priority);
            logActivity(ticket, "priority_changed", "Priority changed", null, oldPriority, priority.name());
            eventPublisher.publishEvent(
                    new TicketEvent(this, ticket, TicketEvent.Type.PRIORITY_CHANGED, null, oldPriority, priority.name()));
        }

        Ticket saved = ticketRepository.save(ticket);
        eventPublisher.publishEvent(new TicketEvent(this, saved, TicketEvent.Type.UPDATED, null));
        return saved;
    }

    @Transactional
    public Ticket changeStatus(Long id, TicketStatus newStatus, String actorEmail) {
        Ticket ticket = findById(id);
        TicketStatus oldStatus = ticket.getStatus();

        if (oldStatus == newStatus) {
            return ticket;
        }

        ticket.setStatus(newStatus);

        if (newStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(Instant.now());
        } else if (newStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(Instant.now());
        }

        logActivity(ticket, "status_changed", "Status changed", actorEmail, oldStatus.name(), newStatus.name());
        Ticket saved = ticketRepository.save(ticket);

        TicketEvent.Type eventType = switch (newStatus) {
            case CLOSED -> TicketEvent.Type.CLOSED;
            case RESOLVED -> TicketEvent.Type.RESOLVED;
            case OPEN -> oldStatus == TicketStatus.CLOSED || oldStatus == TicketStatus.RESOLVED
                    ? TicketEvent.Type.REOPENED : TicketEvent.Type.STATUS_CHANGED;
            default -> TicketEvent.Type.STATUS_CHANGED;
        };
        eventPublisher.publishEvent(new TicketEvent(this, saved, eventType, actorEmail, oldStatus.name(), newStatus.name()));
        return saved;
    }

    @Transactional
    public Ticket assign(Long ticketId, Long agentId, String actorEmail) {
        Ticket ticket = findById(ticketId);
        String oldAgent = ticket.getAssignedAgent() != null ? ticket.getAssignedAgent().getName() : "unassigned";

        if (agentId != null) {
            AgentProfile agent = agentRepository.findById(agentId)
                    .orElseThrow(() -> new EntityNotFoundException("Agent not found: " + agentId));
            ticket.setAssignedAgent(agent);
            logActivity(ticket, "assigned", "Ticket assigned to " + agent.getName(), actorEmail, oldAgent, agent.getName());
        } else {
            ticket.setAssignedAgent(null);
            logActivity(ticket, "unassigned", "Ticket unassigned", actorEmail, oldAgent, "unassigned");
        }

        Ticket saved = ticketRepository.save(ticket);
        eventPublisher.publishEvent(new TicketEvent(this, saved, TicketEvent.Type.ASSIGNED, actorEmail));
        return saved;
    }

    @Transactional
    public Reply addReply(Long ticketId, String body, String authorName, String authorEmail,
                          String authorType, boolean internal) {
        Ticket ticket = findById(ticketId);

        Reply reply = new Reply();
        reply.setTicket(ticket);
        reply.setBody(body);
        reply.setAuthorName(authorName);
        reply.setAuthorEmail(authorEmail);
        reply.setAuthorType(authorType);
        reply.setInternal(internal);
        reply.setEmailMessageId("<" + UUID.randomUUID() + "@escalated>");

        Reply saved = replyRepository.save(reply);

        if ("agent".equals(authorType) && !internal && ticket.getFirstRespondedAt() == null) {
            ticket.setFirstRespondedAt(Instant.now());
            ticketRepository.save(ticket);
        }

        logActivity(ticket, "reply_added", (internal ? "Internal note" : "Reply") + " by " + authorName, authorEmail, null, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Reply> getReplies(Long ticketId) {
        return replyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional
    public Ticket addTag(Long ticketId, String tagName) {
        Ticket ticket = findById(ticketId);
        Tag tag = tagRepository.findByName(tagName).orElseGet(() -> {
            Tag newTag = new Tag();
            newTag.setName(tagName);
            return tagRepository.save(newTag);
        });
        ticket.getTags().add(tag);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket removeTag(Long ticketId, Long tagId) {
        Ticket ticket = findById(ticketId);
        ticket.getTags().removeIf(t -> t.getId().equals(tagId));
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket snooze(Long ticketId, Instant until, String actorEmail) {
        Ticket ticket = findById(ticketId);
        ticket.setStatus(TicketStatus.SNOOZED);
        ticket.setSnoozedUntil(until);

        logActivity(ticket, "snoozed", "Ticket snoozed until " + until, actorEmail, null, null);
        Ticket saved = ticketRepository.save(ticket);
        eventPublisher.publishEvent(new TicketEvent(this, saved, TicketEvent.Type.SNOOZED, actorEmail));
        return saved;
    }

    @Transactional
    public void wakeUpSnoozedTickets() {
        List<Ticket> snoozed = ticketRepository.findSnoozedTicketsDue(Instant.now());
        for (Ticket ticket : snoozed) {
            ticket.setStatus(TicketStatus.OPEN);
            ticket.setSnoozedUntil(null);
            ticketRepository.save(ticket);
            logActivity(ticket, "unsnoozed", "Ticket automatically unsnoozed", "system", null, null);
            eventPublisher.publishEvent(new TicketEvent(this, ticket, TicketEvent.Type.UNSNOOZED, "system"));
        }
    }

    @Transactional
    public Ticket merge(Long sourceTicketId, Long targetTicketId, String actorEmail) {
        Ticket source = findById(sourceTicketId);
        Ticket target = findById(targetTicketId);

        List<Reply> sourceReplies = replyRepository.findByTicketIdOrderByCreatedAtAsc(sourceTicketId);
        for (Reply reply : sourceReplies) {
            Reply merged = new Reply();
            merged.setTicket(target);
            merged.setBody("[Merged from " + source.getTicketNumber() + "] " + reply.getBody());
            merged.setAuthorName(reply.getAuthorName());
            merged.setAuthorEmail(reply.getAuthorEmail());
            merged.setAuthorType(reply.getAuthorType());
            merged.setInternal(reply.isInternal());
            replyRepository.save(merged);
        }

        source.setStatus(TicketStatus.MERGED);
        source.setMergedIntoTicketId(targetTicketId);
        ticketRepository.save(source);

        logActivity(source, "merged", "Ticket merged into " + target.getTicketNumber(), actorEmail, null, null);
        logActivity(target, "merge_target", "Ticket " + source.getTicketNumber() + " merged into this ticket", actorEmail, null, null);

        eventPublisher.publishEvent(new TicketEvent(this, source, TicketEvent.Type.MERGED, actorEmail));
        return target;
    }

    @Transactional
    public Ticket split(Long ticketId, String newSubject, String replyIds, String actorEmail) {
        Ticket original = findById(ticketId);

        Ticket newTicket = new Ticket();
        newTicket.setSubject(newSubject);
        newTicket.setBody("Split from ticket " + original.getTicketNumber());
        newTicket.setRequesterName(original.getRequesterName());
        newTicket.setRequesterEmail(original.getRequesterEmail());
        newTicket.setPriority(original.getPriority());
        newTicket.setTicketNumber(generateTicketNumber());
        newTicket.setGuestAccessToken(UUID.randomUUID().toString());
        newTicket.setDepartment(original.getDepartment());

        Ticket saved = ticketRepository.save(newTicket);

        if (replyIds != null && !replyIds.isEmpty()) {
            for (String replyIdStr : replyIds.split(",")) {
                Long replyId = Long.parseLong(replyIdStr.trim());
                replyRepository.findById(replyId).ifPresent(reply -> {
                    Reply splitReply = new Reply();
                    splitReply.setTicket(saved);
                    splitReply.setBody(reply.getBody());
                    splitReply.setAuthorName(reply.getAuthorName());
                    splitReply.setAuthorEmail(reply.getAuthorEmail());
                    splitReply.setAuthorType(reply.getAuthorType());
                    splitReply.setInternal(reply.isInternal());
                    replyRepository.save(splitReply);
                });
            }
        }

        logActivity(original, "split", "Ticket split into " + saved.getTicketNumber(), actorEmail, null, null);
        logActivity(saved, "split_from", "Split from ticket " + original.getTicketNumber(), actorEmail, null, null);

        eventPublisher.publishEvent(new TicketEvent(this, saved, TicketEvent.Type.SPLIT, actorEmail));
        return saved;
    }

    @Transactional
    public void delete(Long id, String actorEmail) {
        Ticket ticket = findById(id);
        auditLogService.log("delete", "Ticket", id, actorEmail, null, null);
        eventPublisher.publishEvent(new TicketEvent(this, ticket, TicketEvent.Type.DELETED, actorEmail));
        ticketRepository.delete(ticket);
    }

    @Transactional(readOnly = true)
    public Ticket findByGuestToken(String token) {
        return ticketRepository.findByGuestAccessToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found for guest token"));
    }

    private String generateTicketNumber() {
        return "ESC-" + String.format("%06d", ticketRepository.count() + 1);
    }

    private void logActivity(Ticket ticket, String type, String description,
                             String actorEmail, String oldValue, String newValue) {
        TicketActivity activity = new TicketActivity();
        activity.setTicket(ticket);
        activity.setType(type);
        activity.setDescription(description);
        activity.setActorEmail(actorEmail);
        activity.setOldValue(oldValue);
        activity.setNewValue(newValue);
        activityRepository.save(activity);
    }
}
