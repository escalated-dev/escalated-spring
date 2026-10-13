package dev.escalated.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "escalated_departments")
public class Department extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @JsonIgnore
    @OneToMany(mappedBy = "department")
    @org.hibernate.annotations.Filter(
            name = "escalatedTicketAccess",
            condition = dev.escalated.security.TicketAccessSql.TICKET_STANDARD,
            deduceAliasInjectionPoints = false,
            aliases =
                    @org.hibernate.annotations.SqlFragmentAlias(
                            alias = "row",
                            table = "escalated_tickets"))
    @org.hibernate.annotations.DialectOverride.Filters(
            dialect = org.hibernate.dialect.MySQLDialect.class,
            override =
                    @org.hibernate.annotations.Filters(
                            @org.hibernate.annotations.Filter(
                                    name = "escalatedTicketAccess",
                                    condition = dev.escalated.security.TicketAccessSql.TICKET_MYSQL,
                                    deduceAliasInjectionPoints = false,
                                    aliases =
                                            @org.hibernate.annotations.SqlFragmentAlias(
                                                    alias = "row",
                                                    table = "escalated_tickets"))))
    private List<Ticket> tickets = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "department")
    private List<AgentProfile> agents = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public List<Ticket> getTickets() {
        return tickets;
    }

    public void setTickets(List<Ticket> tickets) {
        this.tickets = tickets;
    }

    public List<AgentProfile> getAgents() {
        return agents;
    }

    public void setAgents(List<AgentProfile> agents) {
        this.agents = agents;
    }
}
