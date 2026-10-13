package dev.escalated.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@org.hibernate.annotations.Filter(
        name = "escalatedTicketAccess",
        condition = dev.escalated.security.TicketAccessSql.TICKET_LINK_STANDARD,
        deduceAliasInjectionPoints = false,
        aliases =
                @org.hibernate.annotations.SqlFragmentAlias(
                        alias = "row",
                        table = "escalated_ticket_links"))
@org.hibernate.annotations.DialectOverride.Filters(
        dialect = org.hibernate.dialect.MySQLDialect.class,
        override =
                @org.hibernate.annotations.Filters(
                        @org.hibernate.annotations.Filter(
                                name = "escalatedTicketAccess",
                                condition =
                                        dev.escalated.security.TicketAccessSql.TICKET_LINK_MYSQL,
                                deduceAliasInjectionPoints = false,
                                aliases =
                                        @org.hibernate.annotations.SqlFragmentAlias(
                                                alias = "row",
                                                table = "escalated_ticket_links"))))
@Entity
@Table(name = "escalated_ticket_links")
public class TicketLink extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_ticket_id", nullable = false)
    private Ticket sourceTicket;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_ticket_id", nullable = false)
    private Ticket targetTicket;

    @Column(name = "link_type", nullable = false)
    private String linkType = "related";

    public Ticket getSourceTicket() {
        return sourceTicket;
    }

    public void setSourceTicket(Ticket sourceTicket) {
        this.sourceTicket = sourceTicket;
    }

    public Ticket getTargetTicket() {
        return targetTicket;
    }

    public void setTargetTicket(Ticket targetTicket) {
        this.targetTicket = targetTicket;
    }

    public String getLinkType() {
        return linkType;
    }

    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }
}
