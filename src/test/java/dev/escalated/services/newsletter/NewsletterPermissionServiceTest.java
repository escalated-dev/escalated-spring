package dev.escalated.services.newsletter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.security.AccessControl;
import dev.escalated.security.AccessFeatures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/**
 * The newsletter screens' permission checks follow the roles switch (ADR
 * 2026-10-29-configurable-access-features): off, the admin gate alone decides; on, the role must
 * grant {@code newsletters.manage} / {@code newsletters.send}.
 */
class NewsletterPermissionServiceTest {

    private final EscalatedProperties properties = new EscalatedProperties();
    private final AgentProfileRepository agents = mock(AgentProfileRepository.class);
    private final NewsletterPermissionService service = new NewsletterPermissionService(
            new AccessControl(new AccessFeatures(properties, new MockEnvironment()), agents));
    private final Authentication manager =
            UsernamePasswordAuthenticationToken.authenticated("manager@example.com", null, List.of());

    @Test
    void offAnyAdminMayManageAndSend() {
        assertThatCode(() -> service.require(manager, "newsletters.send")).doesNotThrowAnyException();
    }

    @Test
    void onTheRoleMustGrantThePermission() {
        properties.getAccess().setRolesPermissions(true);
        when(agents.findPermissionNamesByEmail("manager@example.com")).thenReturn(List.of("newsletters.manage"));
        assertThatCode(() -> service.require(manager, "newsletters.manage")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.require(manager, "newsletters.send"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
