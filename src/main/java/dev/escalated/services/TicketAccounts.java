package dev.escalated.services;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.security.TicketAccount;
import dev.escalated.security.TicketAccountResolver;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class TicketAccounts {
    private final EscalatedProperties properties;
    private final ObjectProvider<TicketAccountResolver> resolvers;
    private final TicketRepository tickets;

    public TicketAccounts(
            EscalatedProperties properties,
            ObjectProvider<TicketAccountResolver> resolvers,
            TicketRepository tickets) {
        this.properties = properties;
        this.resolvers = resolvers;
        this.tickets = tickets;
    }

    public void fromRequester(Ticket ticket) {
        if (!properties.getAccounts().isEnabled()
                || ticket.getAccountType() != null
                || ticket.getAccountId() != null) {
            return;
        }
        TicketAccountResolver resolver = resolvers.getIfAvailable();
        apply(ticket, resolver == null ? null : resolver.forRequester(ticket));
    }

    public void fromSubject(Ticket ticket, String type, String id) {
        if (!properties.getAccounts().isEnabled()
                || ticket.getAccountType() != null
                || ticket.getAccountId() != null) {
            return;
        }
        TicketAccountResolver resolver = resolvers.getIfAvailable();
        TicketAccount account = resolver == null ? null : resolver.forSubject(ticket, type, id);
        if (account != null
                && tickets.setInitialAccount(ticket.getId(), account.type(), account.id()) == 1) {
            apply(ticket, account);
        }
    }

    private void apply(Ticket ticket, TicketAccount account) {
        if (account != null) {
            ticket.setAccountType(account.type());
            ticket.setAccountId(account.id());
        }
    }
}
