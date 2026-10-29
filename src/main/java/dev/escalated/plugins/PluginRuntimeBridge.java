package dev.escalated.plugins;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

/**
 * Runs SDK plugins ({@code @escalated-dev/plugin-sdk}) through the {@code @escalated-dev/plugin-runtime}
 * Node process, spoken to as JSON-RPC 2.0 over its stdin and stdout, one message per line.
 *
 * <p>The runtime is started on first use. When it cannot start (no Node, no package) the host
 * carries on without SDK plugins and retries with exponential backoff, capped at five minutes:
 * actions are dropped, filters return their input, endpoints answer 503.
 *
 * <p>While the host waits for an answer, the runtime may call back with {@code ctx.*} requests;
 * each is served on its own thread. A call may act only for a plugin the host is dispatching to at
 * that moment; one naming any other plugin is refused, never served as the plugin it names.
 */
final class PluginRuntimeBridge implements AutoCloseable {

    /** The runtime's entry point, relative to the project directory. */
    static final String DEFAULT_SCRIPT = "node_modules/@escalated-dev/plugin-runtime/build/bin/escalated-plugins.js";

    static final String PROTOCOL_VERSION = "1.0";
    static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(15);
    static final int MAX_MESSAGE_BYTES = 10 * 1024 * 1024;
    static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger("dev.escalated.plugins.runtime");
    private static final ObjectMapper JSON = PluginJson.MAPPER.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Answers a runtime {@code ctx.*} call for {@code plugin}, which the bridge has checked it serves. */
    @FunctionalInterface
    interface ContextServer {
        Object serve(String plugin, String method, Map<String, Object> params, Authentication actor) throws Exception;
    }

    /** The dispatch a runtime call serves: the plugins it may act for, and the user behind it. */
    record Scope(List<String> plugins, Authentication actor) {
    }

    /** The runtime is not running. */
    static final class UnavailableException extends RuntimeException {
        UnavailableException(String message) {
            super(message);
        }
    }

    private final List<String> command;
    private final File directory;
    private final Map<String, String> environment;
    private final String hostVersion;
    private final ContextServer server;
    private final ExecutorService executor;

    private final Object lock = new Object();
    private Proc proc;
    private int failures;
    private long retryAt;
    private List<PluginEntry> cached = List.of();
    private boolean httpContract;

    private final Map<Long, Scope> scopes = new ConcurrentHashMap<>();
    private final AtomicLong nextScope = new AtomicLong();

    PluginRuntimeBridge(List<String> command, File directory, Map<String, String> environment, String hostVersion,
                        ContextServer server, ExecutorService executor) {
        this.command = List.copyOf(command);
        this.directory = directory;
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
        this.hostVersion = hostVersion;
        this.server = server;
        this.executor = executor;
    }

    private final class Proc {
        final Process process;
        final OutputStream stdin;
        final Object writeLock = new Object();
        final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
        final AtomicLong nextId = new AtomicLong();
        final CompletableFuture<Void> done = new CompletableFuture<>();

        Proc(Process process) {
            this.process = process;
            this.stdin = process.getOutputStream();
        }

        boolean alive() {
            return !done.isDone();
        }

        void write(Object message) throws IOException {
            byte[] bytes = JSON.writeValueAsBytes(message);
            if (bytes.length > MAX_MESSAGE_BYTES) {
                throw new IOException("message of " + bytes.length + " bytes is over the 10 MB limit");
            }
            synchronized (writeLock) {
                stdin.write(bytes);
                stdin.write('\n');
                stdin.flush();
            }
        }

        void kill() {
            if (!done.complete(null)) {
                return;
            }
            pending.values().forEach(future -> future.completeExceptionally(new IOException("the runtime stopped")));
            pending.clear();
            try {
                stdin.close();
            } catch (IOException ignored) {
                // Closing a dead pipe.
            }
            process.destroyForcibly();
        }
    }

    // ---- lifecycle

    /** A running, handshaken runtime, starting one when the last has died and the backoff has passed. */
    private Proc ensure() {
        synchronized (lock) {
            if (proc != null && proc.alive()) {
                return proc;
            }
            if (System.currentTimeMillis() < retryAt) {
                throw new UnavailableException("the plugin runtime is not available");
            }
            try {
                proc = start();
                failures = 0;
                return proc;
            } catch (Exception ex) {
                proc = null;
                failures++;
                long backoff = Math.min(1000L << Math.min(failures - 1, 9), MAX_BACKOFF.toMillis());
                retryAt = System.currentTimeMillis() + backoff;
                LOG.warn("Escalated plugin runtime could not start (retrying in {} ms): {}", backoff, ex.getMessage());
                throw new UnavailableException("the plugin runtime is not available");
            }
        }
    }

    private Proc start() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (directory != null) {
            builder.directory(directory);
        }
        builder.environment().putAll(environment);
        Proc started = new Proc(builder.start());
        Thread errors = new Thread(() -> drainErrors(started.process.getErrorStream()), "escalated-plugin-runtime-stderr");
        errors.setDaemon(true);
        errors.start();
        Thread reader = new Thread(() -> read(started), "escalated-plugin-runtime-stdout");
        reader.setDaemon(true);
        reader.start();

        try {
            Map<String, Object> hello = new LinkedHashMap<>();
            hello.put("protocol_version", PROTOCOL_VERSION);
            hello.put("host", "escalated-spring");
            hello.put("host_version", hostVersion);
            JsonNode handshake = send(started, "handshake", hello, HANDSHAKE_TIMEOUT);
            if (handshake == null || !handshake.path("compatible").asBoolean(false)) {
                throw new IOException("the runtime is not compatible with protocol " + PROTOCOL_VERSION);
            }
            boolean contract = false;
            for (JsonNode version : handshake.path("http_contract_versions")) {
                if (version.asInt() == 1) {
                    contract = true;
                }
            }
            JsonNode manifests = send(started, "manifest", Map.of(), HANDSHAKE_TIMEOUT);
            cached = entries(manifests);
            httpContract = contract;
            return started;
        } catch (Exception ex) {
            started.kill();
            throw ex;
        }
    }

    private static void drainErrors(InputStream stream) {
        try (var lines = new java.io.BufferedReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                LOG.info("[plugin runtime] {}", line);
            }
        } catch (IOException ignored) {
            // The runtime exited.
        }
    }

    /** One line of at most 10 MB; a longer one is skipped. Null at the end of the stream. */
    private static byte[] readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        boolean overflow = false;
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                if (overflow) {
                    LOG.warn("Escalated plugin runtime sent a message over 10 MB; it was ignored");
                    overflow = false;
                    line.reset();
                    continue;
                }
                return line.toByteArray();
            }
            if (!overflow) {
                line.write(b);
                if (line.size() > MAX_MESSAGE_BYTES) {
                    overflow = true;
                    line.reset();
                }
            }
        }
        return line.size() > 0 && !overflow ? line.toByteArray() : null;
    }

    /** Delivers answers to their callers and serves the runtime's {@code ctx.*} calls. */
    private void read(Proc p) {
        try (InputStream in = new BufferedInputStream(p.process.getInputStream())) {
            byte[] line;
            while ((line = readLine(in)) != null) {
                if (new String(line, StandardCharsets.UTF_8).isBlank()) {
                    continue;
                }
                JsonNode message;
                try {
                    message = JSON.readTree(line);
                } catch (IOException ex) {
                    LOG.warn("Escalated plugin runtime sent a malformed message; it was ignored");
                    continue;
                }
                if (message.hasNonNull("method")) {
                    if (message.hasNonNull("id")) {
                        executor.execute(() -> answer(p, message));
                    }
                    continue;
                }
                CompletableFuture<JsonNode> waiting = p.pending.remove(message.path("id").asLong(-1));
                if (waiting != null) {
                    waiting.complete(message);
                }
            }
        } catch (IOException ex) {
            LOG.debug("Escalated plugin runtime stopped: {}", ex.getMessage());
        } finally {
            p.kill();
        }
    }

    /** Makes one call and waits for its answer. */
    private JsonNode send(Proc p, String method, Object params, Duration timeout) throws Exception {
        long id = p.nextId.incrementAndGet();
        CompletableFuture<JsonNode> answer = new CompletableFuture<>();
        p.pending.put(id, answer);
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            request.put("params", params);
            p.write(request);
            JsonNode message = answer.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (message.hasNonNull("error")) {
                throw new IllegalStateException(method + ": " + message.path("error").path("message").asText("failed"));
            }
            return message.get("result");
        } catch (TimeoutException ex) {
            throw new TimeoutException(method + " timed out after " + timeout.toMillis() + " ms");
        } catch (ExecutionException ex) {
            throw new IOException(method + ": the runtime stopped", ex.getCause());
        } finally {
            p.pending.remove(id);
        }
    }

    /** A call on behalf of a dispatch, starting the runtime when needed. */
    JsonNode call(Scope scope, String method, Object params, Duration timeout) throws Exception {
        Proc p = ensure();
        long key = nextScope.incrementAndGet();
        scopes.put(key, scope);
        try {
            return send(p, method, params, timeout);
        } finally {
            scopes.remove(key);
        }
    }

    /** The SDK plugins the runtime reports, starting it on first use. The last list stays while it is down. */
    List<PluginEntry> entries() {
        try {
            ensure();
        } catch (UnavailableException ignored) {
            // Keep the last list, so the admin screen does not lose the plugins.
        }
        return known();
    }

    List<PluginEntry> known() {
        synchronized (lock) {
            return cached;
        }
    }

    /** Whether the runtime speaks HTTP contract version 1, starting it when needed. */
    boolean supportsHttp() {
        ensure();
        synchronized (lock) {
            return httpContract;
        }
    }

    @Override
    public void close() {
        Proc running;
        synchronized (lock) {
            running = proc;
            proc = null;
        }
        if (running != null) {
            running.kill();
        }
    }

    // ---- the runtime's ctx.* calls

    /**
     * The scope a ctx call acts in: one the runtime is being asked to serve right now that includes
     * the plugin it names. A call naming none acts for the plugin being served when there is exactly
     * one dispatch for exactly one plugin.
     */
    private Scope resolve(String named) {
        List<Scope> current = new ArrayList<>(scopes.values());
        if (named == null || named.isEmpty()) {
            if (current.size() == 1 && current.get(0).plugins().size() == 1) {
                return current.get(0);
            }
            throw new IllegalStateException("the call names no plugin");
        }
        List<Scope> matched = current.stream().filter(scope -> scope.plugins().contains(named)).toList();
        if (matched.isEmpty()) {
            throw new IllegalStateException("no dispatch is serving plugin \"" + named + "\"");
        }
        Authentication actor = matched.get(0).actor();
        for (Scope scope : matched) {
            if (scope.actor() != actor) {
                // Two dispatches for this plugin at once: the call cannot say whose it is.
                actor = null;
            }
        }
        return new Scope(List.of(named), actor);
    }

    private void answer(Proc p, JsonNode message) {
        Map<String, Object> params = PluginJson.record(PluginJson.fromNode(message.get("params")));
        if (params == null) {
            params = new LinkedHashMap<>();
        }
        String method = message.path("method").asText();
        ObjectNode reply = JSON.createObjectNode();
        reply.put("jsonrpc", "2.0");
        reply.set("id", message.get("id"));
        try {
            String named = params.get("plugin") instanceof String name ? name : null;
            Scope scope = resolve(named);
            Object result = server.serve(scope.plugins().get(0), method, params, scope.actor());
            reply.set("result", JSON.valueToTree(result));
        } catch (Exception ex) {
            ObjectNode error = reply.putObject("error");
            error.put("code", -32000);
            error.put("message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
        try {
            p.write(reply);
        } catch (IOException ex) {
            LOG.debug("Answering the plugin runtime's {} failed: {}", method, ex.getMessage());
        }
    }

    // ---- the runtime's manifest

    private List<PluginEntry> entries(JsonNode manifests) {
        Map<String, JsonNode> byName = new TreeMap<>();
        if (manifests != null) {
            manifests.fields().forEachRemaining(field -> byName.put(field.getKey(), field.getValue()));
        }
        List<PluginEntry> out = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> item : byName.entrySet()) {
            JsonNode m = item.getValue();
            String name = m.path("name").asText(item.getKey());
            if (name.isBlank()) {
                name = item.getKey();
            }
            PluginManifest manifest = new PluginManifest(
                    name,
                    m.path("version").asText(null),
                    m.path("description").asText(null),
                    m.path("author").asText(null),
                    m.path("homepage").asText(null),
                    list(m.get("pages"), PluginManifest.Page.class),
                    list(m.get("components"), PluginManifest.SlotComponent.class),
                    list(m.get("widgets"), PluginManifest.Widget.class));
            PluginEntry entry = PluginEntry.sdkPlugin(name, manifest);
            if (seen.containsKey(entry.slug)) {
                LOG.warn("Escalated plugins \"{}\" and \"{}\" share the slug \"{}\"; \"{}\" is not loaded",
                        seen.get(entry.slug), name, entry.slug, name);
                continue;
            }
            seen.put(entry.slug, name);
            for (JsonNode hook : m.path("actionHooks")) {
                entry.actionHooks.add(hook.asText());
            }
            for (JsonNode filter : m.path("filterHooks")) {
                entry.sdkFilters.put(filter.path("hook").asText(), filter.path("priority").asInt(10));
            }
            for (JsonNode endpoint : m.path("endpoints")) {
                addRoute(entry, entry.endpoints, endpoint, endpoint.path("capability").asText(null));
            }
            for (JsonNode webhook : m.path("webhooks")) {
                addRoute(entry, entry.webhooks, webhook, null);
            }
            out.add(entry);
        }
        return List.copyOf(out);
    }

    private static void addRoute(PluginEntry entry, List<ParsedRoute> routes, JsonNode route, String capability) {
        try {
            routes.add(ParsedRoute.parse(entry.name, route.path("method").asText(), route.path("path").asText(),
                    capability, null));
        } catch (IllegalArgumentException ex) {
            LOG.warn("{}; skipped", ex.getMessage());
        }
    }

    private static <T> List<T> list(JsonNode node, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                try {
                    out.add(JSON.treeToValue(item, type));
                } catch (IOException | IllegalArgumentException ex) {
                    LOG.warn("Escalated plugin manifest entry skipped: {}", ex.getMessage());
                }
            }
        }
        return out;
    }
}
