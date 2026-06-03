package dev.escalated.services.newsletter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.Contact;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds JPA {@link Specification} instances for newsletter segment filters.
 * Column names are allowlisted before mapping to entity paths — never
 * concatenated from user input into dynamic property names.
 */
final class ContactSegmentSpecification {

    private static final Pattern METADATA_KEY = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private ContactSegmentSpecification() {}

    static Specification<Contact> forRules(
            List<ContactSegmentResolver.SegmentRule> rules, boolean includeOptedOut) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeOptedOut) {
                predicates.add(cb.isNull(root.get("marketingOptOutAt")));
            }
            for (ContactSegmentResolver.SegmentRule rule : rules) {
                predicates.add(rulePredicate(root, cb, rule));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate rulePredicate(
            Root<Contact> root, CriteriaBuilder cb, ContactSegmentResolver.SegmentRule rule) {
        String field = rule.field().toLowerCase(Locale.ROOT);
        if (field.startsWith("metadata.")) {
            return metadataPredicate(root, cb, field.substring("metadata.".length()), rule);
        }
        return columnPredicate(root, cb, field, rule);
    }

    private static Predicate columnPredicate(
            Root<Contact> root,
            CriteriaBuilder cb,
            String field,
            ContactSegmentResolver.SegmentRule rule) {
        Path<?> path = columnPath(root, field);
        return switch (rule.op()) {
            case "is_null" -> cb.isNull(path);
            case "not_null" -> cb.isNotNull(path);
            case "=" -> equalityPredicate(cb, path, rule.value());
            case "!=" -> cb.not(equalityPredicate(cb, path, rule.value()));
            case ">" -> orderingPredicate(cb, path, rule.value(), Comparison.GT);
            case ">=" -> orderingPredicate(cb, path, rule.value(), Comparison.GTE);
            case "<" -> orderingPredicate(cb, path, rule.value(), Comparison.LT);
            case "<=" -> orderingPredicate(cb, path, rule.value(), Comparison.LTE);
            case "contains" -> stringLikePredicate(cb, path, rule.value(), "%", "%");
            case "starts_with" -> stringLikePredicate(cb, path, rule.value(), "", "%");
            case "ends_with" -> stringLikePredicate(cb, path, rule.value(), "%", "");
            case "in" -> inPredicate(cb, path, rule.value());
            default -> cb.disjunction();
        };
    }

    private static Path<?> columnPath(Root<Contact> root, String field) {
        return switch (field) {
            case "id" -> root.get("id");
            case "email" -> root.get("email");
            case "name" -> root.get("name");
            case "user_id" -> root.get("userId");
            case "created_at" -> root.get("createdAt");
            case "updated_at" -> root.get("updatedAt");
            case "marketing_opt_out_at" -> root.get("marketingOptOutAt");
            default -> throw new IllegalArgumentException("disallowed column: " + field);
        };
    }

    private static Predicate equalityPredicate(CriteriaBuilder cb, Path<?> path, String value) {
        if (value == null) {
            return cb.isNull(path);
        }
        if (path.getJavaType() == Long.class) {
            return cb.equal(path, Long.parseLong(value));
        }
        if (path.getJavaType() == Instant.class) {
            return cb.equal(path, Instant.parse(value));
        }
        return cb.equal(cb.lower(path.as(String.class)), value.toLowerCase(Locale.ROOT));
    }

    private enum Comparison {
        GT,
        GTE,
        LT,
        LTE
    }

    private static Predicate orderingPredicate(
            CriteriaBuilder cb, Path<?> path, String value, Comparison comparison) {
        if (value == null) {
            return cb.disjunction();
        }
        if (path.getJavaType() == Instant.class) {
            Instant instant = Instant.parse(value);
            @SuppressWarnings("unchecked")
            Expression<Instant> instantPath = (Expression<Instant>) path;
            return switch (comparison) {
                case GT -> cb.greaterThan(instantPath, instant);
                case GTE -> cb.greaterThanOrEqualTo(instantPath, instant);
                case LT -> cb.lessThan(instantPath, instant);
                case LTE -> cb.lessThanOrEqualTo(instantPath, instant);
            };
        }
        if (path.getJavaType() == Long.class) {
            Long number = Long.parseLong(value);
            @SuppressWarnings("unchecked")
            Expression<Long> longPath = (Expression<Long>) path;
            return switch (comparison) {
                case GT -> cb.greaterThan(longPath, number);
                case GTE -> cb.greaterThanOrEqualTo(longPath, number);
                case LT -> cb.lessThan(longPath, number);
                case LTE -> cb.lessThanOrEqualTo(longPath, number);
            };
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        Expression<String> stringPath = cb.lower(path.as(String.class));
        return switch (comparison) {
            case GT -> cb.greaterThan(stringPath, lowered);
            case GTE -> cb.greaterThanOrEqualTo(stringPath, lowered);
            case LT -> cb.lessThan(stringPath, lowered);
            case LTE -> cb.lessThanOrEqualTo(stringPath, lowered);
        };
    }

    private static Predicate stringLikePredicate(
            CriteriaBuilder cb, Path<?> path, String value, String prefix, String suffix) {
        if (value == null || path.getJavaType() != String.class) {
            return cb.disjunction();
        }
        String pattern = prefix + escapeLike(value.toLowerCase(Locale.ROOT)) + suffix;
        return cb.like(cb.lower(path.as(String.class)), pattern);
    }

    private static Predicate inPredicate(CriteriaBuilder cb, Path<?> path, String value) {
        if (value == null) {
            return cb.disjunction();
        }
        String[] parts = value.split(",");
        List<Predicate> options = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            options.add(equalityPredicate(cb, path, trimmed));
        }
        if (options.isEmpty()) {
            return cb.disjunction();
        }
        return cb.or(options.toArray(Predicate[]::new));
    }

    private static Predicate metadataPredicate(
            Root<Contact> root,
            CriteriaBuilder cb,
            String key,
            ContactSegmentResolver.SegmentRule rule) {
        if (!METADATA_KEY.matcher(key).matches()) {
            return cb.disjunction();
        }
        Path<String> json = root.get("metadataJson");
        return switch (rule.op()) {
            case "is_null" -> cb.or(cb.isNull(json), cb.not(cb.like(cb.lower(json), metadataKeyPattern(key))));
            case "not_null" -> cb.and(cb.isNotNull(json), cb.like(cb.lower(json), metadataKeyPattern(key)));
            case "=" -> metadataValueLike(cb, json, key, rule.value(), true);
            case "!=" -> cb.not(metadataValueLike(cb, json, key, rule.value(), true));
            case "contains" -> metadataValueLike(cb, json, key, rule.value(), false);
            case "starts_with" -> cb.and(
                    cb.isNotNull(json),
                    cb.like(cb.lower(json), "%\"" + escapeLike(key.toLowerCase(Locale.ROOT)) + "\":\""
                            + escapeLike(rule.value() == null ? "" : rule.value().toLowerCase(Locale.ROOT)) + "%"));
            case "ends_with" -> cb.and(
                    cb.isNotNull(json),
                    cb.like(
                            cb.lower(json),
                            "%\""
                                    + escapeLike(key.toLowerCase(Locale.ROOT))
                                    + "\":%"
                                    + escapeLike(rule.value() == null ? "" : rule.value().toLowerCase(Locale.ROOT))
                                    + "\"%"));
            case ">", ">=", "<", "<=" -> metadataOrderingPredicate(cb, json, key, rule);
            case "in" -> metadataInPredicate(cb, json, key, rule.value());
            default -> cb.disjunction();
        };
    }

    private static String metadataKeyPattern(String key) {
        return "%\"" + escapeLike(key.toLowerCase(Locale.ROOT)) + "\":%";
    }

    private static Predicate metadataValueLike(
            CriteriaBuilder cb, Path<String> json, String key, String value, boolean exact) {
        if (value == null) {
            return cb.isNull(json);
        }
        String jsonFragment = metadataJsonFragment(key, value);
        String pattern = exact
                ? "%" + escapeLike(jsonFragment.toLowerCase(Locale.ROOT)) + "%"
                : "%\"" + escapeLike(key.toLowerCase(Locale.ROOT)) + "\"%"
                        + escapeLike(value.toLowerCase(Locale.ROOT)) + "%";
        return cb.and(cb.isNotNull(json), cb.like(cb.lower(json), pattern));
    }

    private static String metadataJsonFragment(String key, String value) {
        try {
            return "\"" + key + "\":" + new ObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "\"" + key + "\":\"" + escapeLike(value) + "\"";
        }
    }

    private static Predicate metadataOrderingPredicate(
            CriteriaBuilder cb, Path<String> json, String key, ContactSegmentResolver.SegmentRule rule) {
        if (rule.value() == null) {
            return cb.disjunction();
        }
        String pattern = "%\"" + escapeLike(key.toLowerCase(Locale.ROOT)) + "\":%"
                + escapeLike(rule.value().toLowerCase(Locale.ROOT)) + "%";
        Predicate present = cb.and(cb.isNotNull(json), cb.like(cb.lower(json), pattern));
        return switch (rule.op()) {
            case ">" -> present;
            case ">=" -> present;
            case "<" -> cb.not(present);
            case "<=" -> cb.not(present);
            default -> cb.disjunction();
        };
    }

    private static Predicate metadataInPredicate(CriteriaBuilder cb, Path<String> json, String key, String value) {
        if (value == null) {
            return cb.disjunction();
        }
        List<Predicate> options = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                options.add(metadataValueLike(cb, json, key, trimmed, true));
            }
        }
        if (options.isEmpty()) {
            return cb.disjunction();
        }
        return cb.or(options.toArray(Predicate[]::new));
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
