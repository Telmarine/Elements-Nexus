package com.tac5studios.elementsnexus.kits;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.storage.Storage;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kits ("kits" collection) and when each player last claimed them ("kit_usage" collection). */
public final class Kits {

    public static final String KITS = "kits";
    public static final String USAGE = "kit_usage";
    public static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private static final Type USAGE_TYPE = new TypeToken<HashMap<String, Long>>() {}.getType();
    private static final Pattern TIME = Pattern.compile("(\\d+)([smhd]?)");

    private Kits() {}

    public static List<String> names() {
        List<String> n = Storage.get().keys(KITS);
        n.sort(String::compareTo);
        return n;
    }

    public static Kit get(String name) {
        return Storage.get().get(KITS, name.toLowerCase(Locale.ROOT), Kit.class);
    }

    public static void save(String name, Kit kit) {
        Storage.get().put(KITS, name.toLowerCase(Locale.ROOT), kit);
    }

    public static void delete(String name) {
        Storage.get().remove(KITS, name.toLowerCase(Locale.ROOT));
    }

    // ---------- items ----------

    /** Copy everything in a player's inventory (main, armor, offhand). */
    public static List<JsonElement> fromInventory(ServerPlayer p) {
        RegistryAccess reg = p.server.registryAccess();
        List<JsonElement> out = new ArrayList<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            ItemStack.CODEC.encodeStart(reg.createSerializationContext(JsonOps.INSTANCE), s.copy())
                    .resultOrPartial(err -> ElementsNexus.LOGGER.warn("[Nexus] Could not save an item in a kit: {}", err))
                    .ifPresent(out::add);
        }
        return out;
    }

    /** Give a kit's items. Anything that doesn't fit is dropped at the player's feet. */
    public static int give(ServerPlayer p, Kit kit) {
        RegistryAccess reg = p.server.registryAccess();
        int given = 0;
        for (JsonElement e : kit.items) {
            ItemStack s = ItemStack.CODEC.parse(reg.createSerializationContext(JsonOps.INSTANCE), e)
                    .resultOrPartial(err -> ElementsNexus.LOGGER.warn("[Nexus] Skipped a kit item that no longer exists: {}", err))
                    .orElse(ItemStack.EMPTY);
            if (s.isEmpty()) continue;
            if (!p.getInventory().add(s)) p.drop(s, false);
            given++;
        }
        p.containerMenu.broadcastChanges();
        return given;
    }

    // ---------- claiming ----------

    /** Can this player claim this kit at all (permission)? */
    public static boolean allowed(ServerPlayer p, String name, Kit kit) {
        String node = "nexus.kit." + name;
        if (com.tac5studios.elementsnexus.config.Features.on("ranks", "ops_bypass") && Perm.isOp(p)) return true;
        Ranks.Result r = Ranks.check(p.getUUID(), node);
        if (r != null) return r.allowed();
        return !kit.locked;
    }

    /** Seconds until the kit can be claimed again. 0 = ready now. -1 = already used (one-time kit). */
    public static long waitLeft(UUID id, String name, Kit kit) {
        Long last = usage(id).get(name);
        if (last == null) return 0;
        if (kit.cooldown < 0) return -1;
        long left = (last + kit.cooldown * 1000L - System.currentTimeMillis()) / 1000L;
        return Math.max(0, left);
    }

    public static void markClaimed(UUID id, String name) {
        HashMap<String, Long> u = usage(id);
        u.put(name, System.currentTimeMillis());
        Storage.get().put(USAGE, id.toString(), u);
    }

    /** Forget a player's claims of one kit (for kit resets). */
    public static void clearClaim(UUID id, String name) {
        HashMap<String, Long> u = usage(id);
        if (u.remove(name) != null) Storage.get().put(USAGE, id.toString(), u);
    }

    private static HashMap<String, Long> usage(UUID id) {
        HashMap<String, Long> u = Storage.get().get(USAGE, id.toString(), USAGE_TYPE);
        return u != null ? u : new HashMap<>();
    }

    // ---------- time text ----------

    /** "once" = -1, "0" = 0, "30s" "10m" "2h" "1d" or "1d12h". Returns null if it can't be read. */
    public static Long parseCooldown(String text) {
        String t = text.toLowerCase(Locale.ROOT).trim();
        if (t.equals("once")) return -1L;
        if (t.equals("none")) return 0L;
        Matcher m = TIME.matcher(t);
        long total = 0;
        int pos = 0;
        while (m.find()) {
            if (m.start() != pos) return null;
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "m" -> n * 60;
                case "h" -> n * 3600;
                case "d" -> n * 86400;
                default -> n; // seconds
            };
            pos = m.end();
        }
        return pos == t.length() && pos > 0 ? total : null;
    }

    public static String timeText(long seconds) {
        if (seconds < 0) return "once";
        if (seconds == 0) return "no wait";
        long d = seconds / 86400, h = seconds % 86400 / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 && d == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    public static Map<String, Long> usageView(UUID id) {
        return usage(id);
    }
}
