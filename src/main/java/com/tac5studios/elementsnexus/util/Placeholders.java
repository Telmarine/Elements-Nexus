package com.tac5studios.elementsnexus.util;

import com.tac5studios.elementsnexus.chat.TitleHook;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.nick.Nick;
import com.tac5studios.elementsnexus.tablist.Tablist;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.server.level.ServerPlayer;

/**
 * The only placeholders Nexus uses: {player} {name} {rank} {title} {balance} {location} {online}.
 * {player} = nickname if set, {name} = real name.
 */
public final class Placeholders {

    private Placeholders() {}

    public static String apply(String text, ServerPlayer p) {
        if (text == null || p == null) return text;
        String s = text;
        if (s.contains("{player}")) s = s.replace("{player}", Nick.display(p));
        if (s.contains("{name}")) s = s.replace("{name}", p.getGameProfile().getName());
        if (s.contains("{rank}")) s = s.replace("{rank}", Tablist.rankTag(p));
        if (s.contains("{title}")) s = s.replace("{title}", Features.on("chat", "title_scrolls_hook") ? TitleHook.title(p) : "");
        if (s.contains("{balance}")) s = s.replace("{balance}", balance(p));
        if (s.contains("{location}")) s = s.replace("{location}", location(p));
        if (s.contains("{online}")) s = s.replace("{online}", String.valueOf(Vanish.visibleCount(p.server, p)));
        return s;
    }

    /** Where the balance comes from right now: "economy", "coins" or "none". */
    private static String balanceSource() {
        String s = com.tac5studios.elementsnexus.config.SidePanelConfig.BALANCE_SOURCE.get();
        if (s.equals("monopoly")) s = "economy"; // old name in configs from 1.0.0 - 1.0.2
        boolean economy = com.tac5studios.elementsnexus.hooks.EconomyBalance.available();
        if (s.equals("auto")) {
            if (economy) return "economy";
            return com.tac5studios.elementsnexus.hooks.Coins.available() ? "coins" : "none";
        }
        if (s.equals("coins") && !com.tac5studios.elementsnexus.hooks.Coins.available()) return "none";
        if (s.equals("economy") && !economy) return "none";
        return s;
    }

    /** True when the balance comes from coin items (so it can be shown one coin per line). */
    public static boolean balanceIsCoins() {
        return balanceSource().equals("coins");
    }

    /** True when the balance comes from an economy mod (so its parts can be shown one per line). */
    public static boolean balanceIsEconomy() {
        return balanceSource().equals("economy");
    }

    /** True when there is a balance to show. */
    public static boolean hasBalance() {
        return !balanceSource().equals("none");
    }

    public static String balance(ServerPlayer p) {
        return switch (balanceSource()) {
            case "coins" -> com.tac5studios.elementsnexus.hooks.Coins.balance(p);
            case "economy" -> String.join(com.tac5studios.elementsnexus.config.SidePanelConfig.ECONOMY_SEPARATOR.get(), com.tac5studios.elementsnexus.hooks.EconomyBalance.formatted(p));
            default -> "";
        };
    }

    /** True when a land-claim mod is installed. */
    public static boolean hasLocation() {
        return com.tac5studios.elementsnexus.hooks.Claims.available();
    }

    public static String location(ServerPlayer p) {
        var c = com.tac5studios.elementsnexus.hooks.Claims.at(p);
        if (!c.claimed()) return com.tac5studios.elementsnexus.config.SidePanelConfig.WILDERNESS.get();
        String name = c.name().isBlank() ? (c.server() ? "Server land" : "Claimed") : c.name();
        if (name.length() > 24) name = name.substring(0, 23) + "…";
        name = name.replace("&", "&\u200B");
        //noinspection resource
        String fmt = claimStyle(c.name(), p.level().dimension().location().toString());
        if (fmt == null) {
            fmt = c.server() ? com.tac5studios.elementsnexus.config.SidePanelConfig.SERVER_LAND.get()
                    : com.tac5studios.elementsnexus.config.SidePanelConfig.CLAIMED.get();
        }
        return fmt.replace("{claim}", name);
    }

    /** A claim_styles entry for this claim name (checked first) or dimension, or null. */
    private static String claimStyle(String claim, String dimension) {
        String byDim = null;
        for (String entry : com.tac5studios.elementsnexus.config.SidePanelConfig.CLAIM_STYLES.get()) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String key = entry.substring(0, eq).trim();
            if (!claim.isBlank() && key.equalsIgnoreCase(claim.trim())) return entry.substring(eq + 1);
            if (byDim == null && key.equalsIgnoreCase(dimension)) byDim = entry.substring(eq + 1);
        }
        return byDim;
    }
}
