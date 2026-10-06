package com.tac5studios.elementsnexus.chat;

import com.tac5studios.elementsnexus.ElementsNexus;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Reads a player's active title from the Title Scrolls mod, if it is installed.
 * Uses reflection so Elements: Nexus never needs Title Scrolls to load.
 */
public final class TitleHook {

    private static boolean tried;
    private static Object attachment;   // AttachmentType<PlayerTitleData>
    private static Object registry;     // TitleReloadListener
    private static Method getDef;       // TitleReloadListener.get(String) -> Optional<TitleDefinition>
    private static Method display;      // TitleDefinition.display()
    private static Method active;       // PlayerTitleData.activeTitle() -> Optional<String>
    private static Method chatColor;    // TitleDefinition.chatColor() (Title Scrolls 1.0.1+), may be missing

    private TitleHook() {}

    /** The active title text with its colors, or "" if none (or Title Scrolls is not installed). */
    public static String title(ServerPlayer player) {
        if (!setup()) return "";
        try {
            @SuppressWarnings("unchecked")
            Object data = player.getData((net.neoforged.neoforge.attachment.AttachmentType<Object>) attachment);
            Optional<?> id = (Optional<?>) active.invoke(data);
            if (id.isEmpty()) return "";
            Optional<?> def = (Optional<?>) getDef.invoke(registry, id.get());
            return def.isEmpty() ? "" : String.valueOf(display.invoke(def.get()));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * The chat text color of the player's active title (the title's "chat_color", e.g. "&b" or "&#FFD700"),
     * or "" if there is none, the title has no chat color, or this Title Scrolls version doesn't have it.
     */
    public static String chatColor(ServerPlayer player) {
        if (!setup() || chatColor == null) return "";
        try {
            @SuppressWarnings("unchecked")
            Object data = player.getData((net.neoforged.neoforge.attachment.AttachmentType<Object>) attachment);
            Optional<?> id = (Optional<?>) active.invoke(data);
            if (id.isEmpty()) return "";
            Optional<?> def = (Optional<?>) getDef.invoke(registry, id.get());
            if (def.isEmpty()) return "";
            Object c = chatColor.invoke(def.get());
            if (c instanceof Optional<?> o) c = o.orElse(null);
            return c == null ? "" : String.valueOf(c).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** True when Title Scrolls is installed and its titles can be read. */
    public static boolean available() {
        return setup();
    }

    private static boolean setup() {
        if (tried) return attachment != null;
        tried = true;
        if (!ModList.get().isLoaded("titlescrolls")) return false;
        try {
            Class<?> attachments = Class.forName("com.tenko.titlescrolls.registry.ModAttachments");
            Object holder = attachments.getField("PLAYER_TITLES").get(null);
            Object type = ((Supplier<?>) holder).get();
            Class<?> main = Class.forName("com.tenko.titlescrolls.TitleScrolls");
            Object reg = main.getField("TITLE_REGISTRY").get(null);
            Method g = reg.getClass().getMethod("get", String.class);
            Class<?> defClass = Class.forName("com.tenko.titlescrolls.data.TitleDefinition");
            Method d = defClass.getMethod("display");
            Class<?> dataClass = Class.forName("com.tenko.titlescrolls.data.PlayerTitleData");
            Method a = dataClass.getMethod("activeTitle");
            try {
                chatColor = defClass.getMethod("chatColor");
            } catch (NoSuchMethodException old) {
                chatColor = null; // Title Scrolls before 1.0.1: titles have no chat color
            }
            registry = reg;
            getDef = g;
            display = d;
            active = a;
            attachment = type;
            ElementsNexus.LOGGER.info("[Nexus] Title Scrolls found. {title} works in chat.");
            return true;
        } catch (Exception e) {
            ElementsNexus.LOGGER.warn("[Nexus] Title Scrolls is installed but its titles could not be read: {}", e.toString());
            return false;
        }
    }
}
