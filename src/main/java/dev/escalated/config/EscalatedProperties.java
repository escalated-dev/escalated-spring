package dev.escalated.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "escalated")
public class EscalatedProperties {

    private String visibility = "all";
    private AccountsProperties accounts = new AccountsProperties();
    private boolean targetedSlas = false;
    private Map<String, String> automationConditions = new java.util.HashMap<>();
    private AutomationsProperties automations = new AutomationsProperties();

    public AutomationsProperties getAutomations() {
        return automations;
    }

    public void setAutomations(AutomationsProperties automations) {
        this.automations = automations;
    }

    /** {@code escalated.automations.*}: the automation sweep and the built-in auto-close. */
    public static class AutomationsProperties {
        /** Seconds between automation sweeps. */
        private int checkIntervalSeconds = 60;

        /**
         * Days after which a resolved ticket is closed by the built-in
         * auto-close. 0 turns it off. Defaults to the 7 days it always used.
         */
        private int autoCloseDays = 7;

        public int getCheckIntervalSeconds() {
            return checkIntervalSeconds;
        }

        public void setCheckIntervalSeconds(int checkIntervalSeconds) {
            this.checkIntervalSeconds = checkIntervalSeconds;
        }

        public int getAutoCloseDays() {
            return autoCloseDays;
        }

        public void setAutoCloseDays(int autoCloseDays) {
            this.autoCloseDays = autoCloseDays;
        }
    }

    public Map<String, String> getAutomationConditions() {
        return automationConditions;
    }

    public void setAutomationConditions(Map<String, String> value) {
        automationConditions = value;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        if (!"all".equals(visibility) && !"department".equals(visibility)) {
            throw new IllegalArgumentException("visibility must be all or department");
        }
        this.visibility = visibility;
    }

    public AccountsProperties getAccounts() {
        return accounts;
    }

    public void setAccounts(AccountsProperties accounts) {
        this.accounts = accounts;
    }

    public boolean isTargetedSlas() {
        return targetedSlas;
    }

    public void setTargetedSlas(boolean value) {
        targetedSlas = value;
    }

    public static class AccountsProperties {
        private boolean enabled;
        private boolean scopeAgents;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public boolean isScopeAgents() {
            return scopeAgents;
        }

        public void setScopeAgents(boolean value) {
            scopeAgents = value;
        }
    }

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
    private GuestRateLimitProperties guestRateLimit = new GuestRateLimitProperties();
    private EmailProperties email = new EmailProperties();
    private List<TicketActionProperties> ticketActions = new ArrayList<>();
    private TicketSubjectsProperties ticketSubjects = new TicketSubjectsProperties();
    private NewslettersProperties newsletters = new NewslettersProperties();
    private UiProperties ui = new UiProperties();

    public UiProperties getUi() {
        return ui;
    }

    public void setUi(UiProperties ui) {
        this.ui = ui;
    }

    /**
     * {@code escalated.ui.*}: the Inertia pages of {@code @escalated-dev/escalated}.
     *
     * <p>Off by default, so a host that uses Escalated as an API only is unchanged. Laravel
     * defaults its {@code escalated.ui.enabled} to true; here a host opts in.
     */
    public static class UiProperties {
        /** Serve the agent, customer and admin pages. */
        private boolean enabled = false;

        /**
         * The asset version Inertia compares on every visit. A client holding another version is
         * answered 409 and reloads. Blank turns the check off.
         */
        private String version = "";

        /**
         * A Thymeleaf view to render the first visit with, instead of the built-in page. It gets
         * {@code page} (the page object), {@code pageJson}, and {@code routesScript}.
         */
        private String rootView;

        /** The document title of the built-in page. */
        private String title = "Support";

        /** Script URLs the built-in page loads as modules: the host's built frontend. */
        private List<String> scripts = new ArrayList<>();

        /** Stylesheet URLs the built-in page links. */
        private List<String> stylesheets = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version == null ? "" : version;
        }

        public String getRootView() {
            return rootView;
        }

        public void setRootView(String rootView) {
            this.rootView = rootView;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public List<String> getScripts() {
            return scripts;
        }

        public void setScripts(List<String> scripts) {
            this.scripts = scripts;
        }

        public List<String> getStylesheets() {
            return stylesheets;
        }

        public void setStylesheets(List<String> stylesheets) {
            this.stylesheets = stylesheets;
        }
    }


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

    public GuestRateLimitProperties getGuestRateLimit() {
        return guestRateLimit;
    }

    public void setGuestRateLimit(GuestRateLimitProperties guestRateLimit) {
        this.guestRateLimit = guestRateLimit;
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
        /**
         * Minutes before an SLA deadline at which {@code sla.warning} is
         * announced, once per deadline. 0 turns warnings off.
         */
        private int warningMinutes = 30;

        public int getWarningMinutes() {
            return warningMinutes;
        }

        public void setWarningMinutes(int warningMinutes) {
            this.warningMinutes = warningMinutes;
        }

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

        /**
         * Other sites allowed to embed the widget, each {@code scheme://host[:port]}
         * or {@code https://*.example.com}. Empty by default: no other site gets
         * CORS access until one is listed here or in the
         * {@code widget_allowed_origins} admin setting.
         */
        private List<String> allowedOrigins = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : allowedOrigins;
        }
    }

    public static class GuestAccessProperties {
        private boolean enabled = false;

        /** Lifetime of a guest grant, clamped to 5 minutes through 7 days. */
        private int ttlMinutes = 1440;

        /**
         * Whether a ticket's permanent guest token (issued before verified
         * access) still opens it, until a grant is issued for that ticket.
         * A transition setting: turn it off once old links have aged out.
         */
        private boolean legacyTokens = true;

        /**
         * Key for code digests and grants. Falls back to
         * {@code escalated.email.inbound-secret}; without either, a random key
         * per process is used and grants do not survive a restart.
         */
        private String secret;

        /** From address of the code email; the mail sender's default when unset. */
        private String fromAddress;

        private int codesPerClientPerHour = 3;
        private int codesPerMailboxPerHour = 10;

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }

        public boolean isLegacyTokens() {
            return legacyTokens;
        }

        public void setLegacyTokens(boolean legacyTokens) {
            this.legacyTokens = legacyTokens;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getFromAddress() {
            return fromAddress;
        }

        public void setFromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
        }

        public int getCodesPerClientPerHour() {
            return codesPerClientPerHour;
        }

        public void setCodesPerClientPerHour(int codesPerClientPerHour) {
            this.codesPerClientPerHour = codesPerClientPerHour;
        }

        public int getCodesPerMailboxPerHour() {
            return codesPerMailboxPerHour;
        }

        public void setCodesPerMailboxPerHour(int codesPerMailboxPerHour) {
            this.codesPerMailboxPerHour = codesPerMailboxPerHour;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Per-client-IP rate limits on the unauthenticated guest endpoints
     * ({@code POST /escalated/api/widget/tickets}, {@code POST /escalated/api/widget/chat/start},
     * which shares the ticket counter, and the guest reply endpoints
     * under {@code /escalated/api/widget} and {@code /escalated/api/guest}).
     * Every accepted guest ticket or reply writes rows and sends mail, so an
     * uncapped endpoint lets anyone flood the helpdesk and the mail provider. A
     * request over the limit gets {@code 429} with {@code Retry-After}.
     *
     * <p>The client IP is {@code HttpServletRequest#getRemoteAddr()}. Behind a
     * reverse proxy or load balancer, set {@code server.forward-headers-strategy}
     * and configure your container's trusted proxies, or every guest shares the
     * proxy's address.
     *
     * <p>Counters live in a {@code GuestRateLimitStore}: in memory, per process,
     * unless the host defines a shared {@code GuestRateLimitStore} bean (e.g.
     * Redis-backed) for a multi-instance deployment.
     */
    public static class GuestRateLimitProperties {
        /** Set false only when the host already throttles these routes upstream. */
        private boolean enabled = true;
        /** Guest ticket submissions and widget chat starts, together, per IP per minute. */
        private int ticketsPerMinute = 5;
        /** Guest replies per IP per minute. */
        private int repliesPerMinute = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTicketsPerMinute() {
            return ticketsPerMinute;
        }

        public void setTicketsPerMinute(int ticketsPerMinute) {
            this.ticketsPerMinute = ticketsPerMinute;
        }

        public int getRepliesPerMinute() {
            return repliesPerMinute;
        }

        public void setRepliesPerMinute(int repliesPerMinute) {
            this.repliesPerMinute = repliesPerMinute;
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
