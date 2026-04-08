package dev.escalated.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "escalated_side_conversations")
public class SideConversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false)
    private String subject;

    @Column(name = "participant_emails", columnDefinition = "TEXT")
    private String participantEmails;

    @Column(nullable = false)
    private String status = "open";

    @OneToMany(mappedBy = "sideConversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SideConversationReply> replies = new ArrayList<>();

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getParticipantEmails() {
        return participantEmails;
    }

    public void setParticipantEmails(String participantEmails) {
        this.participantEmails = participantEmails;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<SideConversationReply> getReplies() {
        return replies;
    }

    public void setReplies(List<SideConversationReply> replies) {
        this.replies = replies;
    }
}
