package dev.escalated.security;

/** Optional host bean for private, recipient-authorized delivery while visibility is restricted. */
public interface AuthorizedTicketBroadcaster {
    void deliver(Long ticketId, String destination, Object payload);
}
