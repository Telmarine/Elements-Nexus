package com.tac5studios.elementsnexus.migrate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.tac5studios.elementsnexus.homes.Homes;
import com.tac5studios.elementsnexus.moderation.Punish;
import com.tac5studios.elementsnexus.nick.Nick;
import com.tac5studios.elementsnexus.ranks.Rank;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.ranks.UserData;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.teleport.Teleports.Spot;
import net.minecraft.server.MinecraftServer;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helper for every migration. In preview mode nothing is written, only counted.
 * Data already in Nexus is never overwritten.
 */
public class Importer {

    private static final Pattern HEX = Pattern.compile("&#[0-9a-fA-F]{6}");
    private static final Pattern CODE = Pattern.compile("&[0-9a-fA-F]");
    private static final Pattern RANK_NAME = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern WARP_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    public final MinecraftServer server;
    public final boolean apply;
    /** category -> {added, already in Nexus} */
    private final Map<String, int[]> counts = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();
    /** Rank names created in this run (so users can be assigned to them in preview mode too). */
    private final java.util.Set<String> newRanks = new java.util.HashSet<>();

    public Importer(MinecraftServer server, boolean apply) {
        this.server = server;
        this.apply = apply;
    }

    // ---------- report ----------

    public void added(String category) { counts.computeIfAbsent(category, k -> new int[2])[0]++; }
    public void kept(String category) { counts.computeIfAbsent(category, k -> new int[2])[1]++; }
    public void note(String text) { notes.add(text); }

    public Map<String, int[]> counts() { return counts; }
    public List<String> notes() { return notes; }

    // ---------- reading files ----------

    /** Read a JSON file (comments allowed). Null if missing or broken. */
    public JsonElement json(Path p) {
        if (!Files.isRegularFile(p)) return null;
        try {
            JsonReader r = new JsonReader(new StringReader(Files.readString(p, StandardCharsets.UTF_8)));
            r.setLenient(true);
            return JsonParser.parseReader(r);
        } catch (Exception e) {
            note("Could not read " + p.getFileName() + ": " + e.getMessage());
            return null;
        }
    }

    public JsonObject obj(Path p) {
        JsonElement e = json(p);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    // ---------- small converters ----------

    /** "&#55FF55[Owner]" -> "&#55FF55". Falls back to "&7". */
    public static String colorOf(String prefix) {
        if (prefix == null) return "&7";
        Matcher h = HEX.matcher(prefix);
        Matcher c = CODE.matcher(prefix);
        int hi = h.find() ? h.start() : Integer.MAX_VALUE;
        int ci = c.find() ? c.start() : Integer.MAX_VALUE;
        if (hi == Integer.MAX_VALUE && ci == Integer.MAX_VALUE) return "&7";
        return hi < ci ? h.group() : c.group();
    }

    public static String rankKey(String name) {
        String k = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return RANK_NAME.matcher(k).matches() ? k : null;
    }

    public static String placeKey(String name, Pattern valid) {
        String k = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return valid.matcher(k).matches() ? k : null;
    }

    public static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Find a player's UUID by name: Nexus users first, then the server's name cache. */
    public UUID uuidByName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (String key : Storage.get().keys(Ranks.USERS)) {
            UserData u = Storage.get().get(Ranks.USERS, key, UserData.class);
            if (u != null && name.equalsIgnoreCase(u.name)) return uuid(key);
        }
        var cache = server.getProfileCache();
        if (cache != null) {
            var gp = cache.get(name);
            if (gp.isPresent()) return gp.get().getId();
        }
        return null;
    }

    // ---------- writing (skips anything Nexus already has) ----------

    public boolean rank(String name, Rank r) {
        String k = rankKey(name);
        if (k == null) {
            note("Skipped rank with an unusable name: " + name);
            return false;
        }
        if (Storage.get().has(Ranks.RANKS, k) || newRanks.contains(k)) {
            kept("Ranks");
            return false;
        }
        r.isDefault = false; // the default is set separately
        newRanks.add(k);
        if (apply) Storage.get().put(Ranks.RANKS, k, r);
        added("Ranks");
        return true;
    }

    public boolean rankKnown(String name) {
        String k = rankKey(name);
        return k != null && (newRanks.contains(k) || Storage.get().has(Ranks.RANKS, k));
    }

    public void defaultRank(String name) {
        String k = rankKey(name);
        if (k == null || !rankKnown(k)) return;
        String current = Ranks.defaultRankName();
        if (k.equals(current)) return;
        note("Default rank: " + (current == null ? "(none)" : current) + " -> " + k);
        if (apply) Ranks.setDefault(k);
    }

    /** Give a player a rank, only if Nexus has no rank saved for them. */
    public void userRank(UUID id, String name, String rank) {
        String k = rankKey(rank);
        if (id == null || k == null || !rankKnown(k)) return;
        UserData u = Ranks.user(id);
        if (u.rank != null && !u.rank.isEmpty()) {
            kept("Player ranks");
            return;
        }
        if (apply) {
            if (name != null && !name.isEmpty() && (u.name == null || u.name.isEmpty())) u.name = name;
            u.rank = k;
            Storage.get().put(Ranks.USERS, id.toString(), u);
        }
        added("Player ranks");
    }

    public void home(UUID id, String name, Spot s) {
        String k = placeKey(name, Homes.VALID_NAME);
        if (id == null || k == null || s == null) return;
        TreeMap<String, Spot> homes = Homes.of(id);
        if (homes.containsKey(k)) {
            kept("Homes");
            return;
        }
        homes.put(k, s);
        if (apply) Homes.save(id, homes);
        added("Homes");
    }

    public void warp(String name, Spot s) {
        String k = placeKey(name, WARP_NAME);
        if (k == null || s == null) return;
        put("Warps", "warps", k, s);
    }

    public void spawn(Spot s) {
        if (s != null) put("Spawn", "settings", "spawn", s);
    }

    public void jailSpot(Spot s) {
        if (s != null) put("Jail spot", "settings", "jail", s);
    }

    public void back(UUID id, Spot s) {
        if (id != null && s != null) put("Back locations", "back", id.toString(), s);
    }

    public void nick(UUID id, String nick) {
        if (id == null || nick == null || nick.isBlank()) return;
        put("Nicknames", "nicknames", id.toString(), nick);
    }

    public void kit(String name, com.tac5studios.elementsnexus.kits.Kit kit) {
        String k = placeKey(name, com.tac5studios.elementsnexus.kits.Kits.VALID_NAME);
        if (k != null) put("Kits", "kits", k, kit);
    }

    /** Mark a kit as already claimed by this player (now). */
    public void kitClaimed(UUID id, String kit) {
        if (id == null) return;
        String k = placeKey(kit, com.tac5studios.elementsnexus.kits.Kits.VALID_NAME);
        if (k == null) return;
        Map<String, Long> u = com.tac5studios.elementsnexus.kits.Kits.usageView(id);
        if (u.containsKey(k)) {
            kept("Kit claims");
            return;
        }
        if (apply) com.tac5studios.elementsnexus.kits.Kits.markClaimed(id, k);
        added("Kit claims");
    }

    public void mute(UUID id, long until, String reason, String by) {
        if (id == null) return;
        if (until != 0 && until < System.currentTimeMillis()) return; // already over
        Punish p = Punish.of(id);
        if (p.mute != null) {
            kept("Mutes");
            return;
        }
        Punish.Timed t = new Punish.Timed();
        t.until = until;
        t.reason = reason == null ? "" : reason;
        t.by = by == null ? "" : by;
        p.mute = t;
        if (apply) p.save(id);
        added("Mutes");
    }

    public void ban(UUID id, String name, long since, long until, String reason, String by) {
        if (id == null) return;
        if (until != 0 && until < System.currentTimeMillis()) return;
        var list = server.getPlayerList().getBans();
        var profile = new com.mojang.authlib.GameProfile(id, name == null ? "" : name);
        if (list.isBanned(profile)) {
            kept("Bans");
            return;
        }
        if (apply) {
            list.add(new net.minecraft.server.players.UserBanListEntry(profile, new java.util.Date(since > 0 ? since : System.currentTimeMillis()),
                    by, until == 0 ? null : new java.util.Date(until), reason));
        }
        added("Bans");
    }

    public void ipBan(String ip, long since, long until, String reason, String by) {
        if (ip == null || ip.isEmpty()) return;
        if (until != 0 && until < System.currentTimeMillis()) return;
        var list = server.getPlayerList().getIpBans();
        if (list.isBanned(ip)) {
            kept("IP bans");
            return;
        }
        if (apply) {
            list.add(new net.minecraft.server.players.IpBanListEntry(ip, new java.util.Date(since > 0 ? since : System.currentTimeMillis()),
                    by, until == 0 ? null : new java.util.Date(until), reason));
        }
        added("IP bans");
    }

    public void hologram(String id, com.tac5studios.elementsnexus.holograms.Hologram h) {
        String k = placeKey(id, WARP_NAME);
        if (k != null && h != null) put("Holograms", com.tac5studios.elementsnexus.holograms.Holograms.COLLECTION, k, h);
    }

    private void put(String category, String col, String key, Object value) {
        if (Storage.get().has(col, key)) {
            kept(category);
            return;
        }
        if (apply) Storage.get().put(col, key, value);
        added(category);
    }

    /** After a real import: clear caches and refresh what players see. */
    public void finish() {
        if (!apply) return;
        Ranks.changed();
        for (var p : server.getPlayerList().getPlayers()) {
            Nick.forget(p.getUUID());
            p.refreshDisplayName();
        }
        com.tac5studios.elementsnexus.tablist.Tablist.refresh(server);
    }
}
