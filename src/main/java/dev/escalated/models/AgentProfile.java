package dev.escalated.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "escalated_agent_profiles")
public class AgentProfile extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    // Optional link to the host app's user. Stored as a string so hosts with
    // UUID/string user primary keys (not just integer ids) can be linked.
    @Column(name = "user_id")
    private String userId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "is_admin", nullable = false)
    private boolean admin = false;

    @Column(name = "is_agent", nullable = false)
    private boolean agent = true;

    @Column
    private String avatar;

    @Column
    private String phone;

    @Column
    private String signature;

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @OneToOne(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    private AgentCapacity capacity;

    @OneToMany(mappedBy = "assignedAgent")
    private List<Ticket> assignedTickets = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isAgent() {
        return agent;
    }

    public void setAgent(boolean agent) {
        this.agent = agent;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public void setTwoFactorSecret(String twoFactorSecret) {
        this.twoFactorSecret = twoFactorSecret;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public void setTwoFactorEnabled(boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public AgentCapacity getCapacity() {
        return capacity;
    }

    public void setCapacity(AgentCapacity capacity) {
        this.capacity = capacity;
    }

    public List<Ticket> getAssignedTickets() {
        return assignedTickets;
    }

    public void setAssignedTickets(List<Ticket> assignedTickets) {
        this.assignedTickets = assignedTickets;
    }
}
