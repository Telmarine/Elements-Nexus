package com.tac5studios.elementsnexus.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns &-color text (&a, &l, &#FFD700) into Minecraft text.
 * Web links are clickable:
 *   https://example.com                -> the link itself is clickable
 *   [Vote here](https://example.com)   -> "Vote here" is clickable, the address is hidden
 * Links work inside colors and gradients. Player-written text uses {@link #colorNoLinks}.
 */
public final class Text {

    private static final Pattern LABELED = Pattern.compile("\\[([^\\]\\n]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern BARE = Pattern.compile("https?://[^\\s<>\"]+");
    private static final String TRAILING = ".,!?;:)]}'\"";

    private Text() {}

    /** Colors + clickable links. Use for text written by the server owner (config files, messages). */
    public static MutableComponent color(String raw) {
        return build(raw, true);
    }

    /** Colors only. Use for text written by players, so chat.toml's link rules still apply. */
    public static MutableComponent colorNoLinks(String raw) {
        return build(raw, false);
    }

    private static MutableComponent build(String raw, boolean links) {
        MutableComponent out = Component.empty();
        if (raw == null || raw.isEmpty()) return out;

        // 1. Visible characters, each with its style.
        StringBuilder chars = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        Style style = Style.EMPTY;
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                // Hex color: &#RRGGBB
                if (raw.charAt(i + 1) == '#' && i + 8 <= raw.length()) {
                    String hex = raw.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(hex, 16)));
                        i += 8;
                        continue;
                    }
                }
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(raw.charAt(i + 1)));
                if (f != null) {
                    if (f == ChatFormatting.RESET) style = Style.EMPTY;
                    else if (f.isColor()) style = Style.EMPTY.withColor(f); // a new color clears bold etc. (same as vanilla)
                    else style = style.applyFormat(f);
                    i += 2;
                    continue;
                }
            }
            chars.append(c);
            styles.add(style);
            i++;
        }

        // 2. Links: which characters open which address, and which are hidden.
        int n = chars.length();
        String[] url = new String[n];
        boolean[] hidden = new boolean[n];
        if (links && n > 0 && chars.indexOf("http") >= 0) {
            String text = chars.toString();
            Matcher m = LABELED.matcher(text);
            while (m.find()) {
                for (int k = m.start(); k < m.end(); k++) {
                    if (k >= m.start(1) && k < m.end(1)) url[k] = m.group(2);
                    else hidden[k] = true;
                }
            }
            m = BARE.matcher(text);
            while (m.find()) {
                int end = m.end();
                while (end > m.start() && TRAILING.indexOf(text.charAt(end - 1)) >= 0) end--;
                if (end - m.start() < 10) continue;
                boolean used = false;
                for (int k = m.start(); k < end; k++) {
                    if (url[k] != null || hidden[k]) {
                        used = true;
                        break;
                    }
                }
                if (used) continue;
                String u = text.substring(m.start(), end);
                for (int k = m.start(); k < end; k++) url[k] = u;
            }
        }

        // 3. Join runs of the same style and link.
        StringBuilder buf = new StringBuilder();
        Style runStyle = null;
        String runUrl = null;
        for (int k = 0; k < n; k++) {
            if (hidden[k]) continue;
            Style s = styles.get(k);
            if (buf.length() > 0 && (!s.equals(runStyle) || !Objects.equals(url[k], runUrl))) {
                append(out, buf, runStyle, runUrl);
            }
            runStyle = s;
            runUrl = url[k];
            buf.append(chars.charAt(k));
        }
        append(out, buf, runStyle, runUrl);
        return out;
    }

    private static void append(MutableComponent out, StringBuilder buf, Style style, String url) {
        if (buf.length() == 0) return;
        Style s = style == null ? Style.EMPTY : style;
        if (url != null) {
            s = s.withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url).withStyle(ChatFormatting.GRAY)));
        }
        out.append(Component.literal(buf.toString()).setStyle(s));
        buf.setLength(0);
    }
}
