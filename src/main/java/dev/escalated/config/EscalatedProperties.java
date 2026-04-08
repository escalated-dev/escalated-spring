package dev.escalated.config;

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
}
