package dev.escalated.services.newsletter;

import dev.escalated.models.Contact;
import dev.escalated.models.newsletter.Newsletter;
import dev.escalated.models.newsletter.NewsletterDelivery;
import dev.escalated.models.newsletter.NewsletterTemplate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a NewsletterDelivery to themed HTML.
 *
 * <p>Markdown is host-pluggable via {@link Options#markdownToHtml}. Themes are
 * Thymeleaf-free HTML files that the renderer evaluates with simple
 * {@code {{key}}} / {@code {{{key}}}} substitution to stay lib-free.
 */
public class NewsletterRenderer {

    private static final List<String> ALLOWED_SCHEMES = List.of("http", "https", "mailto", "tel");
    private static final Pattern ANCHOR = Pattern.compile("(?i)(<a\\s[^>]*\\bhref=)(\"|')(.*?)\\2");
    private static final Pattern MERGE_FIELD = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*\\}\\}");

    private final Options opts;

    public NewsletterRenderer(Options opts) {
        this.opts = opts;
    }

    public String render(NewsletterDelivery delivery, Newsletter newsletter, Contact contact, NewsletterTemplate template) {
        String bodyMd = first(newsletter.getBodyMarkdown(), template != null ? template.getBodyMarkdown() : "");
        String themeSlug = first(newsletter.getTheme(), template != null ? template.getTheme() : opts.defaultTheme);

        String body = markdownToHtml(bodyMd);
        body = resolveMergeFields(body, contact, delivery);

        Map<String, String> ctx = new HashMap<>();
        ctx.put("subject", n(newsletter.getSubject()));
        ctx.put("body", body);
        ctx.put("unsubscribe_url", unsubscribeUrl(delivery));
        ctx.put("view_in_browser_url", viewInBrowserUrl(delivery));
        ctx.put("brand.name", opts.brandName);
        ctx.put("brand.accent", opts.brandAccent);
        ctx.put("brand.logo_url", n(opts.brandLogoUrl));
        ctx.put("brand.physical_address", n(opts.brandPhysicalAddress));

        String themed = renderTheme(themeSlug, ctx);
        if (!opts.trackingEnabled) return themed;
        return injectPixel(rewriteLinks(themed, delivery), delivery);
    }

    public String unsubscribeUrl(NewsletterDelivery d) {
        return trimSlash(opts.baseUrl) + "/escalated/n/u/" + d.getTrackingToken();
    }

    public String viewInBrowserUrl(NewsletterDelivery d) {
        return trimSlash(opts.baseUrl) + "/escalated/n/v/" + d.getTrackingToken();
    }

    private String markdownToHtml(String md) {
        if (opts.markdownToHtml != null) return opts.markdownToHtml.apply(md);
        String escaped = htmlEscape(md);
        return "<p>" + escaped.replaceAll("\\n{2,}", "</p><p>") + "</p>";
    }

    private String resolveMergeFields(String html, Contact contact, NewsletterDelivery delivery) {
        Matcher m = MERGE_FIELD.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String path = m.group(1).trim();
            m.appendReplacement(sb, Matcher.quoteReplacement(htmlEscape(resolvePath(path, contact, delivery))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String resolvePath(String path, Contact contact, NewsletterDelivery delivery) {
        if (path.equals("contact.name")) return n(contact.getName());
        if (path.equals("contact.first_name")) {
            String name = n(contact.getName());
            int sp = name.indexOf(' ');
            return sp >= 0 ? name.substring(0, sp) : name;
        }
        if (path.equals("contact.email")) return n(contact.getEmail());
        if (path.equals("unsubscribe_url")) return unsubscribeUrl(delivery);
        if (path.equals("view_in_browser_url")) return viewInBrowserUrl(delivery);
        // contact.metadata.* not surfaced here for v1 — Contact.metadataJson is a string.
        return "";
    }

    private String renderTheme(String slug, Map<String, String> ctx) {
        try {
            Path themesDir = Path.of(opts.themesDir);
            Path path = themesDir.resolve(slug + ".html");
            if (!Files.exists(path)) path = themesDir.resolve("default.html");
            String source = Files.readString(path, StandardCharsets.UTF_8);
            // {{{ key }}} = raw, {{ key }} = escaped
            source = Pattern.compile("\\{\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*\\}\\}\\}").matcher(source).replaceAll(m -> {
                String v = ctx.getOrDefault(m.group(1).trim(), "");
                return Matcher.quoteReplacement(v);
            });
            source = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*\\}\\}").matcher(source).replaceAll(m -> {
                String v = ctx.getOrDefault(m.group(1).trim(), "");
                return Matcher.quoteReplacement(htmlEscape(v));
            });
            return source;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load newsletter theme: " + slug, e);
        }
    }

    private String rewriteLinks(String html, NewsletterDelivery d) {
        String unsub = unsubscribeUrl(d);
        String view = viewInBrowserUrl(d);
        Matcher m = ANCHOR.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String quote = m.group(2);
            String href = m.group(3);
            String replacement;
            if (href.isEmpty() || href.startsWith("#")) {
                replacement = m.group(0);
            } else {
                int colon = href.indexOf(':');
                String scheme = (colon > 0 ? href.substring(0, colon) : "").toLowerCase();
                if (!ALLOWED_SCHEMES.contains(scheme)) {
                    replacement = prefix + quote + "#" + quote;
                } else if (scheme.equals("mailto") || scheme.equals("tel")
                        || href.startsWith(unsub) || href.startsWith(view)) {
                    replacement = m.group(0);
                } else {
                    String encoded = Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(href.getBytes(StandardCharsets.UTF_8));
                    String tracked = trimSlash(opts.baseUrl) + "/escalated/n/c/" + d.getTrackingToken() + "?u=" + encoded;
                    replacement = prefix + quote + tracked + quote;
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String injectPixel(String html, NewsletterDelivery d) {
        String url = trimSlash(opts.baseUrl) + "/escalated/n/o/" + d.getTrackingToken() + ".gif";
        String pixel = "<img src=\"" + htmlEscape(url) + "\" width=\"1\" height=\"1\" alt=\"\" />";
        if (html.contains("</body>")) return html.replace("</body>", pixel + "</body>");
        return html + pixel;
    }

    private static String n(String s) { return s == null ? "" : s; }
    private static String first(String a, String b) { return (a != null && !a.isEmpty()) ? a : (b == null ? "" : b); }
    private static String trimSlash(String s) { return s == null ? "" : s.replaceAll("/+$", ""); }
    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static class Options {
        public String baseUrl = "http://localhost";
        public String defaultTheme = "default";
        public boolean trackingEnabled = true;
        public String themesDir = "";
        public Function<String, String> markdownToHtml;
        public String brandName = "Support";
        public String brandAccent = "#2563eb";
        public String brandLogoUrl;
        public String brandPhysicalAddress;
    }
}
