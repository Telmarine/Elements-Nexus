package com.tac5studios.elementsnexus.config;

import com.tac5studios.elementsnexus.ElementsNexus;
import net.neoforged.fml.ModList;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Overlap guard: when a mod that does the same job is installed, Nexus turns its own
 * matching features off so the two don't fight. force_on in features.toml turns them back on.
 */
public final class Overlap {

    /** mod id -> label and the Nexus features (or "feature.switch") it replaces. */
    private record Other(String label, List<String> features) {}

    private static final Map<String, Other> OTHERS = new LinkedHashMap<>();

    static {
        OTHERS.put("neoessentials", new Other("NeoEssentials", List.of(
                "ranks.permission_handler", "tablist", "chat", "messaging", "staffchat", "homes", "tpa", "teleport",
                "spawn", "back", "warps", "afk", "kits", "moderation", "vanish", "nicknames", "rules", "help",
                "broadcast", "announcements", "messages", "holograms")));
        OTHERS.put("tab", new Other("TAB", List.of("tablist")));
        OTHERS.put("luckperms", new Other("LuckPerms", List.of("ranks.permission_handler")));
        OTHERS.put("ftbranks", new Other("FTB Ranks", List.of("ranks.permission_handler")));
        OTHERS.put("ftbessentials", new Other("FTB Essentials", List.of(
                "homes", "tpa", "teleport", "spawn", "back", "warps", "kits", "nicknames", "moderation.mute")));
    }

    /** Features turned off, and which mod caused it. Worked out once at startup. */
    private static Map<String, String> blocked;

    private Overlap() {}

    private static Map<String, String> blocked() {
        if (blocked == null) {
            Map<String, String> b = new LinkedHashMap<>();
            ModList mods = ModList.get();
            if (mods != null) {
                for (Map.Entry<String, Other> e : OTHERS.entrySet()) {
                    if (!mods.isLoaded(e.getKey())) continue;
                    for (String f : e.getValue().features()) b.putIfAbsent(f, e.getValue().label());
                }
            }
            blocked = b;
        }
        return blocked;
    }

    /** True if this feature (or "feature.switch") is turned off by another mod. */
    public static boolean blocks(String key) {
        if (!Features.overlapGuardOn()) return false;
        if (!blocked().containsKey(key)) return false;
        String feature = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
        Set<String> force = new LinkedHashSet<>(Features.forceOn());
        return !force.contains(key) && !force.contains(feature);
    }

    /** Write one warning per other mod found. Called once at server start. */
    public static void logWarnings() {
        if (!Features.overlapGuardOn()) return;
        Map<String, List<String>> byMod = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : blocked().entrySet()) {
            if (blocks(e.getKey())) byMod.computeIfAbsent(e.getValue(), k -> new java.util.ArrayList<>()).add(e.getKey());
        }
        for (Map.Entry<String, List<String>> e : byMod.entrySet()) {
            ElementsNexus.LOGGER.warn("[Nexus] {} found - Nexus turned off: {}. Use force_on in features.toml to turn any back on.",
                    e.getKey(), String.join(", ", e.getValue()));
        }
    }
}
