package dev.escalated.services;

import dev.escalated.models.AgentProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mentions in replies and internal notes, as escalated-laravel's {@code MentionService}:
 * {@code @{Full Name}} (what the composer's mention picker inserts) and {@code @name} match a staff
 * member by name or email, ignoring case. {@link AgentPanelService} sends the mentioned agents a
 * {@code mention} notification; the composer's picker searches with {@link #suggestions}.
 */
public class MentionService {
    /** {@code @{Full Name}}. */
    private static final Pattern BRACED = Pattern.compile("@\\{([^}]+)\\}");
    /** {@code @name}, outside braces. */
    private static final Pattern SIMPLE = Pattern.compile("@([a-zA-Z0-9_.-]+)(?![^{]*\\})");

    public static final int SUGGESTIONS_LIMIT = 10;

    /** The names mentioned in a body, braced and simple, trimmed and unique, in order. */
    public List<String> extractMentions(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Pattern pattern : List.of(BRACED, SIMPLE)) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(1).trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** The staff a body mentions, by name or email (case-insensitive). */
    public List<AgentProfile> mentionedAgents(String text, Collection<AgentProfile> staff) {
        Set<String> names = new LinkedHashSet<>();
        for (String name : extractMentions(text)) {
            names.add(name.toLowerCase(Locale.ROOT));
        }
        List<AgentProfile> found = new ArrayList<>();
        if (names.isEmpty()) {
            return found;
        }
        for (AgentProfile agent : staff) {
            if (matches(names, agent.getName()) || matches(names, agent.getEmail())) {
                found.add(agent);
            }
        }
        return found;
    }

    /** The mention picker: up to {@code limit} staff whose name or email contains the query. */
    public List<AgentProfile> suggestions(String query, Collection<AgentProfile> staff, int limit) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<AgentProfile> found = new ArrayList<>();
        if (needle.isEmpty()) {
            return found;
        }
        for (AgentProfile agent : staff) {
            if (contains(agent.getName(), needle) || contains(agent.getEmail(), needle)) {
                found.add(agent);
                if (found.size() >= limit) {
                    break;
                }
            }
        }
        return found;
    }

    public String extractUsernameFromEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "";
        }
        String[] parts = email.split("@");
        return parts.length > 0 ? parts[0] : email;
    }

    private static boolean matches(Set<String> names, String value) {
        return value != null && !value.isBlank() && names.contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
