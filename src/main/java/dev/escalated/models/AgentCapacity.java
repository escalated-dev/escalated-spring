package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "escalated_agent_capacities")
public class AgentCapacity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false, unique = true)
    private AgentProfile agent;

    @Column(name = "max_tickets", nullable = false)
    private int maxTickets = 20;

    @Column(name = "current_tickets", nullable = false)
    private int currentTickets = 0;

    @Column(name = "weight", nullable = false)
    private int weight = 1;

    public AgentProfile getAgent() {
        return agent;
    }

    public void setAgent(AgentProfile agent) {
        this.agent = agent;
    }

    public int getMaxTickets() {
        return maxTickets;
    }

    public void setMaxTickets(int maxTickets) {
        this.maxTickets = maxTickets;
    }

    public int getCurrentTickets() {
        return currentTickets;
    }

    public void setCurrentTickets(int currentTickets) {
        this.currentTickets = currentTickets;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean hasCapacity() {
        return currentTickets < maxTickets;
    }

    public double getUtilization() {
        if (maxTickets == 0) {
            return 1.0;
        }
        return (double) currentTickets / maxTickets;
    }
}
