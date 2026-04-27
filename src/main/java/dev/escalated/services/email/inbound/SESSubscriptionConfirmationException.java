package dev.escalated.services.email.inbound;

/**
 * Thrown by {@link SESInboundParser#parse} when the webhook receives
 * an SNS subscription-confirmation envelope. The host app must fetch
 * {@link #getSubscribeUrl} out-of-band to activate the subscription;
 * the inbound controller catches this as a 202-Accepted sentinel so
 * AWS stops retrying the confirmation POST.
 */
public class SESSubscriptionConfirmationException extends RuntimeException {

    private final String topicArn;
    private final String subscribeUrl;
    private final String token;

    public SESSubscriptionConfirmationException(String topicArn, String subscribeUrl, String token) {
        super("SES subscription confirmation for topic " + topicArn
                + "; GET " + subscribeUrl + " to confirm");
        this.topicArn = topicArn;
        this.subscribeUrl = subscribeUrl;
        this.token = token;
    }

    public String getTopicArn() {
        return topicArn;
    }

    public String getSubscribeUrl() {
        return subscribeUrl;
    }

    public String getToken() {
        return token;
    }
}
