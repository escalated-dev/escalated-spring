package dev.escalated.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "escalated_sla_policies")
public class SlaPolicy extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Column(name = "first_response_minutes", nullable = false)
    private int firstResponseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_schedule_id")
    private BusinessSchedule businessSchedule;

    @OneToMany(mappedBy = "slaPolicy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EscalationRule> escalationRules = new ArrayList<>();

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

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public int getFirstResponseMinutes() {
        return firstResponseMinutes;
    }

    public void setFirstResponseMinutes(int firstResponseMinutes) {
        this.firstResponseMinutes = firstResponseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    public void setResolutionMinutes(int resolutionMinutes) {
        this.resolutionMinutes = resolutionMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BusinessSchedule getBusinessSchedule() {
        return businessSchedule;
    }

    public void setBusinessSchedule(BusinessSchedule businessSchedule) {
        this.businessSchedule = businessSchedule;
    }

    public List<EscalationRule> getEscalationRules() {
        return escalationRules;
    }

    public void setEscalationRules(List<EscalationRule> escalationRules) {
        this.escalationRules = escalationRules;
    }
}
