package com.tac5studios.elementsnexus.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reading and writing lengths of time like 30s, 10m, 2h, 1d, 1w (or 1d12h). */
public final class Time {

    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])");

    private Time() {}

    /** Seconds, or null if the text isn't a time. */
    public static Long parse(String text) {
        String t = text.toLowerCase(Locale.ROOT).trim();
        Matcher m = PART.matcher(t);
        long total = 0;
        int pos = 0;
        while (m.find()) {
            if (m.start() != pos) return null;
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "m" -> n * 60;
                case "h" -> n * 3600;
                case "d" -> n * 86400;
                case "w" -> n * 604800;
                default -> n;
            };
            pos = m.end();
        }
        return pos == t.length() && pos > 0 && total > 0 ? total : null;
    }

    public static String text(long seconds) {
        if (seconds <= 0) return "0s";
        long w = seconds / 604800, d = seconds % 604800 / 86400, h = seconds % 86400 / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (w > 0) sb.append(w).append("w ");
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 && w == 0 && d == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
