package dev.escalated.plugins;

import java.util.regex.Pattern;

/** Plugin slugs, as the plugin contract defines them. */
public final class PluginSlugs {

    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9_-]");

    private PluginSlugs() {
    }

    /**
     * A plugin's slug: its name without a leading {@code @}, each {@code /} as {@code --}, and
     * anything else outside {@code [A-Za-z0-9_-]} as {@code -}. So {@code @escalated-dev/plugin-slack}
     * is {@code escalated-dev--plugin-slack}.
     */
    public static String slug(String name) {
        String value = name.startsWith("@") ? name.substring(1) : name;
        return UNSAFE.matcher(value.replace("/", "--")).replaceAll("-");
    }
}
