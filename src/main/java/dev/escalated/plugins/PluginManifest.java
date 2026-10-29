package dev.escalated.plugins;

import java.util.List;
import java.util.Map;

/**
 * A plugin's identity and the screens it adds, with the SDK manifest's names.
 *
 * <p>{@code name} identifies the plugin: {@code ctx.store} and {@code ctx.config} are keyed by it.
 * Its {@link PluginSlugs#slug slug} is what goes in URLs and the {@code escalated_plugins} row.
 */
public record PluginManifest(
        String name,
        String version,
        String description,
        String author,
        String homepage,
        List<Page> pages,
        List<SlotComponent> components,
        List<Widget> widgets) {

    public PluginManifest {
        pages = pages == null ? List.of() : List.copyOf(pages);
        components = components == null ? List.of() : List.copyOf(components);
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
    }

    /** A manifest with nothing but a name and a version. */
    public static PluginManifest of(String name, String version) {
        return new PluginManifest(name, version, null, null, null, List.of(), List.of(), List.of());
    }

    public PluginManifest withDescription(String value) {
        return new PluginManifest(name, version, value, author, homepage, pages, components, widgets);
    }

    public PluginManifest withAuthor(String value, String url) {
        return new PluginManifest(name, version, description, value, url, pages, components, widgets);
    }

    public PluginManifest withPages(Page... value) {
        return new PluginManifest(name, version, description, author, homepage, List.of(value), components, widgets);
    }

    public PluginManifest withComponents(SlotComponent... value) {
        return new PluginManifest(name, version, description, author, homepage, pages, List.of(value), widgets);
    }

    public PluginManifest withWidgets(Widget... value) {
        return new PluginManifest(name, version, description, author, homepage, pages, components, List.of(value));
    }

    /**
     * An admin page, served at {@code /escalated/admin/plugins/{slug}/{route}} and rendered as
     * {@code Escalated/Plugin/Page} with the props of the plugin's own {@code GET /{component}}
     * endpoint. {@code layout} defaults to {@code admin}; {@code menu} puts it in the navigation.
     */
    public record Page(String route, String component, String layout, String capability, Menu menu) {
    }

    /** A page's menu entry. {@code section} is {@code admin} (the default) or {@code agent}. */
    public record Menu(String label, String section, Integer position, String icon) {
    }

    /** Fills a slot ({@code "{page}.{slot}"}) on a shared page. {@code order} defaults to 100. */
    public record SlotComponent(
            String page, String slot, String component, Map<String, Object> props, Integer order, String capability) {
    }

    /** A dashboard widget. {@code size} is full, half (the default) or quarter. */
    public record Widget(String component, String label, String size, Integer order, String capability) {
    }
}
