package dev.escalated.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "escalated_tickets")
public class Ticket extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Column(name = "ticket_number", unique = true, nullable = false)
    private String ticketNumber;

    @Column(name = "requester_name", nullable = false)
    private String requesterName;

    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;

    // First-class Contact FK (Pattern B). Column added by V2 migration.
    // Populated for public submissions so repeat guests are deduped by email.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private AgentProfile assignedAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id")
    private SlaPolicy slaPolicy;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "sla_first_response_due_at")
    private Instant slaFirstResponseDueAt;

    @Column(name = "first_responded_at")
    private Instant firstRespondedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "snoozed_until")
    private Instant snoozedUntil;

    @Column(name = "merged_into_ticket_id")
    private Long mergedIntoTicketId;

    @Column(name = "email_message_id")
    private String emailMessageId;

    @Column(name = "guest_access_token")
    private String guestAccessToken;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel = "email";

    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reply> replies = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketActivity> activities = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomFieldValue> customFieldValues = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SideConversation> sideConversations = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "escalated_ticket_tags",
            joinColumns = @JoinColumn(name = "ticket_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "sourceTicket", cascade = CascadeType.ALL)
    private List<TicketLink> outgoingLinks = new ArrayList<>();

    @OneToMany(mappedBy = "targetTicket", cascade = CascadeType.ALL)
    private List<TicketLink> incomingLinks = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
    private List<SatisfactionRating> satisfactionRatings = new ArrayList<>();

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    @JsonProperty("requester_name")
    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    @JsonProperty("requester_email")
    public String getRequesterEmail() {
        return requesterEmail;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public AgentProfile getAssignedAgent() {
        return assignedAgent;
    }

    public void setAssignedAgent(AgentProfile assignedAgent) {
        this.assignedAgent = assignedAgent;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public SlaPolicy getSlaPolicy() {
        return slaPolicy;
    }

    public void setSlaPolicy(SlaPolicy slaPolicy) {
        this.slaPolicy = slaPolicy;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public void setSlaDueAt(Instant slaDueAt) {
        this.slaDueAt = slaDueAt;
    }

    public Instant getSlaFirstResponseDueAt() {
        return slaFirstResponseDueAt;
    }

    public void setSlaFirstResponseDueAt(Instant slaFirstResponseDueAt) {
        this.slaFirstResponseDueAt = slaFirstResponseDueAt;
    }

    public Instant getFirstRespondedAt() {
        return firstRespondedAt;
    }

    public void setFirstRespondedAt(Instant firstRespondedAt) {
        this.firstRespondedAt = firstRespondedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getSnoozedUntil() {
        return snoozedUntil;
    }

    public void setSnoozedUntil(Instant snoozedUntil) {
        this.snoozedUntil = snoozedUntil;
    }

    public Long getMergedIntoTicketId() {
        return mergedIntoTicketId;
    }

    public void setMergedIntoTicketId(Long mergedIntoTicketId) {
        this.mergedIntoTicketId = mergedIntoTicketId;
    }

    public String getEmailMessageId() {
        return emailMessageId;
    }

    public void setEmailMessageId(String emailMessageId) {
        this.emailMessageId = emailMessageId;
    }

    public String getGuestAccessToken() {
        return guestAccessToken;
    }

    public void setGuestAccessToken(String guestAccessToken) {
        this.guestAccessToken = guestAccessToken;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public List<Reply> getReplies() {
        return replies;
    }

    public void setReplies(List<Reply> replies) {
        this.replies = replies;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public List<TicketActivity> getActivities() {
        return activities;
    }

    public void setActivities(List<TicketActivity> activities) {
        this.activities = activities;
    }

    public List<CustomFieldValue> getCustomFieldValues() {
        return customFieldValues;
    }

    public void setCustomFieldValues(List<CustomFieldValue> customFieldValues) {
        this.customFieldValues = customFieldValues;
    }

    public List<SideConversation> getSideConversations() {
        return sideConversations;
    }

    public void setSideConversations(List<SideConversation> sideConversations) {
        this.sideConversations = sideConversations;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public List<TicketLink> getOutgoingLinks() {
        return outgoingLinks;
    }

    public void setOutgoingLinks(List<TicketLink> outgoingLinks) {
        this.outgoingLinks = outgoingLinks;
    }

    public List<TicketLink> getIncomingLinks() {
        return incomingLinks;
    }

    public void setIncomingLinks(List<TicketLink> incomingLinks) {
        this.incomingLinks = incomingLinks;
    }

    public List<SatisfactionRating> getSatisfactionRatings() {
        return satisfactionRatings;
    }

    public void setSatisfactionRatings(List<SatisfactionRating> satisfactionRatings) {
        this.satisfactionRatings = satisfactionRatings;
    }

    // --- Computed JSON properties expected by the frontend ---

    @JsonProperty("last_reply_at")
    public Instant getLastReplyAt() {
        if (replies == null || replies.isEmpty()) {
            return null;
        }
        return replies.stream()
                .map(Reply::getCreatedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    @JsonProperty("last_reply_author")
    public String getLastReplyAuthor() {
        if (replies == null || replies.isEmpty()) {
            return null;
        }
        return replies.stream()
                .max(Comparator.comparing(Reply::getCreatedAt))
                .map(Reply::getAuthorName)
                .orElse(null);
    }

    @JsonProperty("is_live_chat")
    public boolean isLiveChat() {
        return "chat".equals(channel);
    }

    @JsonProperty("is_snoozed")
    public boolean isSnoozed() {
        return snoozedUntil != null && snoozedUntil.isAfter(Instant.now());
    }
}
