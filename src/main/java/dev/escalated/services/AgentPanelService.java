package dev.escalated.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.events.ChatEvent;
import dev.escalated.events.ReplyEvent;
import dev.escalated.events.TicketEvent;
import dev.escalated.models.AgentNotification;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.CustomerPresence;
import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketFollower;
import dev.escalated.models.TicketRead;
import dev.escalated.repositories.AgentNotificationRepository;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.CustomerPresenceRepository;
import dev.escalated.repositories.KnowledgeBaseArticleRepository;
import dev.escalated.repositories.TicketFollowerRepository;
import dev.escalated.repositories.TicketReadRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.security.TicketAccess;
import jakarta.persistence.criteria.Subquery;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The agent panel's data (escalated-developer-context ADR 2026-11-05-agent-panel-data, after
 * escalated-laravel #292-#296): each agent's own status, per-agent unread markers, customer
 * presence (shown on agent pages only) and the agents' in-app notifications.
 *
 * <p>It listens to the ticket, reply and chat events. Nothing here fails the operation that
 * triggered it: event handling logs and swallows its own errors.
 */
@Service
public class AgentPanelService {

    /** The statuses an agent may set. */
    public static final List<String> STATUSES = List.of("online", "away", "offline");

    /** The private STOMP destination each agent's notifications are sent to (under /user). */
    public static final String NOTIFICATION_DESTINATION = "/queue/escalated.notifications";

    private static final Logger log = LoggerFactory.getLogger(AgentPanelService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MentionService MENTIONS = new MentionService();
    private static final Duration PRESENCE_THROTTLE = Duration.ofSeconds(30);
    private static final Set<String> STAFF_AUTHORS = Set.of("agent", "admin", "system");
    private static final String UNAVAILABLE = "A ticket you can no longer view";
    private static final Map<String, String> TITLES = Map.of(
            "ticket_assigned", "%s was assigned to you",
            "ticket_reply", "New reply on %s",
            "mention", "%2$s mentioned you on %1$s",
            "sla_warning", "SLA due soon on %s",
            "sla_breached", "SLA breached on %s",
            "chat_transferred", "A chat was transferred to you (%s)",
            "generic", "Update on %s");

    private final TicketRepository tickets;
    private final TicketReadRepository reads;
    private final CustomerPresenceRepository presence;
    private final AgentNotificationRepository notifications;
    private final AgentProfileRepository agents;
    private final TicketFollowerRepository followers;
    private final KnowledgeBaseArticleRepository articles;
    private final EscalatedProperties properties;
    private final ObjectProvider<TicketAccess> access;
    private final ObjectProvider<SimpMessagingTemplate> messaging;
    private final ObjectProvider<PlatformTransactionManager> transactions;

    public AgentPanelService(
            TicketRepository tickets,
            TicketReadRepository reads,
            CustomerPresenceRepository presence,
            AgentNotificationRepository notifications,
            AgentProfileRepository agents,
            TicketFollowerRepository followers,
            KnowledgeBaseArticleRepository articles,
            EscalatedProperties properties,
            ObjectProvider<TicketAccess> access,
            ObjectProvider<SimpMessagingTemplate> messaging,
            @Qualifier(EscalatedTransactionManagers.ESCALATED) ObjectProvider<PlatformTransactionManager> transactions) {
        this.tickets = tickets;
        this.reads = reads;
        this.presence = presence;
        this.notifications = notifications;
        this.agents = agents;
        this.followers = followers;
        this.articles = articles;
        this.properties = properties;
        this.access = access;
        this.messaging = messaging;
        this.transactions = transactions;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // ---- Agent status -------------------------------------------------------------------------

    /** The agent's status, {@code offline} until they set one. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public String status(Long agentId) {
        if (agentId == null) {
            return "offline";
        }
        return agents.findById(agentId)
                .map(AgentProfile::getChatStatus)
                .filter(STATUSES::contains)
                .orElse("offline");
    }

    /**
     * Stores the agent's status on their profile. Live-chat routing reads {@code is_available}, so
     * only {@code online} leaves the agent available for new chats.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void setStatus(Long agentId, String status) {
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unknown status " + status);
        }
        AgentProfile agent = agents.findById(agentId).orElseThrow();
        agent.setChatStatus(status);
        agent.setAvailable("online".equals(status));
        agents.save(agent);
    }

    // ---- Unread markers --------------------------------------------------------------------------

    /** Marks the ticket read for the agent. The caller found the ticket through the agent's scope. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void markRead(Long agentId, Long ticketId) {
        mark(agentId, ticketId, now());
    }

    /** Marks the ticket unread for the agent until they open it again. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void markUnread(Long agentId, Long ticketId) {
        mark(agentId, ticketId, null);
    }

    private void mark(Long agentId, Long ticketId, Instant at) {
        if (agentId == null || ticketId == null) {
            return;
        }
        if (reads.mark(agentId, ticketId, at, now()) == 0) {
            try {
                reads.saveAndFlush(new TicketRead(ticketId, agentId, at));
            } catch (DataIntegrityViolationException raced) {
                reads.mark(agentId, ticketId, at, now());
            }
        }
    }

    /**
     * The tickets unread for the agent: marked unread, or with customer activity after the agent
     * last read them. The inbox's {@code unread} counts add it to the list's own filters.
     */
    public static Specification<Ticket> unreadFor(Long agentId) {
        return (root, query, cb) -> {
            Subquery<Long> markedUnread = query.subquery(Long.class);
            var mark = markedUnread.from(TicketRead.class);
            markedUnread.select(mark.get("id")).where(
                    cb.equal(mark.get("ticketId"), root.get("id")),
                    cb.equal(mark.get("agentId"), agentId),
                    cb.isNull(mark.get("lastReadAt")));

            Subquery<Long> readSince = query.subquery(Long.class);
            var read = readSince.from(TicketRead.class);
            readSince.select(read.get("id")).where(
                    cb.equal(read.get("ticketId"), root.get("id")),
                    cb.equal(read.get("agentId"), agentId),
                    cb.greaterThanOrEqualTo(read.<Instant>get("lastReadAt"), root.<Instant>get("lastCustomerActivityAt")));

            return cb.or(
                    cb.exists(markedUnread),
                    cb.and(cb.isNotNull(root.get("lastCustomerActivityAt")), cb.not(cb.exists(readSince))));
        };
    }

    /**
     * {@code is_unread}, {@code requester_online} and {@code requester_last_seen_at} for each ticket,
     * keyed by ticket id: one query for the tickets, one for the agent's markers and one for presence,
     * whatever the page size. The tickets are read through the current visibility scope.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<Long, Map<String, Object>> rowFields(Long agentId, Collection<Long> ticketIds) {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        if (ticketIds == null || ticketIds.isEmpty()) {
            return out;
        }
        List<Ticket> rows = tickets.findAllById(ticketIds);
        Map<Long, TicketRead> marks = new HashMap<>();
        if (agentId != null) {
            reads.findByAgentIdAndTicketIdIn(agentId, ticketIds).forEach(read -> marks.put(read.getTicketId(), read));
        }
        Set<String> emails = new LinkedHashSet<>();
        rows.forEach(ticket -> {
            if (ticket.getRequesterEmail() != null) {
                emails.add(ticket.getRequesterEmail().toLowerCase(Locale.ROOT));
            }
        });
        Map<String, Instant> seen = new HashMap<>();
        if (!emails.isEmpty()) {
            presence.findByEmailIn(emails).forEach(p -> seen.put(p.getEmail(), p.getLastSeenAt()));
        }
        Instant onlineAfter = Instant.now().minusSeconds(properties.getUi().getPresenceOnlineWindowSeconds());
        for (Ticket ticket : rows) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("is_unread", unread(marks.get(ticket.getId()), ticket.getLastCustomerActivityAt()));
            Instant lastSeen = ticket.getRequesterEmail() == null ? null
                    : seen.get(ticket.getRequesterEmail().toLowerCase(Locale.ROOT));
            fields.put("requester_online", lastSeen != null && !lastSeen.isBefore(onlineAfter));
            fields.put("requester_last_seen_at", lastSeen == null ? null : lastSeen.toString());
            out.put(ticket.getId(), fields);
        }
        return out;
    }

    private static boolean unread(TicketRead mark, Instant activity) {
        if (mark != null && mark.getLastReadAt() == null) {
            return true;
        }
        if (activity == null) {
            return false;
        }
        return mark == null || mark.getLastReadAt().isBefore(activity);
    }

    /**
     * Whether a reply came from the customer side: a public reply by the requester, a guest, a chat
     * visitor or an inbound email. Agents' replies, internal notes and system replies are not.
     */
    public static boolean customerSide(Reply reply) {
        return reply != null && !reply.isInternal()
                && (reply.getAuthorType() == null || !STAFF_AUTHORS.contains(reply.getAuthorType().toLowerCase(Locale.ROOT)));
    }

    // ---- Customer presence ---------------------------------------------------------------------

    /** Records that the customer with this email is on the site, at most every 30 seconds. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void see(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String key = email.trim().toLowerCase(Locale.ROOT);
        Instant now = now();
        if (presence.touch(key, now, now.minus(PRESENCE_THROTTLE)) > 0 || presence.existsByEmail(key)) {
            return;
        }
        presence.saveAndFlush(new CustomerPresence(key, now));
    }

    /**
     * {@link #see} in a transaction of its own, logging and swallowing any error: presence never fails
     * a request.
     */
    public void quietlySee(String email) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactions.getObject());
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> see(email));
        } catch (RuntimeException failed) {
            log.debug("Escalated could not record customer presence: {}", failed.getMessage());
        }
    }

    // ---- Events ------------------------------------------------------------------------------------

    /** A new ticket (not an import) is customer activity; an assignment and SLA events notify. */
    @EventListener
    public void onTicket(TicketEvent event) {
        try {
            Ticket ticket = event.getTicket();
            switch (event.getType()) {
                case CREATED -> {
                    if (!ImportContext.isImporting() && ticket != null && ticket.getId() != null) {
                        activity(ticket.getId(), ticket.getCreatedAt() == null ? Instant.now() : ticket.getCreatedAt());
                    }
                }
                case ASSIGNED -> {
                    if (ticket != null && ticket.getAssignedAgent() != null) {
                        notify(ticket.getAssignedAgent().getId(), "ticket_assigned", ticket, Map.of(), event.getActorEmail());
                    }
                }
                case SLA_WARNING, SLA_BREACHED -> {
                    if (ticket != null && ticket.getAssignedAgent() != null) {
                        String type = event.getType() == TicketEvent.Type.SLA_WARNING ? "sla_warning" : "sla_breached";
                        notify(ticket.getAssignedAgent().getId(), type, ticket, Map.of(), null);
                    }
                }
                default -> {
                    // Other ticket events change no panel data.
                }
            }
        } catch (RuntimeException failed) {
            log.warn("Escalated agent panel could not handle {}: {}", event.getType(), failed.getMessage());
        }
    }

    /** A public reply from the customer side is customer activity, and notifies the assignee and followers. */
    @EventListener
    public void onReply(ReplyEvent event) {
        try {
            Reply reply = event.getReply();
            if (event.getType() != ReplyEvent.Type.CREATED || reply == null || reply.getTicket() == null) {
                return;
            }
            if (!customerSide(reply)) {
                mentions(reply);
                return;
            }
            Ticket ticket = reply.getTicket();
            activity(ticket.getId(), reply.getCreatedAt() == null ? Instant.now() : reply.getCreatedAt());

            Set<Long> recipients = new LinkedHashSet<>();
            if (ticket.getAssignedAgent() != null) {
                recipients.add(ticket.getAssignedAgent().getId());
            }
            for (TicketFollower follower : followers.findByTicketId(ticket.getId())) {
                Long id = parseLong(follower.getUserId());
                if (id != null) {
                    recipients.add(id);
                }
            }
            for (Long agentId : recipients) {
                notify(agentId, "ticket_reply", ticket, Map.of("reply_id", reply.getId()), reply.getAuthorEmail());
            }
        } catch (RuntimeException failed) {
            log.warn("Escalated agent panel could not handle a reply: {}", failed.getMessage());
        }
    }

    /**
     * Mentions of agents in an agent's reply or internal note, as escalated-laravel's
     * {@code MentionService}: each mentioned agent who may see the ticket gets a {@code mention}
     * notification. A system reply mentions nobody, and nobody is notified of their own mention.
     */
    private void mentions(Reply reply) {
        String authorType = reply.getAuthorType() == null ? "" : reply.getAuthorType().toLowerCase(Locale.ROOT);
        if (!Set.of("agent", "admin").contains(authorType) || reply.getBody() == null) {
            return;
        }
        List<AgentProfile> staff = mentionableAgents();
        List<AgentProfile> mentioned = MENTIONS.mentionedAgents(reply.getBody(), staff);
        if (mentioned.isEmpty()) {
            return;
        }
        Ticket ticket = reply.getTicket();
        TicketAccess scope = access.getIfAvailable();
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("reply_id", reply.getId());
        extra.put("mentioned_by", reply.getAuthorName() == null ? "Someone" : reply.getAuthorName());
        for (AgentProfile agent : mentioned) {
            if (scope != null && scope.enabled() && !scope.canReceiveStaff(ticket.getId(), agent.getId())) {
                continue;
            }
            notify(agent.getId(), "mention", ticket, extra, reply.getAuthorEmail());
        }
    }

    /** The staff a mention can name: active agents and admins. */
    public List<AgentProfile> mentionableAgents() {
        return agents.findByActiveTrueOrderByName().stream()
                .filter(agent -> agent.isAgent() || agent.isAdmin())
                .toList();
    }

    /** The composer's mention picker: up to 10 agents whose name or email contains {@code q}. */
    public List<Map<String, Object>> mentionSuggestions(String q) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentProfile agent : MENTIONS.suggestions(q, mentionableAgents(), MentionService.SUGGESTIONS_LIMIT)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", agent.getId());
            row.put("name", agent.getName());
            row.put("email", agent.getEmail());
            out.add(row);
        }
        return out;
    }

    /** A chat transferred to an agent notifies them. */
    @EventListener
    public void onChat(ChatEvent event) {
        try {
            if (event.getType() != ChatEvent.Type.TRANSFERRED || event.getTransfer() == null
                    || event.getTransfer().toAgentId() == null) {
                return;
            }
            Ticket ticket = tickets.findById(event.getTicketId()).orElse(null);
            if (ticket == null) {
                return;
            }
            String from = event.getTransfer().fromAgentId() == null ? null
                    : agents.findById(event.getTransfer().fromAgentId()).map(AgentProfile::getEmail).orElse(null);
            Map<String, Object> extra = new HashMap<>();
            extra.put("session_id", event.getSessionId());
            notify(event.getTransfer().toAgentId(), "chat_transferred", ticket, extra, from);
        } catch (RuntimeException failed) {
            log.warn("Escalated agent panel could not handle a chat transfer: {}", failed.getMessage());
        }
    }

    private void activity(Long ticketId, Instant at) {
        tickets.findById(ticketId).ifPresent(ticket -> {
            ticket.setLastCustomerActivityAt(at.truncatedTo(ChronoUnit.SECONDS));
            tickets.save(ticket);
        });
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException notAnId) {
            return null;
        }
    }

    // ---- Notifications -------------------------------------------------------------------------------

    /** Whether the in-app notifications feed is on ({@code escalated.ui.agent-notifications-enabled}). */
    public boolean notificationsEnabled() {
        return properties.getUi().isAgentNotificationsEnabled();
    }

    /**
     * Stores an in-app notification for the agent about the ticket and sends it as
     * {@code notification.created} to the agent's private queue. Nobody is notified of their own
     * action ({@code causerEmail}), and nothing is stored while notifications are off.
     */
    public void notify(Long agentId, String type, Ticket ticket, Map<String, Object> extra, String causerEmail) {
        if (!notificationsEnabled() || agentId == null || ticket == null) {
            return;
        }
        AgentProfile agent = agents.findById(agentId).orElse(null);
        if (agent == null || (causerEmail != null && causerEmail.equalsIgnoreCase(agent.getEmail()))) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticket_id", ticket.getId());
        data.put("reference", ticket.getTicketNumber());
        data.putAll(extra);
        AgentNotification notification;
        try {
            notification = notifications.save(new AgentNotification(UUID.randomUUID().toString(), agentId, type,
                    JSON.writeValueAsString(data), now()));
        } catch (JsonProcessingException impossible) {
            return;
        }
        SimpMessagingTemplate template = messaging.getIfAvailable();
        if (template == null) {
            return;
        }
        TicketAccess scope = access.getIfAvailable();
        boolean visible = scope == null || !scope.enabled() || scope.canReceiveStaff(ticket.getId(), agentId);
        Map<String, Object> item = item(notification, data, visible ? ticket : null, true);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("event", "notification.created");
        message.put("data", item);
        template.convertAndSendToUser(agent.getEmail(), NOTIFICATION_DESTINATION, message);
    }

    /** The agent's unread notification count. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public long unreadCount(Long agentId) {
        return agentId == null ? 0 : notifications.countByAgentIdAndReadAtIsNull(agentId);
    }

    /** A page of the agent's notifications, newest first: {@code {data, meta}}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> page(Long agentId, int page, int perPage, boolean unreadOnly) {
        PageRequest request = PageRequest.of(page - 1, perPage, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<AgentNotification> found = unreadOnly
                ? notifications.findByAgentIdAndReadAtIsNull(agentId, request)
                : notifications.findByAgentId(agentId, request);

        Map<Long, Ticket> visible = new HashMap<>();
        Set<Long> ids = new LinkedHashSet<>();
        Map<String, Map<String, Object>> data = new HashMap<>();
        for (AgentNotification n : found.getContent()) {
            Map<String, Object> parsed = parse(n.getData());
            data.put(n.getId(), parsed);
            Long id = ticketId(parsed);
            if (id != null) {
                ids.add(id);
            }
        }
        if (!ids.isEmpty()) {
            tickets.findAllById(ids).forEach(ticket -> visible.put(ticket.getId(), ticket));
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (AgentNotification n : found.getContent()) {
            Map<String, Object> parsed = data.get(n.getId());
            Long id = ticketId(parsed);
            items.add(item(n, parsed, id == null ? null : visible.get(id), id != null));
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("current_page", page);
        meta.put("last_page", Math.max(1, found.getTotalPages()));
        meta.put("per_page", perPage);
        meta.put("total", found.getTotalElements());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", items);
        body.put("meta", meta);
        return body;
    }

    /** Marks one of the agent's notifications read; false when the agent has none with that id. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public boolean read(Long agentId, String id) {
        if (notifications.countMine(agentId, id) == 0) {
            return false;
        }
        notifications.read(agentId, id, now());
        return true;
    }

    /** Marks every notification of the agent's read. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void readAll(Long agentId) {
        notifications.readAll(agentId, now());
    }

    private Map<String, Object> item(AgentNotification n, Map<String, Object> data, Ticket ticket, boolean hasTicket) {
        String type = TITLES.containsKey(n.getType()) ? n.getType() : "generic";
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", n.getId());
        item.put("type", type);
        if (hasTicket && ticket == null) {
            item.put("title", UNAVAILABLE);
            item.put("body", null);
            item.put("ticket", null);
            item.put("url", null);
        } else {
            String reference = ticket != null ? ticket.getTicketNumber() : String.valueOf(data.getOrDefault("reference", ""));
            item.put("title", String.format(TITLES.get(type), reference,
                    String.valueOf(data.getOrDefault("mentioned_by", "Someone"))));
            item.put("body", ticket == null ? null : ticket.getSubject());
            if (ticket == null) {
                item.put("ticket", null);
                item.put("url", null);
            } else {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", ticket.getId());
                summary.put("reference", ticket.getTicketNumber());
                summary.put("subject", ticket.getSubject());
                item.put("ticket", summary);
                item.put("url", dev.escalated.ui.UiRoutes.PREFIX + "/agent/tickets/" + ticket.getTicketNumber());
            }
        }
        item.put("created_at", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
        item.put("read_at", n.getReadAt() == null ? null : n.getReadAt().toString());
        return item;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        try {
            Object parsed = JSON.readValue(json, Object.class);
            return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (JsonProcessingException | IllegalArgumentException malformed) {
            return Map.of();
        }
    }

    private static Long ticketId(Map<String, Object> data) {
        Object id = data.get("ticket_id");
        return id instanceof Number number ? number.longValue() : null;
    }

    // ---- Knowledge base search -------------------------------------------------------------------------

    /**
     * The agents' knowledge-base search: published articles whose title or body contains the term
     * (case-insensitive, {@code %} and {@code _} taken literally), title matches first, then the most
     * recently updated.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<KnowledgeBaseArticle> searchArticles(String term, int limit) {
        return articles.searchForAgents(likePattern(term), PageRequest.of(0, limit));
    }

    /** {@code %term%}, lower-cased, with {@code !}, {@code %} and {@code _} escaped by {@code !}. */
    public static String likePattern(String term) {
        String lower = term == null ? "" : term.toLowerCase(Locale.ROOT);
        return "%" + lower.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
