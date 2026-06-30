package dev.escalated.services;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the recipient user ids for a ticket's followers.
 *
 * <p>The package abstracts the host user table, so it cannot email follower
 * users itself — these ids are exposed for the host app to deliver to. See
 * issue #74.
 */
public final class FollowerRecipients {

    private FollowerRecipients() {}

    /**
     * Excludes the actor (a user is never notified of their own action) and
     * de-duplicates the given user ids, preserving order.
     */
    public static List<String> resolve(List<String> userIds, String excludeUserId) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String userId : userIds) {
            if (userId.equals(excludeUserId) || !seen.add(userId)) {
                continue;
            }
            result.add(userId);
        }
        return result;
    }
}
