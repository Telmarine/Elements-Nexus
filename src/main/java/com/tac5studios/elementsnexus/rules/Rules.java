package com.tac5studios.elementsnexus.rules;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.config.TomlFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** The rules list from rules.toml. In-game edits rewrite the file (comments included). */
public final class Rules {

    private static final TomlFile FILE = new TomlFile("rules.toml");
    private static final List<String> LIST = new ArrayList<>();

    private Rules() {}

    public static void load() {
        FILE.load();
        LIST.clear();
        LIST.addAll(FILE.strings("rules.list"));
    }

    public static List<String> list() {
        return LIST;
    }

    public static String header() { return FILE.str("display.header", "&6&l✦ Server Rules ✦"); }
    public static String footer() { return FILE.str("display.footer", ""); }
    public static String line() { return FILE.str("display.line", "&e{number}. &f{rule}"); }
    public static int perPage() { return Math.max(1, FILE.num("display.per_page", 10)); }

    /** Save the current list back to rules.toml. Returns false if the file could not be written. */
    public static boolean save() {
        StringBuilder s = new StringBuilder();
        s.append("# ============================================================\n");
        s.append("#  RULES  —  what /rules shows\n");
        s.append("#  Admins can also change the list in game with /rules edit.\n");
        s.append("# ============================================================\n\n");
        s.append("[display]\n");
        s.append("# Lines above and below the rules. Leave empty for none.\n");
        s.append("header = ").append(quote(header())).append('\n');
        s.append("footer = ").append(quote(footer())).append("\n\n");
        s.append("# How each rule looks. Available: {number} {rule}\n");
        s.append("line = ").append(quote(line())).append("\n\n");
        s.append("# Rules shown per page.\n");
        s.append("per_page = ").append(perPage()).append("\n\n");
        s.append("[rules]\n");
        s.append("# One rule per line. & colors work.\n");
        s.append("list = [\n");
        for (String r : LIST) s.append("    ").append(quote(r)).append(",\n");
        s.append("]\n");
        try {
            Files.writeString(FILE.path(), s.toString(), StandardCharsets.UTF_8);
            FILE.load();
            return true;
        } catch (Exception e) {
            ElementsNexus.LOGGER.error("[Nexus] Could not save {}", FILE.path(), e);
            return false;
        }
    }

    private static String quote(String v) {
        StringBuilder q = new StringBuilder("\"");
        for (char c : v.toCharArray()) {
            switch (c) {
                case '\\' -> q.append("\\\\");
                case '"' -> q.append("\\\"");
                case '\n' -> q.append("\\n");
                case '\t' -> q.append("\\t");
                default -> {
                    if (c < 0x20) q.append(String.format("\\u%04x", (int) c));
                    else q.append(c);
                }
            }
        }
        return q.append('"').toString();
    }
}
