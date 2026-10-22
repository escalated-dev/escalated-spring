package dev.escalated.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.contracts.ImportAdapter;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Contact;
import dev.escalated.models.Department;
import dev.escalated.models.ImportJob;
import dev.escalated.models.ImportSourceMap;
import dev.escalated.models.Reply;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.ImportJobRepository;
import dev.escalated.repositories.ImportSourceMapRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Keeps import jobs and runs them, as escalated-laravel's {@code ImportService}: walk the
 * adapter's entity types in order under {@link ImportContext}, resume each from its saved cursor
 * and counters, write every record not yet in the source map, log failures, save progress after
 * each batch, and stop between batches when the job has been paused or cancelled. A completed
 * job drops its credentials.
 *
 * <p>Tickets here carry their requester as a name and email, so contacts import as Escalated
 * contacts. Agents must already exist as agent profiles (matched by email).
 */
@Service
public class ImportJobService {

    private static final Logger log = LoggerFactory.getLogger(ImportJobService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ERRORS = 10_000;

    private final List<ImportAdapter> adapters;
    private final ImportJobRepository jobs;
    private final ImportSourceMapRepository maps;
    private final ImportCredentials credentials;
    private final TicketRepository tickets;
    private final ReplyRepository replies;
    private final TagRepository tags;
    private final DepartmentRepository departments;
    private final AgentProfileRepository agents;
    private final ContactRepository contacts;

    public ImportJobService(List<ImportAdapter> adapters, ImportJobRepository jobs, ImportSourceMapRepository maps,
                            ImportCredentials credentials, TicketRepository tickets, ReplyRepository replies,
                            TagRepository tags, DepartmentRepository departments, AgentProfileRepository agents,
                            ContactRepository contacts) {
        this.adapters = adapters;
        this.jobs = jobs;
        this.maps = maps;
        this.credentials = credentials;
        this.tickets = tickets;
        this.replies = replies;
        this.tags = tags;
        this.departments = departments;
        this.agents = agents;
        this.contacts = contacts;
    }

    public List<ImportAdapter> adapters() {
        return adapters;
    }

    public Optional<ImportAdapter> adapter(String platform) {
        return adapters.stream().filter(a -> a.name().equals(platform)).findFirst();
    }

    public ImportCredentials credentials() {
        return credentials;
    }

    /** Runs (or resumes) the job. A run that throws marks the job failed. */
    public void run(String jobId) {
        ImportJob job = jobs.findById(jobId).orElse(null);
        if (job == null || "completed".equals(job.getStatus())) {
            return;
        }
        try {
            ImportContext.suppress(() -> {
                runJob(job);
                return null;
            });
        } catch (RuntimeException e) {
            log.error("Import {} failed: {}", jobId, e.getMessage());
            jobs.setStatus(jobId, "failed");
        }
    }

    private void runJob(ImportJob job) {
        ImportAdapter adapter = adapter(job.getPlatform())
                .orElseThrow(() -> new IllegalStateException("No adapter found for platform '" + job.getPlatform() + "'."));
        if (!"importing".equals(job.getStatus())) {
            job.transitionTo("importing");
        }
        if (job.getStartedAt() == null) {
            job.setStartedAt(Instant.now());
        }
        ImportJob saved = jobs.save(job);
        Map<String, String> creds = credentials.open(saved.getCredentials());
        if (creds == null) {
            creds = Map.of();
        }

        for (String entityType : adapter.entityTypes()) {
            if (stopped(saved.getId()) || importEntityType(saved, adapter, entityType, creds)) {
                return;
            }
        }
        if (stopped(saved.getId())) {
            return;
        }
        ImportJob done = jobs.findById(saved.getId()).orElseThrow();
        done.setStatus("completed");
        done.setCompletedAt(Instant.now());
        done.setCredentials(null);
        jobs.save(done);
    }

    /** Re-reads the status the admin may have changed: paused, or cancelled (failed). */
    private boolean stopped(String jobId) {
        String status = jobs.statusOf(jobId);
        return "paused".equals(status) || "failed".equals(status);
    }

    public static Map<String, Map<String, Object>> progressOf(ImportJob job) {
        return read(job.getProgress(), new TypeReference<LinkedHashMap<String, Map<String, Object>>>() { },
                new LinkedHashMap<>());
    }

    public static List<Map<String, Object>> errorsOf(ImportJob job) {
        return read(job.getErrorLog(), new TypeReference<ArrayList<Map<String, Object>>>() { }, new ArrayList<>());
    }

    public static Map<String, Object> mappingsOf(ImportJob job) {
        return read(job.getFieldMappings(), new TypeReference<LinkedHashMap<String, Object>>() { }, new LinkedHashMap<>());
    }

    private static <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return JSON.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return true when the job was paused or cancelled mid-type */
    private boolean importEntityType(ImportJob job, ImportAdapter adapter, String entityType,
                                     Map<String, String> creds) {
        ImportJob current = jobs.findById(job.getId()).orElseThrow();
        Map<String, Map<String, Object>> progress = progressOf(current);
        List<Map<String, Object>> errors = errorsOf(current);
        Map<String, Object> saved = progress.computeIfAbsent(entityType, k -> new LinkedHashMap<>(Map.of(
                "total", 0, "processed", 0, "skipped", 0, "failed", 0)));
        int processed = number(saved.get("processed"));
        int skipped = number(saved.get("skipped"));
        int failed = number(saved.get("failed"));
        String cursor = saved.get("cursor") instanceof String c ? c : null;

        while (true) {
            ImportAdapter.ExtractResult result = adapter.extract(entityType, creds, cursor);
            for (Map<String, Object> record : result.records()) {
                String sourceId = text(record, "source_id");
                if (sourceId == null) {
                    failed++;
                    continue;
                }
                if (resolve(job.getId(), entityType, sourceId) != null) {
                    skipped++;
                    continue;
                }
                try {
                    String escalatedId = persist(job, entityType, record);
                    maps.save(new ImportSourceMap(job.getId(), entityType, sourceId, escalatedId));
                    processed++;
                } catch (RuntimeException e) {
                    failed++;
                    if (errors.size() < MAX_ERRORS) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("entity_type", entityType);
                        entry.put("source_id", sourceId);
                        entry.put("error", e.getMessage());
                        entry.put("timestamp", Instant.now().toString());
                        errors.add(entry);
                    }
                }
            }

            saved.put("processed", processed);
            saved.put("skipped", skipped);
            saved.put("failed", failed);
            if (result.totalCount() != null) {
                saved.put("total", result.totalCount());
            }
            // As Laravel's: an exhausted extract keeps the last cursor, so a resume re-reads one
            // page, not the whole type.
            if (result.cursor() != null) {
                saved.put("cursor", result.cursor());
            } else {
                saved.putIfAbsent("cursor", null);
            }
            jobs.saveProgress(job.getId(), write(progress), write(errors), Instant.now());

            if (stopped(job.getId())) {
                return true;
            }
            if (result.exhausted()) {
                return false;
            }
            cursor = result.cursor();
        }
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private String resolve(String jobId, String entityType, String sourceId) {
        return maps.findByImportJobIdAndEntityTypeAndSourceId(jobId, entityType, sourceId)
                .map(ImportSourceMap::getEscalatedId).orElse(null);
    }

    private String ref(ImportJob job, String entityType, Object sourceId) {
        if (sourceId == null || sourceId.toString().isBlank()) {
            return null;
        }
        return resolve(job.getId(), entityType, sourceId.toString().trim());
    }

    private static String text(Map<String, Object> record, String key) {
        Object value = record.get(key);
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private String persist(ImportJob job, String entityType, Map<String, Object> record) {
        return switch (entityType) {
            case "agents" -> persistAgent(record);
            case "contacts" -> persistContact(record);
            case "tags" -> persistTag(record);
            case "departments" -> persistDepartment(record);
            case "tickets" -> persistTicket(job, record);
            case "replies" -> persistReply(job, record);
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }

    private String persistAgent(Map<String, Object> record) {
        String email = text(record, "email");
        return agents.findByEmail(email == null ? "" : email)
                .map(a -> a.getId().toString())
                .orElseThrow(() -> new IllegalStateException(
                        "Agent with email '" + email + "' not found in host application."));
    }

    private String persistContact(Map<String, Object> record) {
        String email = Contact.normalizeEmail(text(record, "email"));
        if (email.isEmpty()) {
            throw new IllegalArgumentException("Contact has no email.");
        }
        Contact contact = contacts.findByEmail(email).orElseGet(() -> {
            Contact created = new Contact();
            created.setEmail(email);
            created.setName(text(record, "name"));
            return contacts.save(created);
        });
        return contact.getId().toString();
    }

    private String persistTag(Map<String, Object> record) {
        String name = text(record, "name");
        if (name == null) {
            throw new IllegalArgumentException("Tag has no name.");
        }
        Tag tag = tags.findByName(name).orElseGet(() -> {
            Tag created = new Tag();
            created.setName(name);
            return tags.save(created);
        });
        return tag.getId().toString();
    }

    private String persistDepartment(Map<String, Object> record) {
        String name = text(record, "name");
        if (name == null) {
            throw new IllegalArgumentException("Department has no name.");
        }
        Department department = departments.findByName(name).orElseGet(() -> {
            Department created = new Department();
            created.setName(name);
            created.setActive(true);
            return departments.save(created);
        });
        return department.getId().toString();
    }

    /** As Laravel's TicketStatus::tryFrom: an unknown value falls back to the default. */
    private static <E extends Enum<E>> E lookup(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().toLowerCase(Locale.ROOT).equals(value.toLowerCase(Locale.ROOT))) {
                return constant;
            }
        }
        return fallback;
    }

    private static Instant instant(Map<String, Object> record, String key) {
        String value = text(record, key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String persistTicket(ImportJob job, Map<String, Object> record) {
        String subject = Optional.ofNullable(text(record, "title")).orElseGet(
                () -> Optional.ofNullable(text(record, "subject")).orElse("Imported ticket"));
        Ticket ticket = new Ticket();
        ticket.setSubject(subject);
        ticket.setBody(Optional.ofNullable(text(record, "description"))
                .orElseGet(() -> Optional.ofNullable(text(record, "body")).orElse(subject)));
        ticket.setStatus(lookup(TicketStatus.class, text(record, "status"), TicketStatus.OPEN));
        ticket.setPriority(lookup(TicketPriority.class, text(record, "priority"), TicketPriority.MEDIUM));
        ticket.setTicketNumber("IMP-" + UUID.randomUUID().toString().substring(0, 13));
        ticket.setGuestAccessToken(UUID.randomUUID().toString());
        ticket.setChannel("import");

        String requester = ref(job, "contacts", record.get("requester_source_id"));
        Contact contact = requester == null ? null : contacts.findById(Long.valueOf(requester)).orElse(null);
        if (contact != null) {
            ticket.setContact(contact);
            ticket.setRequesterEmail(contact.getEmail());
            ticket.setRequesterName(contact.getName() == null ? contact.getEmail() : contact.getName());
        } else {
            ticket.setRequesterEmail("unknown@import.invalid");
            ticket.setRequesterName("Unknown");
        }
        String assignee = ref(job, "agents", record.get("assignee_source_id"));
        if (assignee != null) {
            agents.findById(Long.valueOf(assignee)).ifPresent(ticket::setAssignedAgent);
        }
        String department = ref(job, "departments", record.get("department_source_id"));
        if (department != null) {
            departments.findById(Long.valueOf(department)).ifPresent(ticket::setDepartment);
        }
        if (record.get("tag_source_ids") instanceof List<?> sources) {
            for (Object source : sources) {
                String tagId = ref(job, "tags", source);
                if (tagId != null) {
                    tags.findById(Long.valueOf(tagId)).ifPresent(ticket.getTags()::add);
                }
            }
        }

        Ticket saved = tickets.save(ticket);
        saved.setTicketNumber(String.format("IMP-%06d", saved.getId()));
        tickets.save(saved);
        Instant created = instant(record, "created_at");
        if (created != null) {
            maps.keepTicketCreatedAt(saved.getId(), created);
        }
        return saved.getId().toString();
    }

    private String persistReply(ImportJob job, Map<String, Object> record) {
        String ticketId = ref(job, "tickets", record.get("ticket_source_id"));
        if (ticketId == null) {
            throw new IllegalStateException("Parent ticket not found for reply.");
        }
        Reply reply = new Reply();
        reply.setTicket(tickets.findById(Long.valueOf(ticketId)).orElseThrow());
        reply.setBody(Optional.ofNullable(text(record, "body")).orElse(""));
        Object internal = record.get("is_internal_note");
        reply.setInternal(Boolean.TRUE.equals(internal) || "true".equals(String.valueOf(internal)));

        String agentId = ref(job, "agents", record.get("author_source_id"));
        String contactId = agentId == null ? ref(job, "contacts", record.get("author_source_id")) : null;
        if (agentId != null) {
            AgentProfile agent = agents.findById(Long.valueOf(agentId)).orElseThrow();
            reply.setAuthorType("agent");
            reply.setAuthorName(agent.getName());
            reply.setAuthorEmail(agent.getEmail());
        } else if (contactId != null) {
            Contact contact = contacts.findById(Long.valueOf(contactId)).orElseThrow();
            reply.setAuthorType("customer");
            reply.setAuthorName(contact.getName() == null ? contact.getEmail() : contact.getName());
            reply.setAuthorEmail(contact.getEmail());
        } else {
            // No author the import knows: kept as a system reply naming its source.
            reply.setAuthorType("system");
            reply.setAuthorName("Imported from " + job.getPlatform());
            reply.setAuthorEmail("import@" + job.getPlatform() + ".invalid");
        }
        Reply saved = replies.save(reply);
        Instant created = instant(record, "created_at");
        if (created != null) {
            maps.keepReplyCreatedAt(saved.getId(), created);
        }
        return saved.getId().toString();
    }
}
