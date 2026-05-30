package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.contracts.TicketSubject;
import dev.escalated.dto.SerializedTicketSubjectDto;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketSubjectLink;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.repositories.TicketSubjectLinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class TicketSubjectServiceTest {

    private static final String SUBJECT_TYPE = "com.example.FakeProject";

    @Mock
    private TicketSubjectLinkRepository linkRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private ObjectProvider<TicketSubjectResolver> resolverProvider;

    private EscalatedProperties properties;
    private TicketSubjectService service;

    private final Ticket ticket = new Ticket();

    @BeforeEach
    void setUp() {
        ticket.setId(1L);
        properties = new EscalatedProperties();
        properties.getTicketSubjects().setTypes(List.of(SUBJECT_TYPE));
        service = new TicketSubjectService(linkRepository, ticketRepository, properties, resolverProvider);
    }

    @Test
    void attach_preservesStringIdAndRole() {
        when(linkRepository.findByTicketIdAndSubjectTypeAndSubjectId(1L, SUBJECT_TYPE, "prj_9f1c"))
                .thenReturn(Optional.empty());
        when(linkRepository.findMaxPositionByTicketId(1L)).thenReturn(-1);
        when(linkRepository.save(any(TicketSubjectLink.class))).thenAnswer(inv -> {
            TicketSubjectLink link = inv.getArgument(0);
            link.setId(10L);
            return link;
        });

        TicketSubjectLink link = service.attach(ticket, SUBJECT_TYPE, "prj_9f1c", "project");

        assertThat(link.getSubjectType()).isEqualTo(SUBJECT_TYPE);
        assertThat(link.getSubjectId()).isEqualTo("prj_9f1c");
        assertThat(link.getRole()).isEqualTo("project");
        assertThat(link.getPosition()).isEqualTo(0);
    }

    @Test
    void attach_isIdempotentAndUpdatesRole() {
        TicketSubjectLink existing = new TicketSubjectLink();
        existing.setId(5L);
        existing.setSubjectType(SUBJECT_TYPE);
        existing.setSubjectId("p1");
        existing.setRole(null);
        when(linkRepository.findByTicketIdAndSubjectTypeAndSubjectId(1L, SUBJECT_TYPE, "p1"))
                .thenReturn(Optional.of(existing));
        when(linkRepository.save(existing)).thenReturn(existing);

        TicketSubjectLink link = service.attach(ticket, SUBJECT_TYPE, "p1", "account");

        assertThat(link.getRole()).isEqualTo("account");
        verify(linkRepository).save(existing);
    }

    @Test
    void attach_rejectsTypeOutsideAllowlist() {
        assertThatThrownBy(() -> service.attach(ticket, "com.example.Other", "1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an allowed ticket subject");
    }

    @Test
    void attach_allowsAnyTypeWhenAllowlistEmpty() {
        properties.getTicketSubjects().setTypes(List.of());
        when(linkRepository.findByTicketIdAndSubjectTypeAndSubjectId(1L, "any", "1"))
                .thenReturn(Optional.empty());
        when(linkRepository.findMaxPositionByTicketId(1L)).thenReturn(0);
        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.attach(ticket, "any", 1, null);
    }

    @Test
    void sync_replacesSubjectsInOrder() {
        properties.getTicketSubjects().setTypes(List.of(SUBJECT_TYPE));
        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<TicketSubjectLink> links = service.sync(ticket, List.of(
                new TicketSubjectService.SyncItem(SUBJECT_TYPE, "b", "primary"),
                new TicketSubjectService.SyncItem(SUBJECT_TYPE, "c", null)));

        verify(linkRepository).deleteByTicketId(1L);
        assertThat(links).hasSize(2);
        assertThat(links.get(0).getSubjectId()).isEqualTo("b");
        assertThat(links.get(0).getRole()).isEqualTo("primary");
        assertThat(links.get(0).getPosition()).isZero();
        assertThat(links.get(1).getSubjectId()).isEqualTo("c");
        assertThat(links.get(1).getPosition()).isEqualTo(1);
    }

    @Test
    void serialize_usesContractWhenResolverPresent() {
        TicketSubjectLink link = new TicketSubjectLink();
        link.setSubjectType(SUBJECT_TYPE);
        link.setSubjectId("7");
        link.setRole("project");

        when(resolverProvider.getIfAvailable()).thenReturn((type, id) -> new TicketSubject() {
            @Override
            public String ticketSubjectTitle() {
                return "Acme Redesign";
            }

            @Override
            public String ticketSubjectSubtitle() {
                return "Project · Acme";
            }

            @Override
            public String ticketSubjectUrl() {
                return "https://app.test/projects/7";
            }

            @Override
            public String ticketSubjectColor() {
                return "#2563eb";
            }

            @Override
            public String ticketSubjectIcon() {
                return "folder";
            }
        });

        List<SerializedTicketSubjectDto> serialized = service.serializeLinks(List.of(link));

        assertThat(serialized).hasSize(1);
        assertThat(serialized.get(0).getTitle()).isEqualTo("Acme Redesign");
        assertThat(serialized.get(0).getSubtitle()).isEqualTo("Project · Acme");
        assertThat(serialized.get(0).getUrl()).isEqualTo("https://app.test/projects/7");
        assertThat(serialized.get(0).getColor()).isEqualTo("#2563eb");
        assertThat(serialized.get(0).getIcon()).isEqualTo("folder");
        assertThat(serialized.get(0).isMissing()).isFalse();
    }

    @Test
    void serialize_fallsBackWhenResolverMissing() {
        when(resolverProvider.getIfAvailable()).thenReturn(null);

        TicketSubjectLink link = new TicketSubjectLink();
        link.setSubjectType(SUBJECT_TYPE);
        link.setSubjectId("99");

        List<SerializedTicketSubjectDto> serialized = service.serializeLinks(List.of(link));

        assertThat(serialized.get(0).getTitle()).isEqualTo(SUBJECT_TYPE + "#99");
        assertThat(serialized.get(0).isMissing()).isTrue();
    }

    @Test
    void detach_deletesLinkForTicket() {
        TicketSubjectLink link = new TicketSubjectLink();
        link.setId(3L);
        when(linkRepository.findByIdAndTicketId(3L, 1L)).thenReturn(Optional.of(link));

        service.detach(ticket, 3L);

        verify(linkRepository).delete(link);
    }

    @Test
    void isApiTypeAllowed_requiresNonEmptyAllowlist() {
        assertThat(service.isApiTypeAllowed(SUBJECT_TYPE)).isTrue();
        assertThat(service.isApiTypeAllowed("other")).isFalse();

        properties.getTicketSubjects().setTypes(List.of());
        assertThat(service.isApiTypeAllowed(SUBJECT_TYPE)).isFalse();
    }
}
