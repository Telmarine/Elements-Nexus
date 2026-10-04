package com.tac5studios.elementsnexus.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extra text styles for config files, turned into &-codes before coloring:
 *   <smallcaps>Text</smallcaps>                 -> ᴛᴇxᴛ
 *   <gradient:#FFD700:#FF4D2E>Text</gradient>   -> each letter shades from one color to the next
 *   <shimmer:#FFD700:#FF4D2E>Text</shimmer>     -> same, but the colors slowly move (animated)
 */
public final class Fancy {

    private static final Pattern SMALLCAPS = Pattern.compile("<smallcaps>(.*?)</smallcaps>", Pattern.DOTALL);
    private static final Pattern GRADIENT = Pattern.compile("<(gradient|shimmer)((?::#[0-9a-fA-F]{6})+)>(.*?)</\\1>", Pattern.DOTALL);

    private static final String FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private Fancy() {}

    /** Apply all styles. phase = animation step (0..1), moves shimmer colors. */
    public static String apply(String text, double phase) {
        if (text == null || text.indexOf('<') < 0) return text;
        Matcher m = SMALLCAPS.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(smallCaps(m.group(1))));
        m.appendTail(sb);
        text = sb.toString();

        m = GRADIENT.matcher(text);
        sb = new StringBuilder();
        while (m.find()) {
            boolean moving = m.group(1).equals("shimmer");
            List<int[]> stops = parseStops(m.group(2));
            m.appendReplacement(sb, Matcher.quoteReplacement(gradient(m.group(3), stops, moving ? phase : -1)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Turn letters into small caps. Leaves &-codes alone. */
    public static String smallCaps(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) { // keep color codes as they are
                out.append(c).append(s.charAt(i + 1));
                i++;
                continue;
            }
            int k = FROM.indexOf(Character.toLowerCase(c));
            out.append(k >= 0 ? TO.charAt(k) : c);
        }
        return out.toString();
    }

    private static List<int[]> parseStops(String raw) {
        List<int[]> stops = new ArrayList<>();
        for (String part : raw.split(":")) {
            if (part.isEmpty()) continue;
            int rgb = Integer.parseInt(part.substring(1), 16);
            stops.add(new int[]{(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255});
        }
        if (stops.size() == 1) stops.add(stops.get(0));
        return stops;
    }

    /** Color each letter. phase < 0 = fixed gradient; phase >= 0 = looping, shifted by phase. */
    private static String gradient(String s, List<int[]> stops, double phase) {
        // Count visible letters (skip &-codes inside).
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '&' && i + 1 < s.length()) { i++; continue; }
            n++;
        }
        if (n == 0) return s;
        StringBuilder out = new StringBuilder();
        int idx = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) { i++; continue; } // drop inner codes; the gradient sets color
            double t;
            int[] rgb;
            if (phase < 0) {
                t = n == 1 ? 0 : (double) idx / (n - 1);
                rgb = at(stops, t, false);
            } else {
                t = ((double) idx / n + phase) % 1.0;
                rgb = at(stops, t, true);
            }
            out.append(String.format("&#%02X%02X%02X", rgb[0], rgb[1], rgb[2])).append(c);
            idx++;
        }
        return out.toString();
    }

    /** Color at position t (0..1). loop = last color blends back into the first. */
    private static int[] at(List<int[]> stops, double t, boolean loop) {
        int segments = loop ? stops.size() : stops.size() - 1;
        double pos = t * segments;
        int a = Math.min((int) Math.floor(pos), segments - 1);
        double f = pos - a;
        int[] c1 = stops.get(a % stops.size());
        int[] c2 = stops.get((a + 1) % stops.size());
        return new int[]{
                (int) Math.round(c1[0] + (c2[0] - c1[0]) * f),
                (int) Math.round(c1[1] + (c2[1] - c1[1]) * f),
                (int) Math.round(c1[2] + (c2[2] - c1[2]) * f)};
    }
}
