package dev.escalated.controllers.widget;

import dev.escalated.config.WidgetRoutesCondition;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mailbox proof and tracking lookup on the widget prefix. Mounted only while
 * the widget is ({@code escalated.widget.enabled}, see
 * {@link WidgetRoutesCondition}).
 */
@RestController
@Conditional(WidgetRoutesCondition.class)
public class WidgetVerificationController {

    private final GuestVerificationEndpoints endpoints;

    public WidgetVerificationController(GuestVerificationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * {@code {email, purpose}} where purpose is {@code ticket}, {@code chat}
     * or {@code lookup}. Answers 202 with the id; the code is only emailed.
     */
    @PostMapping("/escalated/api/widget/verification")
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
    @PostMapping("/escalated/api/widget/lookup")
    @GuestThrottle(GuestThrottle.Scope.REPLY)
    public ResponseEntity<Map<String, Object>> lookup(@RequestBody(required = false) Map<String, Object> body) {
        return endpoints.lookup(body);
    }
}
