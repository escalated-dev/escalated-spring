package dev.escalated.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "escalated_webhooks")
public class Webhook extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String url;

    @Column
    private String secret;

    @Column(name = "events", columnDefinition = "TEXT", nullable = false)
    private String events;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @OneToMany(mappedBy = "webhook", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WebhookDelivery> deliveries = new ArrayList<>();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getEvents() {
        return events;
    }

    public void setEvents(String events) {
        this.events = events;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public List<WebhookDelivery> getDeliveries() {
        return deliveries;
    }

    public void setDeliveries(List<WebhookDelivery> deliveries) {
        this.deliveries = deliveries;
    }
}
