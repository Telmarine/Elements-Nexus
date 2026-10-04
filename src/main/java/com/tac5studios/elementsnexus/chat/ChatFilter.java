package com.tac5studios.elementsnexus.chat;

import com.electronwill.nightconfig.core.Config;
import com.tac5studios.elementsnexus.config.TomlFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Word filter from chat_filter.toml.
 *  block = message is not sent
 *  hide  = message is sent with the word turned into stars
 *  allow = real words that contain a blocked word (never filtered)
 */
public final class ChatFilter {

    public enum Kind { OK, HIDE, BLOCK }

    /** What the filter decided, plus the cleaned text for HIDE. */
    public record Result(Kind kind, String text, String word) {}

    private record Entry(String word, boolean root) {}

    private static final TomlFile FILE = new TomlFile("chat_filter.toml");

    private static boolean enabled = true;
    private static boolean tricks = true;
    private static boolean alertStaff = true;
    private static String bypass = "nexus.chat.filter.bypass";
    private static String stars = "*";
    private static final List<Entry> BLOCK = new ArrayList<>();
    private static final List<Entry> HIDE = new ArrayList<>();
    private static final List<String> ALLOW = new ArrayList<>();

    private ChatFilter() {}

    public static void load() {
        FILE.load();
        enabled = FILE.bool("settings.enabled", true);
        tricks = FILE.bool("settings.catch_tricks", true);
        alertStaff = FILE.bool("settings.alert_staff", true);
        bypass = FILE.str("settings.bypass_permission", "nexus.chat.filter.bypass");
        stars = FILE.str("hide.replace_with", "*");
        read("block.words", BLOCK);
        read("hide.words", HIDE);
        ALLOW.clear();
        for (String s : FILE.strings("allow.words")) ALLOW.add(s.toLowerCase(Locale.ROOT));
    }

    private static void read(String key, List<Entry> into) {
        into.clear();
        for (Config c : FILE.tables(key)) {
            Object w = c.get("word");
            if (!(w instanceof String word) || word.isBlank()) continue;
            Object m = c.get("match");
            String clean = word.toLowerCase(Locale.ROOT).trim();
            if (tricks) clean = clean.replace('v', 'u'); // messages read "v" as "u" too
            into.add(new Entry(clean, !"whole".equals(m)));
        }
    }

    public static boolean enabled() { return enabled; }
    public static boolean alertStaff() { return alertStaff; }
    public static String bypassNode() { return bypass; }

    /** Check one message. */
    public static Result check(String message) {
        if (!enabled || message.isBlank()) return new Result(Kind.OK, message, null);

        String[] tokens = message.split(" ", -1);
        List<String> norm = new ArrayList<>();
        for (String t : tokens) norm.add(normalize(t));

        // 1) Blocked words, word by word.
        for (String n : norm) {
            String hit = find(n, BLOCK);
            if (hit != null) return new Result(Kind.BLOCK, message, hit);
        }
        // 2) Blocked words spelled with spaces between letters: "f u c k".
        if (tricks) {
            String hit = findSpacedOut(norm);
            if (hit != null) return new Result(Kind.BLOCK, message, hit);
        }
        // 3) Blocked phrases with spaces in them: "kill yourself".
        String joined = " " + String.join(" ", norm) + " ";
        for (Entry e : BLOCK) {
            if (e.word().contains(" ") && joined.contains(" " + e.word() + (e.root() ? "" : " "))) {
                return new Result(Kind.BLOCK, message, e.word());
            }
        }

        // 4) Hidden words: replace the whole word with stars.
        boolean changed = false;
        for (int i = 0; i < tokens.length; i++) {
            if (find(norm.get(i), HIDE) != null) {
                tokens[i] = stars.repeat(Math.max(1, tokens[i].length()));
                changed = true;
            }
        }
        return changed ? new Result(Kind.HIDE, String.join(" ", tokens), null) : new Result(Kind.OK, message, null);
    }

    /** The list word found in one normalized word, or null. */
    private static String find(String n, List<Entry> list) {
        if (n.isEmpty()) return null;
        String two = squeeze(n, 2);
        String one = squeeze(n, 1);
        for (Entry e : list) {
            if (e.word().contains(" ")) continue;
            if (n.contains("*")) {
                if (wildcard(n, e.word())) return e.word();
                continue;
            }
            boolean noDoubles = squeeze(e.word(), 1).equals(e.word());
            if (e.root()) {
                if ((two.contains(e.word()) || (noDoubles && one.contains(e.word()))) && !allowed(two, e.word())) return e.word();
            } else {
                if (two.equals(e.word()) || (noDoubles && one.equals(e.word()))) return e.word();
            }
        }
        return null;
    }

    /** "f*ck" style: each * stands for up to two letters. */
    private static boolean wildcard(String n, String word) {
        String clean = n.replaceAll("[^a-z*]", "");
        if (clean.replace("*", "").length() < 2) return false;
        String regex = Pattern.quote(clean).replace("*", "\\E[a-z]{0,2}\\Q");
        return word.matches(regex);
    }

    private static String findSpacedOut(List<String> norm) {
        StringBuilder run = new StringBuilder();
        for (String n : norm) {
            if (n.length() == 1) {
                run.append(n);
                continue;
            }
            String hit = run.length() >= 3 ? find(run.toString(), BLOCK) : null;
            if (hit != null) return hit;
            run.setLength(0);
        }
        return run.length() >= 3 ? find(run.toString(), BLOCK) : null;
    }

    /** True if an allow-list word explains this match (e.g. "grape" contains "rape"). */
    private static boolean allowed(String n, String blocked) {
        for (String a : ALLOW) if (a.contains(blocked) && n.contains(a)) return true;
        return false;
    }

    /** Lowercase, read numbers/symbols as letters, drop everything else (keeps * for wildcards). */
    static String normalize(String token) {
        StringBuilder sb = new StringBuilder(token.length());
        for (char c : token.toLowerCase(Locale.ROOT).toCharArray()) {
            if (tricks) {
                switch (c) {
                    case '0' -> { sb.append('o'); continue; }
                    case '1', '!', '|' -> { sb.append('i'); continue; }
                    case '3' -> { sb.append('e'); continue; }
                    case '4', '@' -> { sb.append('a'); continue; }
                    case '5', '$' -> { sb.append('s'); continue; }
                    case '7' -> { sb.append('t'); continue; }
                    case 'v' -> { sb.append('u'); continue; } // "fvck"
                    default -> { }
                }
            }
            if ((c >= 'a' && c <= 'z') || c == '*') sb.append(c);
        }
        return sb.toString();
    }

    /** Shorten runs of the same letter to at most 'max'. */
    private static String squeeze(String s, int max) {
        StringBuilder sb = new StringBuilder();
        int run = 0;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && s.charAt(i) == s.charAt(i - 1)) run++;
            else run = 1;
            if (run <= max) sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}
