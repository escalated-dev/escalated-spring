package dev.escalated.plugins;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

/**
 * A plugin's {@code ctx}, as the SDK's {@code PluginContext}: everything in it is scoped to the plugin
 * it was built for. Native plugins receive it in every callback; for SDK plugins the bridge answers
 * the runtime's {@code ctx.*} calls through the same methods.
 *
 * <p>Writes act as the signed-in user of the request the plugin is serving, or as no user outside a
 * request (a domain event, a scheduled job). A plugin never names who it acts as.
 */
public final class PluginContext {

    private static final Logger LOG = LoggerFactory.getLogger("dev.escalated.plugins");

    private final PluginRegistry registry;
    private final PluginEntry entry;
    private final Authentication actor;

    PluginContext(PluginRegistry registry, PluginEntry entry, Authentication actor) {
        this.registry = registry;
        this.entry = entry;
        this.actor = actor;
    }

    /** The plugin's name, which its store and config are keyed by. */
    public String plugin() {
        return entry.name;
    }

    public String slug() {
        return entry.slug;
    }

    /** The signed-in user this call acts for, or null. */
    public Authentication actor() {
        return actor;
    }

    public Config config() {
        return new Config();
    }

    public Store store() {
        return new Store();
    }

    public Log log() {
        return new Log();
    }

    public Tickets tickets() {
        return new Tickets();
    }

    public Replies replies() {
        return new Replies();
    }

    public Contacts contacts() {
        return new Contacts();
    }

    public Tags tags() {
        return new Tags();
    }

    public Departments departments() {
        return new Departments();
    }

    public Agents agents() {
        return new Agents();
    }

    public Broadcast broadcast() {
        return new Broadcast();
    }

    /**
     * Fires an action of the plugin's choosing to every active plugin. Names that belong to the host
     * (the canonical domain events, their legacy names and the {@code plugin.*} lifecycle actions)
     * are dropped and logged.
     */
    public void emit(String hook, Object data) {
        if (hook == null || hook.isBlank() || registry.reserved(hook)) {
            log().warn("ctx.emit(\"" + hook + "\") dropped: that name belongs to the host", null);
            return;
        }
        Object clean = PluginJson.plain(data);
        Map<String, Object> event = PluginJson.record(clean);
        if (event == null) {
            event = new java.util.LinkedHashMap<>();
            event.put("value", clean);
        }
        registry.dispatchAction(hook, event, actor);
    }

    /** The user of the request being served ({@code {id, name, email}}), or null. */
    public Map<String, Object> currentUser() {
        return registry.domain().currentUser(actor);
    }

    /** {@code ctx.config}: the plugin's settings, one JSON object. */
    public final class Config {

        public Map<String, Object> all() {
            return registry.storage().configAll(entry.name);
        }

        public Object get(String key) {
            return registry.storage().configGet(entry.name, key);
        }

        /** Merges {@code data} into the settings. */
        public void set(Map<String, Object> data) {
            registry.storage().configSet(entry.name, data);
        }
    }

    /** {@code ctx.store}: the plugin's records, by collection and key. */
    public final class Store {

        public Object get(String collection, String key) {
            return registry.storage().get(entry.name, collection, key);
        }

        public void set(String collection, String key, Object value) {
            registry.storage().set(entry.name, collection, key, value);
        }

        /** See the plugin contract: equality and {@code $gt $gte $lt $lte $ne $in $nin} on dotted fields. */
        public List<Map<String, Object>> query(String collection, Map<String, Object> filter, Map<String, Object> options) {
            return registry.storage().query(entry.name, collection, filter, options);
        }

        public Map<String, Object> insert(String collection, Map<String, Object> data) {
            return registry.storage().insert(entry.name, collection, data);
        }

        public Map<String, Object> update(String collection, String key, Map<String, Object> data) {
            return registry.storage().update(entry.name, collection, key, data);
        }

        public void delete(String collection, String key) {
            registry.storage().delete(entry.name, collection, key);
        }
    }

    /** {@code ctx.log}: the host log, with the plugin's name attached. */
    public final class Log {

        private String line(String message, Object data) {
            String text = "[" + entry.name + "] " + message;
            if (data != null) {
                try {
                    text += " " + PluginJson.write(data);
                } catch (IllegalArgumentException ignored) {
                    text += " " + data;
                }
            }
            return text;
        }

        public void info(String message, Object data) {
            LOG.info(line(message, data));
        }

        public void warn(String message, Object data) {
            LOG.warn(line(message, data));
        }

        public void error(String message, Object data) {
            LOG.error(line(message, data));
        }

        public void debug(String message, Object data) {
            LOG.debug(line(message, data));
        }
    }

    /** {@code ctx.tickets}, through the ticket service. */
    public final class Tickets {

        public Map<String, Object> find(Object id) {
            return registry.domain().findTicket(id);
        }

        public List<Map<String, Object>> query(Map<String, Object> filter) {
            return registry.domain().queryTickets(filter);
        }

        public Map<String, Object> create(Map<String, Object> data) {
            return registry.domain().createTicket(data, actor);
        }

        public Map<String, Object> update(Object id, Map<String, Object> data) {
            return registry.domain().updateTicket(id, data, actor);
        }
    }

    /** {@code ctx.replies}, through the ticket service. */
    public final class Replies {

        public Map<String, Object> find(Object id) {
            return registry.domain().findReply(id);
        }

        public List<Map<String, Object>> query(Map<String, Object> filter) {
            return registry.domain().queryReplies(filter);
        }

        public Map<String, Object> create(Map<String, Object> data) {
            return registry.domain().createReply(data, actor, entry.name);
        }
    }

    /** {@code ctx.contacts}: Escalated's contacts, not the host's users. */
    public final class Contacts {

        public Map<String, Object> find(Object id) {
            return registry.domain().findContact(id);
        }

        public Map<String, Object> findByEmail(String email) {
            return registry.domain().findContactByEmail(email);
        }

        /** Finds the contact with {@code email}, or creates it with {@code name}. */
        public Map<String, Object> create(Map<String, Object> data) {
            return registry.domain().createContact(data);
        }
    }

    /** {@code ctx.tags}. */
    public final class Tags {

        public List<Map<String, Object>> all() {
            return registry.domain().allTags();
        }

        public Map<String, Object> create(Map<String, Object> data) {
            return registry.domain().createTag(data);
        }
    }

    /** {@code ctx.departments}. */
    public final class Departments {

        public List<Map<String, Object>> all() {
            return registry.domain().allDepartments();
        }

        public Map<String, Object> find(Object id) {
            return registry.domain().findDepartment(id);
        }
    }

    /** {@code ctx.agents}: active agent profiles. */
    public final class Agents {

        public List<Map<String, Object>> all() {
            return registry.domain().allAgents();
        }

        public Map<String, Object> find(Object id) {
            return registry.domain().findAgent(id);
        }
    }

    /** {@code ctx.broadcast}, over the host's STOMP broker; a no-op without one. */
    public final class Broadcast {

        public void toChannel(String channel, String event, Object data) {
            send(PluginDomain.channelDestination(channel), null, event, data);
        }

        public void toUser(Object userId, String event, Object data) {
            Long id = PluginJson.id(userId);
            if (id == null) {
                throw new IllegalArgumentException("ctx.broadcast.toUser needs a user id");
            }
            send("/topic/users/" + id, null, event, data);
        }

        public void toTicket(Object ticketId, String event, Object data) {
            Long id = PluginJson.id(ticketId);
            if (id == null) {
                throw new IllegalArgumentException("ctx.broadcast.toTicket needs a ticket id");
            }
            send("/topic/tickets/" + id, id, event, data);
        }

        private void send(String destination, Long ticketId, String event, Object data) {
            if (!registry.domain().broadcast(destination, ticketId, event, data)) {
                log().debug("ctx.broadcast to " + destination + " was not delivered: no broker on this host", null);
            }
        }
    }
}
