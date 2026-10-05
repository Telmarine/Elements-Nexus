package com.tac5studios.elementsnexus.help;

import com.tac5studios.elementsnexus.config.TomlFile;

import java.util.Locale;

/** help.toml */
public final class Help {

    private static final TomlFile FILE = new TomlFile("help.toml");

    private Help() {}

    public static void load() {
        FILE.load();
    }

    public static String header() { return FILE.str("display.header", "&6&l✦ Help ✦ &7(page {page}/{pages})"); }
    public static String line() { return FILE.str("display.line", "&e/{command} &7- &f{description}"); }
    public static String noDescription() { return FILE.str("display.no_description", ""); }
    public static int perPage() { return Math.max(1, FILE.num("display.per_page", 10)); }
    public static boolean showOther() { return FILE.bool("display.show_other_commands", true); }

    public static boolean hidden(String command) {
        for (String h : FILE.strings("display.hidden")) if (h.equalsIgnoreCase(command)) return true;
        return false;
    }

    /** The description from help.toml, or null. */
    public static String description(String command) {
        String d = FILE.str("descriptions." + command.toLowerCase(Locale.ROOT), null);
        return d == null || d.isEmpty() ? null : d;
    }
}
