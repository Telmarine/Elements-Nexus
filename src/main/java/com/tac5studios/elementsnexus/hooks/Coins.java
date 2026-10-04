package com.tac5studios.elementsnexus.hooks;

import com.tac5studios.elementsnexus.config.SidePanelConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coin balance for mods that use coin items (like Aiycoin).
 * Counts the coins a player carries, including coins inside boxes and bags in their inventory.
 */
public final class Coins {

    private static final Pattern COLORS = Pattern.compile("^((?:&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR])*)(.*)$");

    private record Coin(Item item, String color, String letter) {}

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
            coins = out;
            builtFrom = cfg;
        }
        return coins;
    }

    /** True if at least one coin from the list exists on this server. */
    public static boolean available() {
        return !coins().isEmpty();
    }

    /** e.g. "B: 12 S: 4 G: 30", each in its coin's color. */
    public static String balance(ServerPlayer p) {
        List<Coin> list = coins();
        Map<Item, Long> counts = new LinkedHashMap<>();
        for (Coin c : list) counts.put(c.item(), 0L);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) count(inv.getItem(i), counts, 0);
        StringBuilder sb = new StringBuilder();
        boolean showEmpty = SidePanelConfig.SHOW_EMPTY_COINS.get();
        for (Coin c : list) {
            long n = counts.get(c.item());
            if (n == 0 && !showEmpty) continue;
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(c.color()).append(c.letter()).append(": ").append(n);
        }
        return sb.isEmpty() ? "&70" : sb.toString();
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
