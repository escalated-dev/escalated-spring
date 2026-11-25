package dev.escalated.controllers.widget;

import dev.escalated.config.GuestRoutesCondition;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mailbox proof and tracking lookup on the guest prefix. Mounted only while
 * the guest routes are ({@code escalated.guest.routes-enabled}, see
 * {@link GuestRoutesCondition}).
 */
@RestController
@Conditional(GuestRoutesCondition.class)
public class GuestVerificationController {

    private final GuestVerificationEndpoints endpoints;

    public GuestVerificationController(GuestVerificationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * {@code {email, purpose}} where purpose is {@code ticket}, {@code chat}
     * or {@code lookup}. Answers 202 with the id; the code is only emailed.
     */
    @PostMapping("/escalated/api/guest/verification")
    @GuestThrottle(GuestThrottle.Scope.REPLY)
    public ResponseEntity<Map<String, Object>> verification(@RequestBody(required = false) Map<String, Object> body,
                                                            HttpServletRequest request) {
        return endpoints.verification(body, request);
    }

    /**
     * {@code {email, reference, verification_id, verification_code}} with a
     * {@code lookup} proof. Answers {@code {data: [{reference, subject,
     * guest_access_token, expires_at}]}}: at most 20, possibly none.
     */
    @PostMapping("/escalated/api/guest/lookup")
    @GuestThrottle(GuestThrottle.Scope.REPLY)
    public ResponseEntity<Map<String, Object>> lookup(@RequestBody(required = false) Map<String, Object> body) {
        return endpoints.lookup(body);
    }
}
