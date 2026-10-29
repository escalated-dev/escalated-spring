package dev.escalated.plugins;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.ChatSession;
import dev.escalated.models.Contact;
import dev.escalated.models.Department;
import dev.escalated.models.Reply;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ChatSessionRepository;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.security.AuthorizedTicketBroadcaster;
import dev.escalated.security.TicketAccess;
import dev.escalated.services.TicketService;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What {@code ctx.tickets}, {@code ctx.replies}, {@code ctx.contacts}, {@code ctx.tags},
 * {@code ctx.departments}, {@code ctx.agents} and {@code ctx.broadcast} reach. Writes go through
 * {@link TicketService}, so they fire the same events, webhooks, workflows and emails as anything
 * else. Plugins are host code: ticket visibility rules do not narrow them.
 */
final class PluginDomain {

    /** Caps {@code ctx.tickets.query} and {@code ctx.replies.query}. */
    static final int MAX_QUERY_ROWS = 500;

    private static final Set<String> TICKET_FILTERS = Set.of("id", "reference", "status", "priority", "channel",
            "ticket_type", "department_id", "requester_id", "contact_id", "assigned_to");
    private static final List<String> TICKET_WRITES = List.of("subject", "title", "body", "description", "priority",
            "status", "department_id", "ticket_type", "metadata");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern CHANNEL = Pattern.compile("^[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$");

    private final TicketService tickets;
    private final TicketRepository ticketRows;
    private final ReplyRepository replies;
    private final ContactRepository contacts;
    private final TagRepository tags;
    private final DepartmentRepository departments;
    private final AgentProfileRepository agents;
    private final ChatSessionRepository chats;
    private final ObjectProvider<TicketAccess> access;
    private final ObjectProvider<SimpMessagingTemplate> messaging;
    private final ObjectProvider<AuthorizedTicketBroadcaster> authorizedBroadcaster;
    private final TransactionTemplate readOnly;

    @SuppressWarnings("checkstyle:ParameterNumber")
    PluginDomain(
            TicketService tickets,
            TicketRepository ticketRows,
            ReplyRepository replies,
            ContactRepository contacts,
            TagRepository tags,
            DepartmentRepository departments,
            AgentProfileRepository agents,
            ChatSessionRepository chats,
            ObjectProvider<TicketAccess> access,
            ObjectProvider<SimpMessagingTemplate> messaging,
            ObjectProvider<AuthorizedTicketBroadcaster> authorizedBroadcaster,
            TransactionTemplate readOnly) {
        this.tickets = tickets;
        this.ticketRows = ticketRows;
        this.replies = replies;
        this.contacts = contacts;
        this.tags = tags;
        this.departments = departments;
        this.agents = agents;
        this.chats = chats;
        this.access = access;
        this.messaging = messaging;
        this.authorizedBroadcaster = authorizedBroadcaster;
        this.readOnly = readOnly;
    }

    /** Runs as the host: ticket visibility does not narrow plugin code. */
    private <T> T unscoped(Supplier<T> work) {
        TicketAccess ticketAccess = access.getIfAvailable();
        return ticketAccess == null ? work.get() : ticketAccess.withoutRestriction(work);
    }

    private <T> T read(Supplier<T> work) {
        return unscoped(() -> readOnly.execute(status -> work.get()));
    }

    // ---- serialisation: what plugins receive

    static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    static String lower(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    static String tagSlug(String name) {
        String slug = NON_SLUG.matcher(name == null ? "" : name.toLowerCase(Locale.ROOT)).replaceAll("-");
        return slug.replaceAll("^-+|-+$", "");
    }

    static Map<String, Object> tagObject(Tag tag) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", tag.getId());
        out.put("name", tag.getName());
        out.put("slug", tagSlug(tag.getName()));
        out.put("color", tag.getColor());
        return out;
    }

    /**
     * A ticket as plugins see it: snake_case, status and priority as lower-case slugs, and
     * {@code title} beside {@code subject} (the SDK's Ticket reads title). Call inside a transaction.
     */
    static Map<String, Object> ticketObject(Ticket ticket) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", ticket.getId());
        out.put("reference", ticket.getTicketNumber());
        out.put("subject", ticket.getSubject());
        out.put("title", ticket.getSubject());
        out.put("description", ticket.getBody());
        out.put("status", lower(ticket.getStatus()));
        out.put("custom_status", ticket.getCustomStatus() == null ? null : ticket.getCustomStatus().getSlug());
        out.put("priority", lower(ticket.getPriority()));
        out.put("ticket_type", ticket.getTicketType());
        out.put("channel", ticket.getChannel());
        out.put("department_id", ticket.getDepartment() == null ? null : ticket.getDepartment().getId());
        out.put("assigned_to", ticket.getAssignedAgent() == null ? null : ticket.getAssignedAgent().getId());
        Contact contact = ticket.getContact();
        out.put("requester_id", contact == null ? null : contact.getUserId());
        out.put("contact_id", contact == null ? null : contact.getId());
        out.put("requester_name", ticket.getRequesterName());
        out.put("metadata", ticket.getMetadata() == null ? new LinkedHashMap<>() : ticket.getMetadata());
        List<Map<String, Object>> tagList = new ArrayList<>();
        ticket.getTags().stream()
                .sorted(java.util.Comparator.comparing(Tag::getId, java.util.Comparator.nullsLast(Long::compare)))
                .forEach(tag -> tagList.add(tagObject(tag)));
        out.put("tags", tagList);
        out.put("created_at", iso(ticket.getCreatedAt()));
        out.put("updated_at", iso(ticket.getUpdatedAt()));
        return PluginJson.plainRecord(out);
    }

    /** A reply as plugins see it. Call inside a transaction. */
    static Map<String, Object> replyObject(Reply reply) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", reply.getId());
        out.put("ticket_id", reply.getTicket() == null ? null : reply.getTicket().getId());
        out.put("body", reply.getBody());
        out.put("type", reply.isInternal() ? "note" : "reply");
        out.put("is_internal", reply.isInternal());
        out.put("author_type", reply.getAuthorType());
        out.put("author_name", reply.getAuthorName());
        out.put("created_at", iso(reply.getCreatedAt()));
        out.put("updated_at", iso(reply.getUpdatedAt()));
        return PluginJson.plainRecord(out);
    }

    static Map<String, Object> contactObject(Contact contact) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", contact.getId());
        out.put("email", contact.getEmail());
        out.put("name", contact.getName());
        out.put("user_id", contact.getUserId());
        out.put("created_at", iso(contact.getCreatedAt()));
        out.put("updated_at", iso(contact.getUpdatedAt()));
        return out;
    }

    static Map<String, Object> departmentObject(Department department) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", department.getId());
        out.put("name", department.getName());
        out.put("slug", tagSlug(department.getName()));
        out.put("description", department.getDescription());
        out.put("is_active", department.isActive());
        return out;
    }

    static Map<String, Object> agentObject(AgentProfile agent) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", agent.getId());
        out.put("name", agent.getName());
        out.put("email", agent.getEmail());
        return out;
    }

    // ---- reads for the event payloads

    Map<String, Object> ticketById(Long id) {
        return read(() -> ticketRows.findById(id).map(PluginDomain::ticketObject).orElse(null));
    }

    Map<String, Object> replyWithTicket(Long replyId) {
        return read(() -> replies.findById(replyId).map(reply -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("reply", replyObject(reply));
            out.put("ticket", ticketObject(reply.getTicket()));
            return out;
        }).orElse(null));
    }

    Map<String, Object> tagByName(String name) {
        return name == null ? null : read(() -> tags.findByName(name).map(PluginDomain::tagObject).orElse(null));
    }

    /** A chat session for {@code chat.*} events: ids and status, never the visitor's token or email. */
    Map<String, Object> chatSession(Long sessionId, Long ticketId, Long agentId) {
        return read(() -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("session_id", sessionId);
            out.put("ticket_id", ticketId);
            ChatSession session = sessionId == null ? null : chats.findById(sessionId).orElse(null);
            out.put("status", session == null ? null : session.getStatus());
            out.put("agent_id", session != null && session.getAgentId() != null ? session.getAgentId() : agentId);
            out.put("department_id", session == null ? null : session.getDepartmentId());
            return out;
        });
    }

    // ---- acting user

    static boolean signedIn(Authentication actor) {
        return actor != null && actor.isAuthenticated() && !(actor instanceof AnonymousAuthenticationToken);
    }

    private static String actorEmail(Authentication actor) {
        return signedIn(actor) ? actor.getName() : null;
    }

    /** {@code ctx.currentUser()}: the user of the request being served, or null. */
    Map<String, Object> currentUser(Authentication actor) {
        if (!signedIn(actor)) {
            return null;
        }
        AgentProfile profile = agents.findByEmail(actor.getName()).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", profile == null ? actor.getName() : profile.getId());
        out.put("name", profile != null && profile.getName() != null ? profile.getName() : actor.getName());
        out.put("email", profile != null && profile.getEmail() != null ? profile.getEmail() : actor.getName());
        return out;
    }

    // ---- ctx.tickets

    private static void checkKeys(String method, Map<String, Object> data, List<String> allowed) {
        Set<String> bad = new TreeSet<>(data.keySet());
        bad.removeAll(allowed);
        if (!bad.isEmpty()) {
            throw new IllegalArgumentException(method + " does not accept " + String.join(", ", bad));
        }
    }

    private static String text(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                if (!(value instanceof String string)) {
                    throw new IllegalArgumentException(key + " must be a string");
                }
                return string;
            }
        }
        return null;
    }

    private static TicketPriority priority(Object value) {
        try {
            return TicketPriority.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown priority " + value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("metadata must be an object");
        }
        return new LinkedHashMap<>((Map<String, Object>) PluginJson.plain(value));
    }

    private static String ticketType(Map<String, Object> data) {
        String type = text(data, "ticket_type");
        if (type != null && (type.isBlank() || type.length() > 100)) {
            throw new IllegalArgumentException("ticket_type is a name of 1 to 100 characters");
        }
        return type;
    }

    private Long department(Object value) {
        Long id = PluginJson.id(value);
        if (id == null) {
            throw new IllegalArgumentException("department_id must be a number");
        }
        if (!departments.existsById(id)) {
            throw new IllegalArgumentException("no department " + id);
        }
        return id;
    }

    Map<String, Object> findTicket(Object id) {
        return read(() -> {
            Long number = PluginJson.id(id);
            Ticket ticket = number != null
                    ? ticketRows.findById(number).orElse(null)
                    : id instanceof String reference && !reference.isBlank()
                            ? ticketRows.findByTicketNumber(reference).orElse(null)
                            : null;
            return ticket == null ? null : ticketObject(ticket);
        });
    }

    /** Up to 500 tickets matching every key of {@code filter}; any key outside the contract's is an error. */
    List<Map<String, Object>> queryTickets(Map<String, Object> filter) {
        Map<String, Object> conditions = filter == null ? Map.of() : filter;
        for (String key : conditions.keySet()) {
            if (!TICKET_FILTERS.contains(key)) {
                throw new IllegalArgumentException("ctx.tickets.query cannot filter on \"" + key + "\"");
            }
        }
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            for (Map.Entry<String, Object> condition : conditions.entrySet()) {
                Object value = condition.getValue();
                switch (condition.getKey()) {
                    case "id" -> where.add(cb.equal(root.get("id"), number("id", value)));
                    case "reference" -> where.add(cb.equal(root.get("ticketNumber"), String.valueOf(value)));
                    case "status" -> where.add(cb.equal(root.get("status"), status(value)));
                    case "priority" -> where.add(cb.equal(root.get("priority"), priority(value)));
                    case "channel" -> where.add(cb.equal(root.get("channel"), String.valueOf(value)));
                    case "ticket_type" -> where.add(cb.equal(root.get("ticketType"), String.valueOf(value)));
                    case "department_id" -> where.add(cb.equal(root.get("department").get("id"),
                            number("department_id", value)));
                    case "contact_id" -> where.add(cb.equal(root.get("contact").get("id"), number("contact_id", value)));
                    case "assigned_to" -> where.add(cb.equal(root.get("assignedAgent").get("id"),
                            number("assigned_to", value)));
                    default -> where.add(cb.equal(root.get("contact").get("userId"), PluginJson.string(conditions,
                            "requester_id")));
                }
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
        return read(() -> ticketRows.findAll(spec, PageRequest.of(0, MAX_QUERY_ROWS, Sort.by("id"))).stream()
                .map(PluginDomain::ticketObject).toList());
    }

    private static Long number(String key, Object value) {
        Long id = PluginJson.id(value);
        if (id == null) {
            throw new IllegalArgumentException("ctx.tickets.query: " + key + " must be a number");
        }
        return id;
    }

    private static dev.escalated.models.TicketStatus status(Object value) {
        try {
            return dev.escalated.models.TicketStatus.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown status " + value);
        }
    }

    /**
     * Opens a ticket through the ticket service, so {@code ticket.created} fires. The requester is
     * {@code contact_id}, or {@code requester_email} (and {@code requester_name}), found or created.
     */
    Map<String, Object> createTicket(Map<String, Object> data, Authentication actor) {
        if (data == null) {
            throw new IllegalArgumentException("ctx.tickets.create requires an object");
        }
        List<String> allowed = new ArrayList<>(TICKET_WRITES);
        allowed.addAll(List.of("channel", "contact_id", "requester_email", "requester_name"));
        checkKeys("ctx.tickets.create", data, allowed);
        String subject = text(data, "subject", "title");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("ctx.tickets.create requires a subject");
        }
        String body = text(data, "description", "body");
        TicketPriority priority = data.get("priority") == null ? TicketPriority.MEDIUM : priority(data.get("priority"));
        String type = ticketType(data);
        Map<String, Object> meta = data.get("metadata") == null ? null : metadata(data.get("metadata"));
        String channel = text(data, "channel");
        if (channel == null || channel.isBlank()) {
            channel = "api";
        } else if (channel.length() > 30) {
            throw new IllegalArgumentException("channel is at most 30 characters");
        }
        String requesterEmail;
        String requesterName;
        if (data.get("contact_id") != null) {
            Long contactId = PluginJson.id(data.get("contact_id"));
            Contact contact = contactId == null ? null : contacts.findById(contactId).orElse(null);
            if (contact == null) {
                throw new IllegalArgumentException("no contact " + data.get("contact_id"));
            }
            requesterEmail = contact.getEmail();
            requesterName = contact.getName();
        } else {
            requesterEmail = text(data, "requester_email");
            requesterName = text(data, "requester_name");
            if (requesterEmail == null || !EMAIL.matcher(Contact.normalizeEmail(requesterEmail)).matches()) {
                throw new IllegalArgumentException("ctx.tickets.create needs contact_id or requester_email");
            }
            requesterEmail = Contact.normalizeEmail(requesterEmail);
        }
        if (requesterName == null || requesterName.isBlank()) {
            requesterName = requesterEmail;
        }
        Long departmentId = data.get("department_id") == null ? null : department(data.get("department_id"));
        String finalEmail = requesterEmail;
        String finalName = requesterName;
        String finalChannel = channel;
        Ticket ticket = unscoped(() -> tickets.create(subject, body == null ? "" : body, finalName, finalEmail, priority,
                departmentId, type, meta, finalChannel));
        if (data.get("status") != null) {
            unscoped(() -> tickets.changeStatusByName(ticket.getId(), String.valueOf(data.get("status")),
                    actorEmail(actor)));
        }
        return findTicket(ticket.getId());
    }

    /** Changes a ticket through the ticket service, so the matching events fire. */
    Map<String, Object> updateTicket(Object id, Map<String, Object> data, Authentication actor) {
        if (data == null) {
            throw new IllegalArgumentException("ctx.tickets.update requires an object");
        }
        checkKeys("ctx.tickets.update", data, TICKET_WRITES);
        Long ticketId = PluginJson.id(id);
        if (ticketId == null || !unscoped(() -> ticketRows.existsById(ticketId))) {
            throw new IllegalArgumentException("no ticket " + id);
        }
        String email = actorEmail(actor);
        String subject = text(data, "subject", "title");
        String body = text(data, "description", "body");
        TicketPriority priority = data.get("priority") == null ? null : priority(data.get("priority"));
        String type = ticketType(data);
        Map<String, Object> meta = data.get("metadata") == null ? null : metadata(data.get("metadata"));
        Long departmentId = data.get("department_id") == null ? null : department(data.get("department_id"));
        if (subject != null || body != null || priority != null || type != null || meta != null) {
            unscoped(() -> tickets.update(ticketId, subject, body, priority, type, meta));
        }
        if (data.get("status") != null) {
            unscoped(() -> tickets.changeStatusByName(ticketId, String.valueOf(data.get("status")), email));
        }
        if (departmentId != null) {
            unscoped(() -> tickets.changeDepartment(ticketId, departmentId, email));
        }
        return findTicket(ticketId);
    }

    // ---- ctx.replies

    Map<String, Object> findReply(Object id) {
        Long number = PluginJson.id(id);
        return number == null ? null : read(() -> replies.findById(number).map(PluginDomain::replyObject).orElse(null));
    }

    /** Up to 500 replies, filtered on {@code ticket_id}, {@code is_internal} and {@code type} (reply or note). */
    List<Map<String, Object>> queryReplies(Map<String, Object> filter) {
        Map<String, Object> conditions = filter == null ? Map.of() : filter;
        checkKeys("ctx.replies.query", conditions, List.of("ticket_id", "is_internal", "type"));
        Specification<Reply> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (conditions.containsKey("ticket_id")) {
                Long ticketId = PluginJson.id(conditions.get("ticket_id"));
                if (ticketId == null) {
                    throw new IllegalArgumentException("ctx.replies.query: ticket_id must be a number");
                }
                where.add(cb.equal(root.get("ticket").get("id"), ticketId));
            }
            if (conditions.containsKey("is_internal")) {
                if (!(conditions.get("is_internal") instanceof Boolean internal)) {
                    throw new IllegalArgumentException("ctx.replies.query: is_internal must be true or false");
                }
                where.add(cb.equal(root.get("internal"), internal));
            }
            if (conditions.containsKey("type")) {
                Object type = conditions.get("type");
                if (!"reply".equals(type) && !"note".equals(type)) {
                    throw new IllegalArgumentException("ctx.replies.query: unknown type " + type);
                }
                where.add(cb.equal(root.get("internal"), "note".equals(type)));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
        return read(() -> replies.findAll(spec, PageRequest.of(0, MAX_QUERY_ROWS, Sort.by("id"))).stream()
                .map(PluginDomain::replyObject).toList());
    }

    /**
     * Adds a reply ({@code reply.created}), or a note ({@code note.created}) with {@code is_internal},
     * as the signed-in user of the request being served, or as the system.
     */
    Map<String, Object> createReply(Map<String, Object> data, Authentication actor, String plugin) {
        if (data == null) {
            throw new IllegalArgumentException("ctx.replies.create requires an object");
        }
        checkKeys("ctx.replies.create", data, List.of("ticket_id", "body", "is_internal"));
        Long ticketId = PluginJson.id(data.get("ticket_id"));
        if (ticketId == null || !unscoped(() -> ticketRows.existsById(ticketId))) {
            throw new IllegalArgumentException("ctx.replies.create needs the id of a ticket");
        }
        String body = text(data, "body");
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("ctx.replies.create needs a body");
        }
        boolean internal = Boolean.TRUE.equals(data.get("is_internal"));
        String authorName;
        String authorEmail;
        String authorType;
        Map<String, Object> user = currentUser(actor);
        if (user != null) {
            authorName = String.valueOf(user.get("name"));
            authorEmail = String.valueOf(user.get("email"));
            authorType = "agent";
        } else {
            authorName = plugin;
            authorEmail = "";
            authorType = "system";
        }
        Reply reply = unscoped(() -> tickets.addReply(ticketId, body, authorName, authorEmail, authorType, internal));
        return findReply(reply.getId());
    }

    // ---- ctx.contacts, ctx.tags, ctx.departments, ctx.agents

    Map<String, Object> findContact(Object id) {
        Long number = PluginJson.id(id);
        return number == null ? null : contacts.findById(number).map(PluginDomain::contactObject).orElse(null);
    }

    Map<String, Object> findContactByEmail(String email) {
        if (email == null) {
            return null;
        }
        return contacts.findByEmail(Contact.normalizeEmail(email)).map(PluginDomain::contactObject).orElse(null);
    }

    /** Finds the contact with the email, or creates it with the name. */
    synchronized Map<String, Object> createContact(Map<String, Object> data) {
        Map<String, Object> fields = data == null ? Map.of() : data;
        checkKeys("ctx.contacts.create", fields, List.of("name", "email"));
        String email = Contact.normalizeEmail(text(fields, "email"));
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("ctx.contacts.create needs an email");
        }
        Contact existing = contacts.findByEmail(email).orElse(null);
        if (existing != null) {
            return contactObject(existing);
        }
        Contact contact = new Contact();
        contact.setEmail(email);
        String name = text(fields, "name");
        contact.setName(name == null || name.isBlank() ? null : name);
        return contactObject(contacts.save(contact));
    }

    List<Map<String, Object>> allTags() {
        return tags.findAll(Sort.by("id")).stream().map(PluginDomain::tagObject).toList();
    }

    /** Adds a tag ({@code name}, optional {@code color}); an existing name is returned as it is. */
    synchronized Map<String, Object> createTag(Map<String, Object> data) {
        Map<String, Object> fields = data == null ? Map.of() : data;
        checkKeys("ctx.tags.create", fields, List.of("name", "slug", "color"));
        String name = text(fields, "name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ctx.tags.create needs a name");
        }
        Tag existing = tags.findByName(name).orElse(null);
        if (existing != null) {
            return tagObject(existing);
        }
        Tag tag = new Tag();
        tag.setName(name);
        String color = text(fields, "color");
        if (color != null && !color.isBlank()) {
            tag.setColor(color);
        }
        return tagObject(tags.save(tag));
    }

    List<Map<String, Object>> allDepartments() {
        return departments.findAll(Sort.by("id")).stream().map(PluginDomain::departmentObject).toList();
    }

    Map<String, Object> findDepartment(Object id) {
        Long number = PluginJson.id(id);
        return number == null ? null : departments.findById(number).map(PluginDomain::departmentObject).orElse(null);
    }

    List<Map<String, Object>> allAgents() {
        return agents.findByActiveTrueOrderByName().stream()
                .filter(agent -> agent.isAgent() || agent.isAdmin())
                .map(PluginDomain::agentObject).toList();
    }

    Map<String, Object> findAgent(Object id) {
        Long number = PluginJson.id(id);
        return number == null ? null : agents.findById(number)
                .filter(agent -> agent.isActive() && (agent.isAgent() || agent.isAdmin()))
                .map(PluginDomain::agentObject).orElse(null);
    }

    // ---- ctx.broadcast

    /**
     * Publishes over the host's STOMP broker: {@code /topic/{channel}}, {@code /topic/users/{id}} and
     * {@code /topic/tickets/{id}}, as {@code {event, data}}. A no-op without a broker. While ticket
     * visibility is restricted, only {@code toTicket} is delivered, through the host's
     * {@link AuthorizedTicketBroadcaster}.
     */
    boolean broadcast(String destination, Long ticketId, String event, Object data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("data", PluginJson.plain(data));
        TicketAccess ticketAccess = access.getIfAvailable();
        if (ticketAccess != null && ticketAccess.enabled()) {
            AuthorizedTicketBroadcaster broadcaster = authorizedBroadcaster.getIfAvailable();
            if (ticketId == null || broadcaster == null) {
                return false;
            }
            broadcaster.deliver(ticketId, destination, payload);
            return true;
        }
        SimpMessagingTemplate template = messaging.getIfAvailable();
        if (template == null) {
            return false;
        }
        template.convertAndSend(destination, (Object) payload);
        return true;
    }

    static String channelDestination(String channel) {
        if (channel == null || !CHANNEL.matcher(channel).matches()) {
            throw new IllegalArgumentException("a broadcast channel is letters, digits, '.', '_', '-' and '/'");
        }
        return "/topic/" + channel;
    }
}
