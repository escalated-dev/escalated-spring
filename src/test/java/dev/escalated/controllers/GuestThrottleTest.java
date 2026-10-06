package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.controllers.widget.GuestAccessController;
import dev.escalated.controllers.widget.WidgetController;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.security.ApiTokenAuthenticationFilter;
import dev.escalated.services.KnowledgeBaseService;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.TicketService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

/**
 * The guest endpoints are unauthenticated and every accepted request writes
 * rows and sends mail, so the library caps them per client IP (ticket creation
 * 5/min, guest replies 10/min by default) instead of relying on each host to
 * put a limiter in front. Mirrors escalated-dev/escalated-nestjs#130.
 *
 * <p>Each test sends from its own client IP, so the counters one test fills
 * never reach another.
 */
@WebMvcTest(controllers = {WidgetController.class, GuestAccessController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GuestThrottleTest.PropertiesConfig.class)
class GuestThrottleTest {

    @TestConfiguration
    @EnableConfigurationProperties(EscalatedProperties.class)
    static class PropertiesConfig {
    }

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private EscalatedProperties properties;

    @MockitoBean private ApiTokenAuthenticationFilter apiTokenFilter;
    @MockitoBean private TicketService ticketService;
    @MockitoBean private KnowledgeBaseService knowledgeBaseService;
    @MockitoBean private SatisfactionRatingService ratingService;

    private String ip;

    @BeforeEach
    void setUp() {
        ip = "203.0.113." + NEXT_IP.getAndIncrement();

        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setGuestAccessToken("guest-token");
        when(ticketService.create(any(), any(), any(), any(), any(), any())).thenReturn(ticket);
        when(ticketService.findByGuestToken("guest-token")).thenReturn(ticket);
        when(ticketService.findByGuestToken("wrong")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(ticketService.addReply(anyLong(), any(), any(), any(), any(), anyBoolean())).thenReturn(new Reply());
    }

    @AfterEach
    void resetLimits() {
        properties.setGuestRateLimit(new EscalatedProperties.GuestRateLimitProperties());
    }

    @Test
    void sixthGuestTicketFromOneIpWithinAMinute_gets429() throws Exception {
        assertThat(createTickets(6)).containsExactly(201, 201, 201, 201, 201, 429);
        verify(ticketService, times(5)).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectedRequest_carriesRetryAfter() throws Exception {
        properties.getGuestRateLimit().setTicketsPerMinute(1);
        createTickets(1);

        MvcResult result = createTicket(99);

        assertThat(result.getResponse().getStatus()).isEqualTo(429);
        assertThat(Integer.parseInt(result.getResponse().getHeader("Retry-After"))).isBetween(1, 60);
    }

    @Test
    void eleventhGuestReplyFromOneIpWithinAMinute_gets429() throws Exception {
        List<Integer> statuses = replies("/escalated/api/widget", "guest-token", 11);

        assertThat(statuses.subList(0, 10)).containsOnly(201);
        assertThat(statuses.get(10)).isEqualTo(429);
    }

    @Test
    void repliesWithAWrongGuestToken_areCounted() throws Exception {
        properties.getGuestRateLimit().setRepliesPerMinute(2);

        assertThat(replies("/escalated/api/widget", "wrong", 3)).containsExactly(404, 404, 429);
    }

    @Test
    void guestAccessReplies_shareTheReplyLimit() throws Exception {
        properties.getGuestRateLimit().setRepliesPerMinute(2);
        replies("/escalated/api/widget", "guest-token", 1);

        assertThat(replies("/escalated/api/guest", "guest-token", 2)).containsExactly(201, 429);
    }

    @Test
    void ticketsAndRepliesAreCountedSeparately() throws Exception {
        createTickets(5);

        assertThat(replies("/escalated/api/widget", "guest-token", 1)).containsExactly(201);
    }

    @Test
    void eachClientIpIsCountedSeparately() throws Exception {
        properties.getGuestRateLimit().setTicketsPerMinute(1);
        createTickets(1);
        ip = "198.51.100.1";

        assertThat(createTickets(1)).containsExactly(201);
    }

    @Test
    void configuredLimitIsHonoured() throws Exception {
        properties.getGuestRateLimit().setTicketsPerMinute(2);

        assertThat(createTickets(3)).containsExactly(201, 201, 429);
    }

    @Test
    void disabled_neverAnswers429() throws Exception {
        properties.getGuestRateLimit().setEnabled(false);

        assertThat(createTickets(8)).containsOnly(201);
    }

    @Test
    void defaultsMatchTheReference() {
        EscalatedProperties.GuestRateLimitProperties defaults = new EscalatedProperties.GuestRateLimitProperties();

        assertThat(defaults.isEnabled()).isTrue();
        assertThat(defaults.getTicketsPerMinute()).isEqualTo(5);
        assertThat(defaults.getRepliesPerMinute()).isEqualTo(10);
    }

    private MvcResult createTicket(int n) throws Exception {
        // A distinct email per call, so only the per-IP limit can be what trips.
        return mockMvc.perform(post("/escalated/api/widget/tickets")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Help\",\"body\":\"d\",\"email\":\"guest" + n + "@example.com\"}"))
                .andReturn();
    }

    private List<Integer> createTickets(int times) throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            statuses.add(createTicket(i).getResponse().getStatus());
        }
        return statuses;
    }

    private List<Integer> replies(String base, String token, int times) throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            statuses.add(mockMvc.perform(post(base + "/tickets/" + token + "/replies")
                            .with(request -> {
                                request.setRemoteAddr(ip);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\":\"hi\"}"))
                    .andReturn().getResponse().getStatus());
        }
        return statuses;
    }
}
