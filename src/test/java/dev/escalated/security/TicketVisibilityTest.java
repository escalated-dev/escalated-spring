package dev.escalated.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Attachment;
import dev.escalated.models.Department;
import dev.escalated.models.DepartmentAgent;
import dev.escalated.models.Reply;
import dev.escalated.models.SideConversation;
import dev.escalated.models.SideConversationReply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketLink;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AttachmentRepository;
import dev.escalated.repositories.DepartmentAgentRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.SideConversationRepository;
import dev.escalated.repositories.TicketLinkRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.AdvancedReportingService;
import dev.escalated.services.TicketSearch;
import dev.escalated.services.TicketService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@SpringBootTest(classes = hostapp.HostApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
@Import(TicketVisibilityTest.Accounts.class)
@Transactional
class TicketVisibilityTest {
    @Autowired TicketRepository tickets;
    @Autowired DepartmentRepository departments;
    @Autowired DepartmentAgentRepository memberships;
    @Autowired AgentProfileRepository agents;
    @Autowired ReplyRepository replies;
    @Autowired TicketLinkRepository links;
    @Autowired SideConversationRepository conversations;
    @Autowired AttachmentRepository attachments;
    @Autowired TicketService service;
    @Autowired AdvancedReportingService reports;
    @Autowired TicketSearch search;
    @Autowired TicketAccess access;
    @Autowired EscalatedProperties properties;
    @Autowired EntityManager em;
    Department own;
    Department other;
    AgentProfile agent;
    Ticket visible;
    Ticket hidden;
    Authentication actor;

    @TestConfiguration
    static class Accounts {
        @Bean
        TicketAccountResolver accountResolver() {
            return new TicketAccountResolver() {
                public TicketAccount forAgent(AgentProfile agent) {
                    return agent.getUserId() == null
                            ? null
                            : new TicketAccount("Company", agent.getUserId());
                }

                public TicketAccount forRequester(Ticket ticket) {
                    return ticket.getRequesterEmail().startsWith("account-")
                            ? new TicketAccount("Company", "007")
                            : null;
                }

                public TicketAccount forSubject(Ticket ticket, String type, String id) {
                    return "Company".equals(type) ? new TicketAccount(type, id) : null;
                }
            };
        }
    }

    @BeforeEach
    void seed() {
        properties.setVisibility("department");
        own = department("Support");
        other = department("Delivery");
        agent = new AgentProfile();
        agent.setName("Agent");
        agent.setEmail(UUID.randomUUID() + "@example.test");
        agent.setDepartment(own);
        agent.setUserId("007");
        agents.save(agent);
        actor = new UsernamePasswordAuthenticationToken(agent.getEmail(), "", List.of());
        visible = ticket(own, "Visible", "Company", "007");
        hidden = ticket(other, "Hidden", "Company", "007");
        ticket(null, "No department", null, null);
        em.flush();
    }

    @AfterEach
    void reset() {
        properties.setVisibility("all");
        properties.getAccounts().setEnabled(false);
        properties.getAccounts().setScopeAgents(false);
        properties.setTargetedSlas(false);
        TicketAccessContext.CURRENT.remove();
    }

    Department department(String name) {
        Department department = new Department();
        department.setName(name);
        return departments.save(department);
    }

    Ticket ticket(Department department, String subject, String type, String id) {
        Ticket ticket = new Ticket();
        ticket.setSubject(subject);
        ticket.setBody("needle");
        ticket.setDepartment(department);
        ticket.setAccountType(type);
        ticket.setAccountId(id);
        ticket.setTicketNumber(UUID.randomUUID().toString());
        ticket.setRequesterName("Customer");
        ticket.setRequesterEmail("customer@example.test");
        return tickets.saveAndFlush(ticket);
    }

    <T> T asAgent(Supplier<T> action) {
        return access.runAs(actor, action);
    }

    Reply reply(Ticket ticket) {
        Reply reply = new Reply();
        reply.setTicket(ticket);
        reply.setBody("secret");
        reply.setAuthorName("Agent");
        reply.setAuthorEmail("agent@example.test");
        return replies.saveAndFlush(reply);
    }

    @Test
    void repositoryFindChecksEvenPreviouslyLoadedTickets() {
        assertThat(asAgent(() -> tickets.findById(hidden.getId()))).isEmpty();
        assertThat(asAgent(() -> tickets.findAll()))
                .extracting(Ticket::getId)
                .containsExactly(visible.getId());
    }

    @Test
    void reportsUseTheSameScope() {
        assertThat(asAgent(() -> reports.overview(30).totalTickets())).isEqualTo(1);
    }

    @Test
    void searchAndChosenDepartmentCannotExpandScope() {
        assertThat(
                        asAgent(
                                        () ->
                                                search.find(
                                                        "needle",
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        PageRequest.of(0, 1)))
                                .getTotalElements())
                .isEqualTo(1);
        assertThat(
                        asAgent(
                                        () ->
                                                search.find(
                                                        null,
                                                        null,
                                                        null,
                                                        other.getId(),
                                                        null,
                                                        null,
                                                        null,
                                                        PageRequest.of(0, 10)))
                                .getTotalElements())
                .isZero();
    }

    @Test
    void bulkUpdateScopesRowsBeforeMutation() {
        assertThat(
                        asAgent(
                                () ->
                                        em.createQuery("UPDATE Ticket t SET t.priority = :priority")
                                                .setParameter("priority", TicketPriority.HIGH)
                                                .executeUpdate()))
                .isEqualTo(1);
        em.clear();
        assertThat(tickets.findById(hidden.getId()).orElseThrow().getPriority())
                .isEqualTo(TicketPriority.MEDIUM);
    }

    @Test
    void bulkDeleteScopesRowsBeforeMutation() {
        assertThat(asAgent(() -> em.createQuery("DELETE FROM Ticket t").executeUpdate()))
                .isEqualTo(1);
        em.clear();
        assertThat(tickets.findById(hidden.getId())).isPresent();
    }

    @Test
    void serviceUpdateRejectsHiddenTicket() {
        assertThatThrownBy(
                        () -> asAgent(() -> service.update(hidden.getId(), "stolen", null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void serviceCloseRejectsHiddenTicket() {
        assertThatThrownBy(
                        () ->
                                asAgent(
                                        () ->
                                                service.changeStatus(
                                                        hidden.getId(),
                                                        TicketStatus.CLOSED,
                                                        agent.getEmail())))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void assignmentRejectsHiddenTicket() {
        assertThatThrownBy(
                        () ->
                                asAgent(
                                        () ->
                                                service.assign(
                                                        hidden.getId(),
                                                        agent.getId(),
                                                        agent.getEmail())))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void directChildrenAreScoped() {
        Reply allowed = reply(visible);
        Reply denied = reply(hidden);
        em.clear();
        assertThat(asAgent(() -> replies.findAll()))
                .extracting(Reply::getId)
                .containsExactly(allowed.getId());
        assertThat(asAgent(() -> replies.findById(denied.getId()))).isEmpty();
    }

    @Test
    void linksRequireBothEndpointsAndCollectionLoadsAreScoped() {
        TicketLink link = new TicketLink();
        link.setSourceTicket(visible);
        link.setTargetTicket(hidden);
        links.saveAndFlush(link);
        em.clear();
        assertThat(asAgent(() -> links.findAll())).isEmpty();
        assertThat(
                        asAgent(
                                () ->
                                        tickets.findById(visible.getId())
                                                .orElseThrow()
                                                .getOutgoingLinks()
                                                .size()))
                .isZero();
    }

    @Test
    void previouslyLoadedLinksAreFilteredWhenTheActorChanges() {
        TicketLink link = new TicketLink();
        link.setSourceTicket(visible);
        link.setTargetTicket(hidden);
        links.saveAndFlush(link);
        em.clear();
        Ticket loaded = tickets.findById(visible.getId()).orElseThrow();
        assertThat(loaded.getOutgoingLinks()).hasSize(1);
        assertThat(asAgent(() -> loaded.getOutgoingLinks())).isEmpty();
    }

    @Test
    void attachmentsRequireAllPresentParents() {
        Reply hiddenReply = reply(hidden);
        Attachment attachment = new Attachment();
        attachment.setTicket(visible);
        attachment.setReply(hiddenReply);
        attachment.setFileName("secret.txt");
        attachment.setFilePath("secret");
        attachments.saveAndFlush(attachment);
        em.clear();
        assertThat(asAgent(() -> attachments.findAll())).isEmpty();
    }

    @Test
    void sideConversationRepliesCannotBeReadDirectly() {
        SideConversation conversation = new SideConversation();
        conversation.setTicket(hidden);
        conversation.setSubject("secret");
        conversations.save(conversation);
        SideConversationReply reply = new SideConversationReply();
        reply.setSideConversation(conversation);
        reply.setBody("secret");
        reply.setAuthorName("Agent");
        reply.setAuthorEmail("agent@example.test");
        em.persist(reply);
        em.flush();
        em.clear();
        assertThat(
                        asAgent(
                                () ->
                                        em.createQuery(
                                                        "FROM SideConversationReply",
                                                        SideConversationReply.class)
                                                .getResultList()))
                .isEmpty();
    }

    @Test
    void preloadedWritesStillCheckOriginalIdentity() {
        hidden.setDepartment(own);
        hidden.setSubject("reparented");
        assertThatThrownBy(() -> asAgent(() -> tickets.saveAndFlush(hidden)))
                .hasRootCauseInstanceOf(EntityNotFoundException.class);
        em.clear();
    }

    @Test
    void preloadedChildCannotBeMovedOutOfHiddenTicket() {
        Reply denied = reply(hidden);
        denied.setTicket(visible);
        assertThatThrownBy(() -> asAgent(() -> replies.saveAndFlush(denied)))
                .hasRootCauseInstanceOf(EntityNotFoundException.class);
        em.clear();
    }

    @Test
    void missingDepartmentIsHiddenAndAdditionalMembershipsAreHonored() {
        memberships.saveAndFlush(new DepartmentAgent(other.getId(), agent.getId()));
        assertThat(asAgent(() -> tickets.count())).isEqualTo(2);
        agent.setDepartment(null);
        agents.saveAndFlush(agent);
        assertThat(asAgent(() -> tickets.findAll()))
                .extracting(Ticket::getId)
                .containsExactly(hidden.getId());
    }

    @Test
    void accountScopeIntersectsDepartmentsAndPreservesOpaqueIds() {
        properties.getAccounts().setEnabled(true);
        properties.getAccounts().setScopeAgents(true);
        ticket(own, "numeric", "Company", "7");
        ticket(own, "case", "company", "007");
        ticket(own, "space", "Company", "007 ");
        ticket(own, "missing", null, null);
        assertThat(asAgent(() -> tickets.findAll()))
                .extracting(Ticket::getId)
                .containsExactly(visible.getId());
        assertThat(
                        asAgent(
                                        () ->
                                                search.find(
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        "Company",
                                                        "007",
                                                        PageRequest.of(0, 10)))
                                .getTotalElements())
                .isEqualTo(1);
    }

    @Test
    void accountScopeWorksWithoutDepartmentModeAndMissingResolverFailsClosed() {
        properties.setVisibility("all");
        properties.getAccounts().setEnabled(true);
        properties.getAccounts().setScopeAgents(true);
        assertThat(asAgent(() -> tickets.count())).isEqualTo(2);
        agent.setUserId(null);
        agents.saveAndFlush(agent);
        assertThat(asAgent(() -> tickets.count())).isZero();
    }

    @Test
    void administratorAndActorlessJobsRetainFullAccess() {
        Authentication admin =
                new UsernamePasswordAuthenticationToken(
                        "admin@example.test",
                        "",
                        List.of(new SimpleGrantedAuthority("ROLE_ESCALATED_ADMIN")));
        assertThat(access.runAs(admin, () -> tickets.count())).isEqualTo(3);
        assertThat(tickets.count()).isEqualTo(3);
    }

    @Test
    void defaultsRetainExistingAccessAndNestedContextsRestoreAfterExceptions() {
        properties.setVisibility("all");
        assertThat(asAgent(() -> tickets.count())).isEqualTo(3);
        properties.setVisibility("department");
        asAgent(
                () -> {
                    assertThatThrownBy(
                                    () ->
                                            access.withoutRestriction(
                                                    () -> {
                                                        throw new IllegalStateException("probe");
                                                    }))
                            .isInstanceOf(IllegalStateException.class);
                    assertThat(tickets.count()).isEqualTo(1);
                    return null;
                });
        assertThat(tickets.count()).isEqualTo(3);
    }

    @Test
    void assignmentAndNotificationRecipientsRespectRevokedMembership() {
        assertThat(access.canAssign(visible, agent)).isTrue();
        assertThat(access.canAssign(hidden, agent)).isFalse();
        assertThat(access.canReceiveStaff(visible.getId(), agent.getId())).isTrue();
        agent.setDepartment(other);
        agents.saveAndFlush(agent);
        assertThat(access.canReceiveStaff(visible.getId(), agent.getId())).isFalse();
    }

    @Test
    void notificationsReadCurrentTicketIdentityEvenWithAnOldEntityLoaded() {
        em.createQuery("UPDATE Ticket t SET t.department = :department WHERE t.id = :id")
                .setParameter("department", other)
                .setParameter("id", visible.getId())
                .executeUpdate();
        assertThat(visible.getDepartment().getId()).isEqualTo(own.getId());
        assertThat(access.canReceiveStaff(visible.getId(), agent.getId())).isFalse();
    }

    @Test
    void notificationsReadCurrentRecipientEvenWithAnOldProfileLoaded() {
        em.createQuery("UPDATE AgentProfile a SET a.department = :department WHERE a.id = :id")
                .setParameter("department", other)
                .setParameter("id", agent.getId())
                .executeUpdate();
        assertThat(agent.getDepartment().getId()).isEqualTo(own.getId());
        assertThat(access.canReceiveStaff(visible.getId(), agent.getId())).isFalse();
    }

    @Test
    void aRecipientCheckCannotFlushUnauthorizedPendingChanges() {
        hidden.setSubject("unauthorized change");
        asAgent(
                () -> {
                    assertThat(access.canReceiveStaff(visible.getId(), agent.getId())).isTrue();
                    return null;
                });
        em.clear();
        assertThat(tickets.findById(hidden.getId()).orElseThrow().getSubject()).isEqualTo("Hidden");
    }

    @Test
    void movingDepartmentsPreservesSlaAndRestoresActor() {
        var deadline = java.time.Instant.parse("2026-10-15T12:00:00Z");
        visible.setSlaDueAt(deadline);
        tickets.saveAndFlush(visible);
        asAgent(
                () -> {
                    service.changeDepartment(visible.getId(), other.getId(), agent.getEmail());
                    assertThat(tickets.findById(visible.getId())).isEmpty();
                    return null;
                });
        assertThat(tickets.findById(visible.getId()).orElseThrow().getSlaDueAt())
                .isEqualTo(deadline);
    }
}
