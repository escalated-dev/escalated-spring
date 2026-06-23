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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class ContactSegmentResolver {

    static final Set<String> ALLOWED_FIELDS = Set.of(
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
        return queryIds(list.getFilterJson(), true);
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
            Specification<Contact> spec = (root, query, cb) -> root.get("id").in(memberIds);
            spec = spec.and((root, query, cb) -> cb.isNull(root.get("marketingOptOutAt")));
            return contactRepository.findAll(spec).stream().map(Contact::getId).toList();
        }
        return queryIds(list.getFilterJson(), false);
    }

    @Transactional(readOnly = true)
    public int countMatches(String filterJson) {
        List<SegmentRule> rules = parseRules(filterJson);
        if (rules.isEmpty()) {
            return (int) contactRepository.count();
        }
        return (int) contactRepository.count(ContactSegmentSpecification.forRules(rules, true));
    }

    /** Package-visible for tests — unknown columns must not reach Criteria paths. */
    static boolean isAllowedField(String field) {
        return ALLOWED_FIELDS.contains(field.toLowerCase(Locale.ROOT))
                || field.toLowerCase(Locale.ROOT).startsWith("metadata.");
    }

    private List<Long> queryIds(String filterJson, boolean includeOptedOut) {
        List<SegmentRule> rules = parseRules(filterJson);
        Specification<Contact> spec = ContactSegmentSpecification.forRules(rules, includeOptedOut);
        return contactRepository.findAll(spec).stream().map(Contact::getId).toList();
    }

    List<SegmentRule> parseRules(String filterJson) {
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

    record SegmentRule(String field, String op, String value) {}
}
