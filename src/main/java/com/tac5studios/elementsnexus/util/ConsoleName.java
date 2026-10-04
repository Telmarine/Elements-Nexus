package com.tac5studios.elementsnexus.util;

import com.tac5studios.elementsnexus.config.Features;
import net.minecraft.network.chat.Component;

/** The name the console shows up as in chat ("Server" unless changed in features.toml). */
public final class ConsoleName {

    private ConsoleName() {}

    /** With & colors, for our own message formats. */
    public static String raw() {
        if (!Features.on("console_name")) return "Server";
        String n = Features.CONSOLE_NAME.get();
        return n == null || n.isBlank() ? "Server" : n;
    }

    /** Without colors (logs, ban lists). */
    public static String plain() {
        return Text.color(raw()).getString();
    }

    public static Component component() {
        return Text.color(raw());
    }
}
