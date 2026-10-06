package com.tac5studios.elementsnexus.hooks;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.config.SidePanelConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coin balance for mods that use coin items (like Aiycoin).
 * Counts the coins a player carries, including coins inside boxes and bags in their inventory.
 * When none of the coins in sidepanel.toml exist, coin items from installed mods are found on their own.
 */
public final class Coins {

    private static final Pattern COLORS = Pattern.compile("^((?:&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR])*)(.*)$");

    private record Coin(Item item, String color, String letter) {}

    /** One coin type and how many the player has. */
    public record Part(String color, String letter, String name, long amount) {}

    /** Item ids that look like coins: "coin", "coins", "gold_coin", "coin_bronze"... */
    private static final Pattern COIN_ID = Pattern.compile("(^|_)coins?($|_)");
    /** Holders and non-money items that also have "coin" in the id. */
    private static final Pattern NOT_COIN = Pattern.compile("bag|pouch|box|purse|wallet|stack|pile|mold|press|block|key|token|chest|vault|machine");
    private static final int AUTO_MAX = 6;
    private static boolean autoLogged;

    private static List<Coin> coins;
    private static List<? extends String> builtFrom;

    private Coins() {}

    /** The coin types from sidepanel.toml that exist on this server. */
    private static List<Coin> coins() {
        List<? extends String> cfg = SidePanelConfig.COINS.get();
        if (coins == null || cfg != builtFrom) {
            List<Coin> out = new ArrayList<>();
            for (String entry : cfg) {
                int eq = entry.indexOf('=');
                if (eq < 0) continue;
                ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, eq).trim());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
                Matcher m = COLORS.matcher(entry.substring(eq + 1).trim());
                if (!m.matches()) continue;
                out.add(new Coin(BuiltInRegistries.ITEM.get(id), m.group(1), m.group(2)));
            }
            if (out.isEmpty()) out = autoDetect();
            coins = out;
            builtFrom = cfg;
        }
        return coins;
    }

    /**
     * No coin from the list exists: look for coin items from installed mods.
     * The mod with the most coin items wins (one money system), in the order the mod registered them.
     */
    private static List<Coin> autoDetect() {
        Map<String, List<Item>> byMod = new LinkedHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals("minecraft")) continue;
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (!COIN_ID.matcher(path).find() || NOT_COIN.matcher(path).find()) continue;
            byMod.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(item);
        }
        List<Item> best = List.of();
        for (List<Item> items : byMod.values()) if (items.size() > best.size()) best = items;
        List<Coin> out = new ArrayList<>();
        for (Item item : best) {
            if (out.size() >= AUTO_MAX) break;
            String name = new ItemStack(item).getHoverName().getString();
            out.add(new Coin(item, "&f", name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT)));
        }
        if (!out.isEmpty() && !autoLogged) {
            autoLogged = true;
            ElementsNexus.LOGGER.info("[Nexus] No coins from sidepanel.toml found. Using coin items from {}: {}. List them in [balance] coins to pick colors and order.",
                    BuiltInRegistries.ITEM.getKey(out.get(0).item()).getNamespace(),
                    String.join(", ", out.stream().map(c -> BuiltInRegistries.ITEM.getKey(c.item()).toString()).toList()));
        }
        return out;
    }

    /** True if at least one coin from the list exists on this server. */
    public static boolean available() {
        return !coins().isEmpty();
    }

    /** e.g. "B: 12 S: 4 G: 30", each in its coin's color. */
    public static String balance(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        for (Part c : parts(p)) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(c.color()).append(c.letter()).append(": ").append(c.amount());
        }
        return sb.isEmpty() ? "&70" : sb.toString();
    }

    /** Each coin type the player should see, with its amount (coins they have none of are left out if show_empty is off). */
    public static List<Part> parts(ServerPlayer p) {
        List<Coin> list = coins();
        Map<Item, Long> counts = new LinkedHashMap<>();
        for (Coin c : list) counts.put(c.item(), 0L);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) count(inv.getItem(i), counts, 0);
        boolean showEmpty = SidePanelConfig.SHOW_EMPTY_COINS.get();
        List<Part> out = new ArrayList<>();
        for (Coin c : list) {
            long n = counts.get(c.item());
            if (n == 0 && !showEmpty) continue;
            out.add(new Part(c.color(), c.letter(), new ItemStack(c.item()).getHoverName().getString(), n));
        }
        return out;
    }

    private static void count(ItemStack s, Map<Item, Long> counts, int depth) {
        if (s.isEmpty()) return;
        Long have = counts.get(s.getItem());
        if (have != null) counts.put(s.getItem(), have + s.getCount());
        if (depth < 2) { // coin boxes and bags keep their coins in a container
            ItemContainerContents inside = s.get(DataComponents.CONTAINER);
            if (inside != null) for (ItemStack in : inside.nonEmptyItems()) count(in, counts, depth + 1);
        }
    }
}
