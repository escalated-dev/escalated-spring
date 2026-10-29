package dev.escalated.plugins;

import java.util.Map;

/**
 * What an endpoint or webhook receives, as the SDK's HTTP contract version 1 has it: the parsed body
 * (JSON or form fields, else null), the exact bytes, the params of the declared path, the query,
 * and lower-case headers without {@code Cookie}, {@code Authorization}, {@code X-XSRF-TOKEN},
 * {@code X-CSRF-TOKEN} or {@code Proxy-*}. {@code path} is the declared path, not the request's.
 */
public record PluginRequest(
        String method,
        String path,
        Object body,
        byte[] rawBody,
        Map<String, String> params,
        Map<String, String> query,
        Map<String, String> headers,
        String clientIp) {
}
