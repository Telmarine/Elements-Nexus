package com.tac5studios.elementsnexus.sidepanel;

/**
 * How wide a text is in the default Minecraft font, in pixels, so lines can be lined up with spaces.
 * Color codes (&a, &#RRGGBB) and tags (&lt;gradient:...&gt;) take no room; bold adds one pixel per letter.
 */
final class TextWidth {

    /** Width of a space. */
    static final int SPACE = 4;

    private TextWidth() {}

    static int of(String text) {
        int w = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char n = text.charAt(i + 1);
                if (n == '#' && i + 7 < text.length()) { i += 7; continue; }
                char l = Character.toLowerCase(n);
                if ("0123456789abcdefklmnor".indexOf(l) >= 0) {
                    if (l == 'l') bold = true;
                    else if (l == 'r' || "0123456789abcdef".indexOf(l) >= 0) bold = false;
                    i++;
                    continue;
                }
            }
            if (c == '<') {
                int end = text.indexOf('>', i);
                if (end > i) { i = end; continue; }
            }
            w += width(c) + (bold && c != ' ' ? 1 : 0);
        }
        return w;
    }

    /** Advance of one character, including the 1-pixel gap after it. */
    private static int width(char c) {
        return switch (c) {
            case ' ', 'I', 't', '[', ']', '(', ')', '{', '}', '"', '*' -> 4;
            case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'f', 'k', '<', '>' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
