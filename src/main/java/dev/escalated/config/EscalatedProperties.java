package dev.escalated.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "escalated")
public class EscalatedProperties {

    private boolean enabled = true;
    private String routePrefix = "escalated";
    private KnowledgeBaseProperties knowledgeBase = new KnowledgeBaseProperties();
    private BroadcastingProperties broadcasting = new BroadcastingProperties();
    private TwoFactorProperties twoFactor = new TwoFactorProperties();
    private SlaProperties sla = new SlaProperties();
    private SnoozeProperties snooze = new SnoozeProperties();
    private WebhookProperties webhook = new WebhookProperties();
    private WidgetProperties widget = new WidgetProperties();
    private GuestAccessProperties guestAccess = new GuestAccessProperties();
    private EmailProperties email = new EmailProperties();
    private List<TicketActionProperties> ticketActions = new ArrayList<>();
    private TicketSubjectsProperties ticketSubjects = new TicketSubjectsProperties();
    private NewslettersProperties newsletters = new NewslettersProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRoutePrefix() {
        return routePrefix;
    }

    public void setRoutePrefix(String routePrefix) {
        this.routePrefix = routePrefix;
    }

    public KnowledgeBaseProperties getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(KnowledgeBaseProperties knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public BroadcastingProperties getBroadcasting() {
        return broadcasting;
    }

    public void setBroadcasting(BroadcastingProperties broadcasting) {
        this.broadcasting = broadcasting;
    }

    public TwoFactorProperties getTwoFactor() {
        return twoFactor;
    }

    public void setTwoFactor(TwoFactorProperties twoFactor) {
        this.twoFactor = twoFactor;
    }

    public SlaProperties getSla() {
        return sla;
    }

    public void setSla(SlaProperties sla) {
        this.sla = sla;
    }

    public SnoozeProperties getSnooze() {
        return snooze;
    }

    public void setSnooze(SnoozeProperties snooze) {
        this.snooze = snooze;
    }

    public WebhookProperties getWebhook() {
        return webhook;
    }

    public void setWebhook(WebhookProperties webhook) {
        this.webhook = webhook;
    }

    public WidgetProperties getWidget() {
        return widget;
    }

    public void setWidget(WidgetProperties widget) {
        this.widget = widget;
    }

    public GuestAccessProperties getGuestAccess() {
        return guestAccess;
    }

    public void setGuestAccess(GuestAccessProperties guestAccess) {
        this.guestAccess = guestAccess;
    }

    public EmailProperties getEmail() {
        return email;
    }

    public void setEmail(EmailProperties email) {
        this.email = email;
    }

    public List<TicketActionProperties> getTicketActions() {
        return ticketActions;
    }

    public void setTicketActions(List<TicketActionProperties> ticketActions) {
        this.ticketActions = ticketActions;
    }

    public TicketSubjectsProperties getTicketSubjects() {
        return ticketSubjects;
    }

    public void setTicketSubjects(TicketSubjectsProperties ticketSubjects) {
        this.ticketSubjects = ticketSubjects;
    }

    public NewslettersProperties getNewsletters() {
        return newsletters;
    }

    public void setNewsletters(NewslettersProperties newsletters) {
        this.newsletters = newsletters;
    }

    /**
     * Host-app models a ticket can be *about* (Project, Customer, asset, …).
     * {@code types} is the allowlist the agent/admin API may attach; leave empty
     * to disable API attach (programmatic {@link dev.escalated.services.TicketSubjectService}
     * still works when the allowlist is empty).
     */
    public static class TicketSubjectsProperties {
        private List<String> types = new ArrayList<>();

        public List<String> getTypes() {
            return types;
        }

        public void setTypes(List<String> types) {
            this.types = types;
        }
    }

    /**
     * A host-defined custom ticket action button, bound from the
     * {@code escalated.ticket-actions} configuration list.
     */
    public static class TicketActionProperties {
        private String key;
        private String label;
        private String variant = "secondary";
        private boolean visible = true;
        private boolean enabled = true;
        private String confirmation;
        private Map<String, Object> metadata;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getVariant() {
            return variant;
        }

        public void setVariant(String variant) {
            this.variant = variant;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getConfirmation() {
            return confirmation;
        }

        public void setConfirmation(String confirmation) {
            this.confirmation = confirmation;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    public static class KnowledgeBaseProperties {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class BroadcastingProperties {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class TwoFactorProperties {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class SlaProperties {
        private int checkIntervalSeconds = 60;

        public int getCheckIntervalSeconds() {
            return checkIntervalSeconds;
        }

        public void setCheckIntervalSeconds(int checkIntervalSeconds) {
            this.checkIntervalSeconds = checkIntervalSeconds;
        }
    }

    public static class SnoozeProperties {
        private int checkIntervalSeconds = 60;

        public int getCheckIntervalSeconds() {
            return checkIntervalSeconds;
        }

        public void setCheckIntervalSeconds(int checkIntervalSeconds) {
            this.checkIntervalSeconds = checkIntervalSeconds;
        }
    }

    public static class WebhookProperties {
        private int maxRetries = 3;

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    public static class WidgetProperties {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class GuestAccessProperties {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Outbound + inbound email config. {@code domain} is used for the
     * right-hand side of RFC 5322 Message-IDs and signed Reply-To
     * addresses. {@code inboundSecret} is the HMAC key used to sign
     * Reply-To addresses so the inbound provider webhook can verify
     * a ticket id without trusting the mail client's threading headers.
     */
    public static class EmailProperties {
        private String domain = "localhost";
        private String inboundSecret = "";

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getInboundSecret() {
            return inboundSecret;
        }

        public void setInboundSecret(String inboundSecret) {
            this.inboundSecret = inboundSecret;
        }
    }

    /** Newsletter broadcast feature (off by default). */
    public static class NewslettersProperties {
        private boolean enabled = false;
        private String appUrl = "http://localhost";
        private String defaultFrom;
        private String defaultReplyTo;
        private String defaultTheme = "default";
        private int rateLimitPerMinute = 60;
        private int batchSize = 50;
        private boolean trackingEnabled = true;
        private double autoPauseBounceRate = 0.05;
        private int autoPauseThreshold = 100;
        private int claimTimeoutMinutes = 10;
        private String brandAccent = "#2563eb";
        private String brandLogoUrl;
        private String brandPhysicalAddress;
        private String themesDir = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppUrl() {
            return appUrl;
        }

        public void setAppUrl(String appUrl) {
            this.appUrl = appUrl;
        }

        public String getDefaultFrom() {
            return defaultFrom;
        }

        public void setDefaultFrom(String defaultFrom) {
            this.defaultFrom = defaultFrom;
        }

        public String getDefaultReplyTo() {
            return defaultReplyTo;
        }

        public void setDefaultReplyTo(String defaultReplyTo) {
            this.defaultReplyTo = defaultReplyTo;
        }

        public String getDefaultTheme() {
            return defaultTheme;
        }

        public void setDefaultTheme(String defaultTheme) {
            this.defaultTheme = defaultTheme;
        }

        public int getRateLimitPerMinute() {
            return rateLimitPerMinute;
        }

        public void setRateLimitPerMinute(int rateLimitPerMinute) {
            this.rateLimitPerMinute = rateLimitPerMinute;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public boolean isTrackingEnabled() {
            return trackingEnabled;
        }

        public void setTrackingEnabled(boolean trackingEnabled) {
            this.trackingEnabled = trackingEnabled;
        }

        public double getAutoPauseBounceRate() {
            return autoPauseBounceRate;
        }

        public void setAutoPauseBounceRate(double autoPauseBounceRate) {
            this.autoPauseBounceRate = autoPauseBounceRate;
        }

        public int getAutoPauseThreshold() {
            return autoPauseThreshold;
        }

        public void setAutoPauseThreshold(int autoPauseThreshold) {
            this.autoPauseThreshold = autoPauseThreshold;
        }

        public int getClaimTimeoutMinutes() {
            return claimTimeoutMinutes;
        }

        public void setClaimTimeoutMinutes(int claimTimeoutMinutes) {
            this.claimTimeoutMinutes = claimTimeoutMinutes;
        }

        public String getBrandAccent() {
            return brandAccent;
        }

        public void setBrandAccent(String brandAccent) {
            this.brandAccent = brandAccent;
        }

        public String getBrandLogoUrl() {
            return brandLogoUrl;
        }

        public void setBrandLogoUrl(String brandLogoUrl) {
            this.brandLogoUrl = brandLogoUrl;
        }

        public String getBrandPhysicalAddress() {
            return brandPhysicalAddress;
        }

        public void setBrandPhysicalAddress(String brandPhysicalAddress) {
            this.brandPhysicalAddress = brandPhysicalAddress;
        }

        public String getThemesDir() {
            return themesDir;
        }

        public void setThemesDir(String themesDir) {
            this.themesDir = themesDir;
        }
    }
}
