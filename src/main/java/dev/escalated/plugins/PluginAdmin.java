package dev.escalated.plugins;

import java.util.Map;
import org.springframework.security.core.Authentication;

/** What the admin screens ask of the registry: the lifecycle routes and the plugins' pages. */
public final class PluginAdmin {

    private PluginAdmin() {
    }

    public enum Action {
        ACTIVATE, DEACTIVATE, UNINSTALL
    }

    /** A lifecycle route's result: the flash to show, and whether the slug was unknown. */
    public record Outcome(boolean success, String message, boolean unknown, Exception failure) {
    }

    public static Outcome apply(PluginRegistry registry, String slug, Action action, Authentication actor) {
        try {
            switch (action) {
                case ACTIVATE -> registry.activate(slug, actor);
                case DEACTIVATE -> registry.deactivate(slug, actor);
                default -> registry.uninstall(slug, actor);
            }
        } catch (PluginRegistry.UnknownPluginException ex) {
            return new Outcome(false, "Plugin not found.", true, null);
        } catch (PluginRegistry.PackageManagedException ex) {
            return new Outcome(false, "This plugin is installed by the package manager; remove it there.", false, null);
        } catch (RuntimeException ex) {
            return new Outcome(false, "The plugin could not be changed.", false, ex);
        }
        String message = switch (action) {
            case ACTIVATE -> "Plugin activated.";
            case DEACTIVATE -> "Plugin deactivated.";
            default -> "Plugin uninstalled.";
        };
        return new Outcome(true, message, false, null);
    }

    /** A page to render, or one the viewer may not see ({@code forbidden}). */
    public record Page(String plugin, String component, String layout, Map<String, Object> props, boolean forbidden) {
    }

    /** An active plugin's page, or null (404) when the plugin is inactive or has no such page. */
    public static Page page(PluginRegistry registry, String slug, String route, Authentication actor) {
        PluginRegistry.ResolvedPage resolved = registry.page(slug, route);
        if (resolved == null) {
            return null;
        }
        PluginManifest.Page page = resolved.page();
        if (!registry.allows(actor, page.capability())) {
            return new Page(resolved.entry().name, page.component(), null, Map.of(), true);
        }
        String layout = page.layout() == null || page.layout().isBlank() ? "admin" : page.layout();
        return new Page(resolved.entry().name, page.component(), layout,
                registry.pageProps(resolved.entry(), page, actor), false);
    }
}
