package dev.escalated.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.contracts.ImportAdapter;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Attachment;
import dev.escalated.models.Contact;
import dev.escalated.models.CustomField;
import dev.escalated.models.Department;
import dev.escalated.models.ImportJob;
import dev.escalated.models.ImportSourceMap;
import dev.escalated.models.Reply;
import dev.escalated.models.SatisfactionRating;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AttachmentRepository;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.CustomFieldRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.ImportJobRepository;
import dev.escalated.repositories.ImportSourceMapRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.SatisfactionRatingRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.email.inbound.AttachmentStorage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    private final CustomFieldService customFields;
    private final CustomFieldRepository customFieldRows;
    private final TicketCustomFieldValues fieldValues;
    private final AttachmentRepository attachments;
    private final ObjectProvider<AttachmentStorage> storage;
    private final SatisfactionRatingRepository ratings;
    private final EscalatedProperties properties;

    public ImportJobService(List<ImportAdapter> adapters, ImportJobRepository jobs, ImportSourceMapRepository maps,
                            ImportCredentials credentials, TicketRepository tickets, ReplyRepository replies,
                            TagRepository tags, DepartmentRepository departments, AgentProfileRepository agents,
                            ContactRepository contacts, CustomFieldService customFields,
                            CustomFieldRepository customFieldRows, TicketCustomFieldValues fieldValues,
                            AttachmentRepository attachments, ObjectProvider<AttachmentStorage> storage,
                            SatisfactionRatingRepository ratings, EscalatedProperties properties) {
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
        this.customFields = customFields;
        this.customFieldRows = customFieldRows;
        this.fieldValues = fieldValues;
        this.attachments = attachments;
        this.storage = storage;
        this.ratings = ratings;
        this.properties = properties;
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
                List<String[]> notes = new ArrayList<>();
                try {
                    String escalatedId = persist(job, entityType, record, notes);
                    maps.save(new ImportSourceMap(job.getId(), entityType, sourceId, escalatedId));
                    processed++;
                    // What the record was imported past (a custom field value that failed its
                    // check), logged against it.
                    for (String[] note : notes) {
                        if (errors.size() < MAX_ERRORS) {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("entity_type", note[0]);
                            entry.put("source_id", sourceId);
                            entry.put("error", note[1]);
                            entry.put("timestamp", Instant.now().toString());
                            errors.add(entry);
                        }
                    }
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

    private String persist(ImportJob job, String entityType, Map<String, Object> record, List<String[]> notes) {
        return switch (entityType) {
            case "agents" -> persistAgent(record);
            case "contacts" -> persistContact(record);
            case "tags" -> persistTag(record);
            case "departments" -> persistDepartment(record);
            case "tickets" -> persistTicket(job, record, notes);
            case "replies" -> persistReply(job, record);
            case "custom_fields" -> persistCustomField(record);
            case "attachments" -> persistAttachment(job, record);
            case "satisfaction_ratings" -> persistSatisfactionRating(job, record);
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

    private String persistTicket(ImportJob job, Map<String, Object> record, List<String[]> notes) {
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
        importFieldValues(job, saved.getId(), record, notes);
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

    // ── Custom fields, attachments and satisfaction ratings ─────────────

    /**
     * Laravel's {@code persistCustomField}: the definition is found by its slug or created with the
     * source's name, type and options, through {@link CustomFieldService#save}, so the admin form's
     * rules hold (a known type; options for a select).
     */
    private String persistCustomField(Map<String, Object> record) {
        String name = text(record, "name");
        if (name == null) {
            throw new IllegalArgumentException("Custom field has no name.");
        }
        Optional<CustomField> existing = customFieldRows.findByFieldKey(CustomFieldService.slugify(name));
        if (existing.isPresent()) {
            return existing.get().getId().toString();
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", name);
        input.put("type", Optional.ofNullable(text(record, "type")).orElse("text"));
        input.put("context", "ticket");
        if (record.get("options") != null) {
            input.put("options", record.get("options"));
        }
        return customFields.save(null, input).getId().toString();
    }

    /**
     * The custom field slug a ticket record's {@code custom_fields} key names, or null to skip it.
     * The wizard's ticket field mapping decides first: a key mapped to
     * {@code custom_fields.<slug>} lands on that field, a key mapped to "" (Skip field) is dropped.
     * Otherwise the key is a custom field's source id from this import, or a slug.
     */
    private String fieldSlugFor(ImportJob job, Map<?, ?> mappings, String key, List<String[]> notes) {
        if (mappings != null && mappings.containsKey(key)) {
            String target = mappings.get(key) == null ? "" : mappings.get(key).toString().trim();
            if (target.isEmpty()) {
                return null;
            }
            if (target.startsWith("custom_fields.")) {
                return target.substring("custom_fields.".length());
            }
            notes.add(new String[] {"custom_field_values",
                "custom_fields." + key + ": mapped to '" + target + "', which is not a custom field."});
            return null;
        }
        String fieldId = resolve(job.getId(), "custom_fields", key);
        if (fieldId != null) {
            Optional<CustomField> field = customFieldRows.findById(Long.valueOf(fieldId));
            if (field.isPresent()) {
                return field.get().getFieldKey();
            }
        }
        return key;
    }

    /**
     * Stores a ticket record's {@code custom_fields} values, each checked as a ticket update checks
     * it ({@link TicketCustomFieldValues#validate}: active ticket fields, type, options). One that
     * fails is left out and logged under {@code custom_field_values}; the ticket is kept. Fields the
     * record does not carry are not required: imported history predates them.
     */
    private void importFieldValues(ImportJob job, Long ticketId, Map<String, Object> record, List<String[]> notes) {
        if (!(record.get("custom_fields") instanceof Map<?, ?> given) || given.isEmpty()) {
            return;
        }
        Map<?, ?> mappings = mappingsOf(job).get("tickets") instanceof Map<?, ?> m ? m : null;
        TicketCustomFieldValues.Changes changes = new TicketCustomFieldValues.Changes();
        Map<String, Object> sorted = new TreeMap<>();
        given.forEach((key, value) -> sorted.put(String.valueOf(key), value));
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            String slug = fieldSlugFor(job, mappings, entry.getKey(), notes);
            if (slug == null) {
                continue;
            }
            Map<String, Object> one = new LinkedHashMap<>();
            one.put(slug, entry.getValue());
            try {
                changes.byField().putAll(fieldValues.validate(one, false).byField());
            } catch (ValidationFailedException e) {
                e.getErrors().forEach((key, messages) ->
                        notes.add(new String[] {"custom_field_values", key + ": " + String.join(" ", messages)}));
            }
        }
        fieldValues.apply(ticketId, changes);
    }

    /**
     * The extensions Laravel's live uploads accept ({@code mimes:} on CreateTicketRequest and
     * ReplyToTicketRequest), with the type each is stored as: taken from the extension, never from
     * the source's claim.
     */
    private static final Map<String, String> ATTACHMENT_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("csv", "text/csv"), Map.entry("txt", "text/plain"), Map.entry("zip", "application/zip"),
            Map.entry("mp4", "video/mp4"), Map.entry("mp3", "audio/mpeg"));

    /** Content a browser would read as markup, refused whatever the file is called. */
    private static final List<String> MARKUP = List.of(
            "<!doctype html", "<html", "<head", "<body", "<script", "<iframe", "<svg", "<?xml");

    /** The base name of a source filename, without control characters. */
    static String importFilename(String name) {
        if (name == null) {
            return "attachment";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (base.isEmpty() || ".".equals(base) || "..".equals(base)) {
            return "attachment";
        }
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private static byte[] content(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Attachment content must be bytes or a base64 string.");
        }
        if (text.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(text.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Attachment content is not valid base64.");
        }
    }

    /**
     * Laravel's {@code persistAttachment} ({@code parent_type} ticket or reply, default reply;
     * {@code parent_source_id}), with the rules of a live upload: the record carries the file's
     * bytes ({@code content}), checked against {@code escalated.imports.attachment-max-kb} and the
     * allowed types, and written through the host's {@link AttachmentStorage}, so they are served
     * only by the authorised download route. A storage path from the source is never trusted.
     */
    private String persistAttachment(ImportJob job, Map<String, Object> record) {
        String parentType = Optional.ofNullable(text(record, "parent_type")).orElse("reply");
        Attachment attachment = new Attachment();
        if ("ticket".equals(parentType)) {
            String id = ref(job, "tickets", record.get("parent_source_id"));
            if (id == null) {
                throw new IllegalStateException("Parent ticket not found for attachment.");
            }
            attachment.setTicket(tickets.findById(Long.valueOf(id)).orElseThrow());
        } else if ("reply".equals(parentType)) {
            String id = ref(job, "replies", record.get("parent_source_id"));
            Reply reply = id == null ? null : replies.findById(Long.valueOf(id)).orElse(null);
            if (reply == null) {
                throw new IllegalStateException("Parent reply not found for attachment.");
            }
            attachment.setReply(reply);
            attachment.setTicket(reply.getTicket());
        } else {
            throw new IllegalArgumentException("Unknown attachment parent type '" + parentType + "'.");
        }

        AttachmentStorage store = storage.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("No attachment storage is configured (an AttachmentStorage bean).");
        }
        byte[] bytes = content(record.get("content"));
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException(
                    "Attachment has no content: imports store the file's bytes, never a source path.");
        }
        long limitKb = properties.getImports().getAttachmentMaxKb();
        if (limitKb > 0 && bytes.length > limitKb * 1024) {
            throw new IllegalArgumentException("Attachment exceeds the " + limitKb + " KB upload limit.");
        }
        String name = importFilename(text(record, "filename"));
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mimeType = ATTACHMENT_TYPES.get(extension);
        if (mimeType == null) {
            throw new IllegalArgumentException("Attachment type '" + extension + "' is not allowed.");
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 512), StandardCharsets.ISO_8859_1)
                .replace("ï»¿", "").stripLeading().toLowerCase(Locale.ROOT);
        if (MARKUP.stream().anyMatch(head::startsWith)) {
            throw new IllegalArgumentException("Attachment content (markup) is not allowed.");
        }

        attachment.setFileName(name);
        attachment.setFilePath(store.put(name, bytes, mimeType));
        attachment.setFileSize((long) bytes.length);
        attachment.setMimeType(mimeType);
        Attachment saved = attachments.save(attachment);
        Instant created = instant(record, "created_at");
        if (created != null) {
            maps.keepAttachmentCreatedAt(saved.getId(), created);
        }
        return saved.getId().toString();
    }

    private static Integer rating(Object value) {
        if (value == null || value instanceof Boolean) {
            return null;
        }
        try {
            BigDecimal number = new BigDecimal(value.toString().trim());
            if (number.stripTrailingZeros().scale() > 0 || number.compareTo(BigDecimal.ONE) < 0
                    || number.compareTo(BigDecimal.valueOf(5)) > 0) {
                return null;
            }
            return number.intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /**
     * Laravel's {@code persistSatisfactionRating}: the rating (or score) and comment, on the
     * imported ticket, at the source's time. Only the row is written, with the ticket's requester
     * as the rater as a live rating records it: no access token, no resolved/closed rule, nothing a
     * live rating sets off. A ticket keeps one rating.
     */
    private String persistSatisfactionRating(ImportJob job, Map<String, Object> record) {
        String ticketId = ref(job, "tickets", record.get("ticket_source_id"));
        if (ticketId == null) {
            throw new IllegalStateException("Ticket not found for satisfaction rating.");
        }
        Object raw = record.get("rating") != null ? record.get("rating") : record.get("score");
        Integer score = rating(raw);
        if (score == null) {
            throw new IllegalArgumentException("The rating must be an integer between 1 and 5.");
        }
        Ticket ticket = tickets.findById(Long.valueOf(ticketId)).orElseThrow();
        if (!ratings.findByTicketId(ticket.getId()).isEmpty()) {
            throw new IllegalStateException("The ticket already has a satisfaction rating.");
        }
        SatisfactionRating row = new SatisfactionRating();
        row.setTicket(ticket);
        row.setRating(score);
        row.setComment(text(record, "comment"));
        row.setRaterEmail(ticket.getRequesterEmail());
        row.setRaterName(ticket.getRequesterName());
        SatisfactionRating saved = ratings.save(row);
        Instant created = instant(record, "created_at");
        if (created != null) {
            maps.keepRatingCreatedAt(saved.getId(), created);
        }
        return saved.getId().toString();
    }
}
