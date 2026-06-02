package dev.escalated.services.newsletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.Contact;
import dev.escalated.models.newsletter.NewsletterList;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.NewsletterListMemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class ContactSegmentResolver {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "id", "email", "name", "user_id", "created_at", "updated_at", "marketing_opt_out_at");

    private static final Set<String> ALLOWED_OPS = Set.of(
            "=", "!=", ">", ">=", "<", "<=",
            "contains", "starts_with", "ends_with", "in", "is_null", "not_null");

    private final ContactRepository contactRepository;
    private final NewsletterListMemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    public ContactSegmentResolver(
            ContactRepository contactRepository,
            NewsletterListMemberRepository memberRepository,
            ObjectMapper objectMapper) {
        this.contactRepository = contactRepository;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Long> resolve(NewsletterList list) {
        if ("static".equals(list.getKind())) {
            return memberRepository.findByListId(list.getId()).stream()
                    .map(m -> m.getContactId())
                    .toList();
        }
        return applyFilter(list.getFilterJson(), true).stream().map(Contact::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> resolveSendable(NewsletterList list) {
        if ("static".equals(list.getKind())) {
            List<Long> memberIds = memberRepository.findByListId(list.getId()).stream()
                    .map(m -> m.getContactId())
                    .toList();
            if (memberIds.isEmpty()) {
                return List.of();
            }
            return contactRepository.findAllById(memberIds).stream()
                    .filter(c -> c.getMarketingOptOutAt() == null)
                    .map(Contact::getId)
                    .toList();
        }
        return applyFilter(list.getFilterJson(), false).stream().map(Contact::getId).toList();
    }

    @Transactional(readOnly = true)
    public int countMatches(String filterJson) {
        return applyFilter(filterJson, true).size();
    }

    private List<Contact> applyFilter(String filterJson, boolean includeOptedOut) {
        List<Contact> contacts = new ArrayList<>(contactRepository.findAll());
        if (!includeOptedOut) {
            contacts = contacts.stream().filter(c -> c.getMarketingOptOutAt() == null).toList();
        }
        for (SegmentRule rule : parseRules(filterJson)) {
            contacts = contacts.stream().filter(c -> matches(c, rule)).toList();
        }
        return contacts;
    }

    private List<SegmentRule> parseRules(String filterJson) {
        List<SegmentRule> rules = new ArrayList<>();
        if (filterJson == null || filterJson.isBlank()) {
            return rules;
        }
        try {
            JsonNode root = objectMapper.readTree(filterJson);
            JsonNode rulesNode = root.get("rules");
            if (rulesNode == null || !rulesNode.isArray()) {
                return rules;
            }
            for (JsonNode item : rulesNode) {
                String field = text(item, "field");
                String op = text(item, "op");
                if (field == null || op == null) {
                    continue;
                }
                if (!isAllowedField(field) || !ALLOWED_OPS.contains(op)) {
                    continue;
                }
                rules.add(new SegmentRule(field, op, valueToString(item.get("value"))));
            }
        } catch (Exception ignored) {
            // invalid filter JSON → no rules
        }
        return rules;
    }

    private static boolean isAllowedField(String field) {
        return ALLOWED_FIELDS.contains(field.toLowerCase(Locale.ROOT))
                || field.toLowerCase(Locale.ROOT).startsWith("metadata.");
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String valueToString(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return value.toString();
    }

    private boolean matches(Contact contact, SegmentRule rule) {
        String actual = resolveField(contact, rule.field());
        return switch (rule.op()) {
            case "is_null" -> actual == null;
            case "not_null" -> actual != null;
            case "=" -> compare(actual, rule.value()) == 0;
            case "!=" -> compare(actual, rule.value()) != 0;
            case ">" -> compare(actual, rule.value()) > 0;
            case ">=" -> compare(actual, rule.value()) >= 0;
            case "<" -> compare(actual, rule.value()) < 0;
            case "<=" -> compare(actual, rule.value()) <= 0;
            case "contains" -> actual != null && rule.value() != null
                    && actual.toLowerCase(Locale.ROOT).contains(rule.value().toLowerCase(Locale.ROOT));
            case "starts_with" -> actual != null && rule.value() != null
                    && actual.toLowerCase(Locale.ROOT).startsWith(rule.value().toLowerCase(Locale.ROOT));
            case "ends_with" -> actual != null && rule.value() != null
                    && actual.toLowerCase(Locale.ROOT).endsWith(rule.value().toLowerCase(Locale.ROOT));
            case "in" -> {
                if (actual == null || rule.value() == null) {
                    yield false;
                }
                String[] parts = rule.value().split(",");
                boolean found = false;
                for (String part : parts) {
                    if (actual.equalsIgnoreCase(part.trim())) {
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            default -> false;
        };
    }

    private String resolveField(Contact contact, String field) {
        String normalized = field.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("metadata.")) {
            String key = field.substring("metadata.".length());
            try {
                JsonNode root = objectMapper.readTree(
                        contact.getMetadataJson() == null ? "{}" : contact.getMetadataJson());
                JsonNode value = root.get(key);
                return value == null || value.isNull() ? null : value.asText();
            } catch (Exception ex) {
                return null;
            }
        }
        return switch (normalized) {
            case "id" -> String.valueOf(contact.getId());
            case "email" -> contact.getEmail();
            case "name" -> contact.getName();
            case "user_id" -> contact.getUserId();
            case "created_at" -> contact.getCreatedAt() == null ? null : contact.getCreatedAt().toString();
            case "updated_at" -> contact.getUpdatedAt() == null ? null : contact.getUpdatedAt().toString();
            case "marketing_opt_out_at" -> contact.getMarketingOptOutAt() == null
                    ? null
                    : contact.getMarketingOptOutAt().toString();
            default -> null;
        };
    }

    private static int compare(String actual, String expected) {
        if (actual == null) {
            return expected == null ? 0 : -1;
        }
        if (expected == null) {
            return 1;
        }
        try {
            java.time.Instant actualInstant = java.time.Instant.parse(actual);
            java.time.Instant expectedInstant = java.time.Instant.parse(expected);
            return actualInstant.compareTo(expectedInstant);
        } catch (Exception ignored) {
            // fall through
        }
        try {
            double actualNumber = Double.parseDouble(actual);
            double expectedNumber = Double.parseDouble(expected);
            return Double.compare(actualNumber, expectedNumber);
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return actual.compareToIgnoreCase(expected);
    }

    private record SegmentRule(String field, String op, String value) {}
}
