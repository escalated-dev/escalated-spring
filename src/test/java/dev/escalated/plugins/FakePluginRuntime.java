package dev.escalated.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A stand-in for {@code @escalated-dev/plugin-runtime}: the same JSON-RPC 2.0 over stdio, answered by
 * Java so the bridge can be tested without Node. The plugins it reports come from the
 * {@value #MANIFEST} environment variable.
 *
 * <ul>
 *   <li>{@code handshake}: compatible, {@code http_contract_versions: [1]} (none with {@value #NO_HTTP}).
 *   <li>{@code action}: for every plugin whose manifest lists the hook (it ignores the {@code plugins}
 *       allow-list, as the real runtime does today), {@code ctx.store.insert} into {@code received}
 *       {@code {hook, event, plugins}}, acting for that plugin.
 *   <li>{@code filter}: a string value gets {@code |sdk} appended; hook {@code fail} is an error.
 *   <li>{@code activate}, {@code deactivate}: {@code ctx.store.insert} into {@code lifecycle}.
 *   <li>{@code endpoint}, {@code webhook}, by declared path: {@code /echo/:id} returns the request;
 *       {@code /boom} fails; {@code /Settings} returns page props; {@code /spy} reads
 *       {@code ctx.store.get} as the plugin named in the query's {@code as}; {@code /ctx} makes the
 *       ctx call its body names ({@code {method, params}}) and returns {@code {result}} or {@code {error}};
 *       {@code /ctx-together} does the same once a second {@code /ctx-together} call has arrived, so
 *       the two calls' ctx requests are in flight at once.
 * </ul>
 *
 * <p>Like the real runtime (after 0.2.0) it stamps every ctx request it makes while handling a host
 * call with that call's id as {@code host_call_id}, and answers the handshake with
 * {@code host_call_ids: true}. {@value #NO_HOST_CALL_IDS} turns both off, as an older runtime.
 */
public final class FakePluginRuntime {

    static final String MANIFEST = "ESCALATED_FAKE_PLUGIN_MANIFEST";
    static final String NO_HTTP = "ESCALATED_FAKE_PLUGIN_NO_HTTP";
    static final String NO_HOST_CALL_IDS = "ESCALATED_FAKE_PLUGIN_NO_HOST_CALL_IDS";

    /** The id of the host call the current worker thread is handling. */
    private static final ThreadLocal<JsonNode> SERVING = new ThreadLocal<>();
    private static final Object TOGETHER_LOCK = new Object();
    private static CompletableFuture<Void> together;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PrintStream OUT = new PrintStream(System.out, false, StandardCharsets.UTF_8);
    private static final Map<Long, CompletableFuture<JsonNode>> PENDING = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT = new AtomicLong();
    private static JsonNode manifests;

    private FakePluginRuntime() {
    }

    /** The command that starts this stand-in: this JVM's java, on a classpath holding it and Jackson. */
    static List<String> command() {
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> classpath = new ArrayList<>();
        for (Class<?> type : List.of(FakePluginRuntime.class, ObjectMapper.class, com.fasterxml.jackson.core.JsonFactory.class,
                com.fasterxml.jackson.annotation.JsonProperty.class)) {
            try {
                classpath.add(new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath());
            } catch (URISyntaxException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return List.of(java, "-Xmx64m", "-cp", String.join(File.pathSeparator, classpath), FakePluginRuntime.class.getName());
    }

    public static void main(String[] args) throws Exception {
        String raw = System.getenv(MANIFEST);
        manifests = JSON.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        System.err.println("fake plugin runtime: ready");
        ExecutorService workers = Executors.newCachedThreadPool();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode message = JSON.readTree(line);
            if (message.hasNonNull("method")) {
                workers.execute(() -> handle(message));
                continue;
            }
            CompletableFuture<JsonNode> waiting = PENDING.remove(message.path("id").asLong());
            if (waiting != null) {
                waiting.complete(message);
            }
        }
        workers.shutdown();
        workers.awaitTermination(5, TimeUnit.SECONDS);
        System.exit(0);
    }

    private static void write(Object message) {
        try {
            String text = JSON.writeValueAsString(message);
            synchronized (OUT) {
                OUT.print(text + "\n");
                OUT.flush();
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Asks the host for a ctx.* method and waits for the answer. */
    private static JsonNode call(String method, Map<String, Object> given) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>(given);
        JsonNode serving = SERVING.get();
        if (serving != null && !serving.isNull() && System.getenv(NO_HOST_CALL_IDS) == null) {
            params.put("host_call_id", serving);
        }
        long id = NEXT.incrementAndGet();
        CompletableFuture<JsonNode> answer = new CompletableFuture<>();
        PENDING.put(id, answer);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);
        write(request);
        JsonNode message = answer.get(20, TimeUnit.SECONDS);
        if (message.hasNonNull("error")) {
            throw new IllegalStateException(message.path("error").path("message").asText());
        }
        return message.get("result");
    }

    private static void handle(JsonNode message) {
        ObjectNode reply = JSON.createObjectNode();
        reply.put("jsonrpc", "2.0");
        reply.set("id", message.get("id"));
        SERVING.set(message.get("id"));
        try {
            reply.set("result", JSON.valueToTree(dispatch(message.path("method").asText(), message.path("params"))));
        } catch (Exception ex) {
            ObjectNode error = reply.putObject("error");
            error.put("code", -32000);
            error.put("message", ex.getMessage());
        } finally {
            SERVING.remove();
        }
        if (message.hasNonNull("id")) {
            write(reply);
        }
    }

    private static boolean declares(String plugin, String list, String hook) {
        for (JsonNode item : manifests.path(plugin).path(list)) {
            if (hook.equals(item.asText()) || hook.equals(item.path("hook").asText())) {
                return true;
            }
        }
        return false;
    }

    private static Object dispatch(String method, JsonNode p) throws Exception {
        switch (method) {
            case "handshake": {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("protocol_version", "1.0");
                out.put("runtime_version", "fake");
                out.put("compatible", "1.0".equals(p.path("protocol_version").asText()));
                out.put("http_contract_versions", System.getenv(NO_HTTP) != null ? List.of() : List.of(1));
                out.put("host_call_ids", System.getenv(NO_HOST_CALL_IDS) == null);
                return out;
            }
            case "manifest":
                return manifests;
            case "ping":
                return Map.of("pong", true);
            case "action": {
                String hook = p.path("hook").asText();
                Iterator<String> names = manifests.fieldNames();
                while (names.hasNext()) {
                    String name = names.next();
                    if (!declares(name, "actionHooks", hook)) {
                        continue;
                    }
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("hook", hook);
                    data.put("event", p.get("event"));
                    data.put("plugins", p.get("plugins"));
                    try {
                        call("ctx.store.insert", Map.of("plugin", name, "collection", "received", "data", data));
                    } catch (Exception ignored) {
                        // The runtime carries on, as the real one does with Promise.allSettled.
                    }
                }
                return Map.of("ok", true);
            }
            case "filter": {
                if ("fail".equals(p.path("hook").asText())) {
                    throw new IllegalStateException("filter failed");
                }
                JsonNode value = p.get("value");
                return value != null && value.isTextual() ? value.asText() + "|sdk" : value;
            }
            case "activate":
            case "deactivate":
                call("ctx.store.insert", Map.of("plugin", p.path("plugin").asText(), "collection", "lifecycle",
                        "data", Map.of("event", method)));
                return Map.of("ok", true);
            case "endpoint":
            case "webhook":
                return http(method, p);
            default:
                throw new IllegalStateException("unknown method: " + method);
        }
    }

    private static Object http(String kind, JsonNode p) throws Exception {
        String name = p.path("plugin").asText();
        String path = p.path("path").asText();
        if (path.startsWith("/echo")) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("kind", kind);
            for (String key : List.of("method", "path", "params", "query", "body", "headers", "httpContract",
                    "rawBodyBase64", "clientIp")) {
                out.put(key, p.get(key));
            }
            return out;
        }
        switch (path) {
            case "/boom":
                throw new IllegalStateException("secret failure detail");
            case "/Settings":
                return Map.of("greeting", "hello from " + name);
            case "/spy": {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("plugin", p.path("query").path("as").asText());
                params.put("collection", "secrets");
                params.put("key", "token");
                try {
                    return Map.of("value", String.valueOf(call("ctx.store.get", params)));
                } catch (Exception ex) {
                    return Map.of("error", ex.getMessage());
                }
            }
            case "/ctx-together":
                meet();
                // fall through
            case "/ctx": {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = p.path("body").has("params")
                        ? JSON.convertValue(p.path("body").path("params"), Map.class) : new LinkedHashMap<>();
                params.put("plugin", name);
                try {
                    JsonNode result = call(p.path("body").path("method").asText(), params);
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("result", result);
                    return out;
                } catch (Exception ex) {
                    return Map.of("error", ex.getMessage());
                }
            }
            default:
                throw new IllegalStateException("no handler for " + path);
        }
    }

    /** Returns once two callers have arrived (or after five seconds), so their ctx requests overlap. */
    private static void meet() throws Exception {
        CompletableFuture<Void> mine;
        synchronized (TOGETHER_LOCK) {
            if (together != null) {
                together.complete(null);
                together = null;
                return;
            }
            together = new CompletableFuture<>();
            mine = together;
        }
        try {
            mine.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException ignored) {
            // Carry on alone.
        }
    }
}
