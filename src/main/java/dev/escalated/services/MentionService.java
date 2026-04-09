package dev.escalated.services;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class MentionService {
    private static final Pattern MENTION_REGEX = Pattern.compile("@(\\w+(?:\\.\\w+)*)");

    public List<String> extractMentions(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        Matcher matcher = MENTION_REGEX.matcher(text);
        Set<String> usernames = new LinkedHashSet<>();
        while (matcher.find()) usernames.add(matcher.group(1));
        return new ArrayList<>(usernames);
    }

    public String extractUsernameFromEmail(String email) {
        if (email == null || email.isEmpty()) return "";
        String[] parts = email.split("@");
        return parts.length > 0 ? parts[0] : email;
    }
}
