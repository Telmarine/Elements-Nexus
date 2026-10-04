package com.tac5studios.elementsnexus.ranks;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.storage.DataStore;
import com.tac5studios.elementsnexus.storage.Storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ranks and the people in them.
 *
 * How a permission is decided:
 *  1. Look at the player's own extra nodes, then their rank, then the ranks it
 *     inherits from (nearest first).
 *  2. The first of those that mentions the node decides.
 *  3. Inside one rank, the most exact match wins (a.b.c beats a.b.* beats *).
 *     If "node" and "-node" are equally exact, deny wins.
 *  4. If nothing mentions the node, the node's own default is used.
 */
public final class Ranks {

    public static final String RANKS = "ranks";
    public static final String USERS = "users";
    public static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_]{1,32}");

    /** A decision plus where it came from (for /rank check). */
    public record Result(boolean allowed, String source) {}

    /** Cached permission layers per player. Cleared whenever ranks or users change. */
    private static final Map<UUID, List<Layer>> CACHE = new HashMap<>();

    private record Layer(String source, List<String> nodes) {}

    private Ranks() {}

    private static DataStore store() {
        return Storage.get();
    }

    // ---------- setup ----------

    /** Make sure a default rank exists. Called when the server starts. */
    public static void ensureDefault() {
        if (defaultRankName() != null) return;
        if (!store().has(RANKS, "member")) {
            Rank r = new Rank();
            r.prefix = "&7[Member] ";
            r.isDefault = true;
            store().put(RANKS, "member", r);
            ElementsNexus.LOGGER.info("[Nexus] Created the starting rank 'member' (default).");
        } else {
            Rank r = rank("member");
            r.isDefault = true;
            store().put(RANKS, "member", r);
        }
        changed();
    }

    // ---------- ranks ----------

    public static List<String> names() {
        List<String> n = store().keys(RANKS);
        n.sort(Comparator.comparingInt((String k) -> -rank(k).priority).thenComparing(k -> k));
        return n;
    }

    public static Rank rank(String name) {
        return store().get(RANKS, name.toLowerCase(Locale.ROOT), Rank.class);
    }

    public static boolean exists(String name) {
        return store().has(RANKS, name.toLowerCase(Locale.ROOT));
    }

    public static void saveRank(String name, Rank r) {
        store().put(RANKS, name.toLowerCase(Locale.ROOT), r);
        changed();
    }

    public static void deleteRank(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        store().remove(RANKS, n);
        // Remove it from every other rank's inherit list.
        for (String other : store().keys(RANKS)) {
            Rank r = rank(other);
            if (r.inherits.remove(n)) store().put(RANKS, other, r);
        }
        changed();
    }

    public static String defaultRankName() {
        for (String k : store().keys(RANKS)) {
            Rank r = rank(k);
            if (r != null && r.isDefault) return k;
        }
        return null;
    }

    public static void setDefault(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String k : store().keys(RANKS)) {
            Rank r = rank(k);
            boolean want = k.equals(n);
            if (r.isDefault != want) {
                r.isDefault = want;
                store().put(RANKS, k, r);
            }
        }
        changed();
    }

    /** True if making 'rank' inherit 'parent' would create a loop. */
    public static boolean wouldLoop(String rank, String parent) {
        Deque<String> todo = new ArrayDeque<>(List.of(parent));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String n = todo.poll();
            if (n.equals(rank)) return true;
            if (!seen.add(n)) continue;
            Rank r = rank(n);
            if (r != null) todo.addAll(r.inherits);
        }
        return false;
    }

    // ---------- users ----------

    public static UserData user(UUID id) {
        UserData u = store().get(USERS, id.toString(), UserData.class);
        return u != null ? u : new UserData();
    }

    public static void saveUser(UUID id, UserData u) {
        store().put(USERS, id.toString(), u);
        changed();
    }

    /** The rank a player is really in (falls back to the default rank). */
    public static String rankOf(UUID id) {
        String r = user(id).rank;
        if (r != null && !r.isEmpty() && exists(r)) return r;
        String d = defaultRankName();
        return d != null ? d : "";
    }

    public static void setRank(UUID id, String name, String rank) {
        UserData u = user(id);
        if (name != null && !name.isEmpty()) u.name = name;
        u.rank = rank.toLowerCase(Locale.ROOT);
        saveUser(id, u);
    }

    /** Remember the player's current name and IP (called on join). */
    public static void rememberName(UUID id, String name, String ip) {
        UserData u = user(id);
        if (!name.equals(u.name) || (ip != null && !ip.equals(u.lastIp))) {
            u.name = name;
            if (ip != null) u.lastIp = ip;
            store().put(USERS, id.toString(), u); // name/IP changes do not affect permissions
        }
    }

    /** The rank and every rank it inherits from, nearest first. */
    public static List<String> chain(String start) {
        List<String> out = new ArrayList<>();
        Deque<String> todo = new ArrayDeque<>();
        if (start != null && !start.isEmpty()) todo.add(start);
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String n = todo.poll();
            if (!seen.add(n)) continue;
            Rank r = rank(n);
            if (r == null) continue;
            out.add(n);
            todo.addAll(r.inherits);
        }
        return out;
    }

    // ---------- permission checks ----------

    /** Decide a node for a player. null = nothing mentions it, use the node's default. */
    public static Result check(UUID id, String node) {
        for (Layer layer : layers(id)) {
            Boolean v = match(layer.nodes(), node);
            if (v != null) return new Result(v, layer.source());
        }
        return null;
    }

    /** Every allowed node a player gets from their own list and their ranks (denies left out). */
    public static List<String> allowedNodes(UUID id) {
        List<String> out = new ArrayList<>();
        for (Layer layer : layers(id)) {
            for (String n : layer.nodes()) if (!n.startsWith("-")) out.add(n);
        }
        return out;
    }

    private static synchronized List<Layer> layers(UUID id) {
        List<Layer> cached = CACHE.get(id);
        if (cached != null) return cached;
        List<Layer> out = new ArrayList<>();
        UserData u = user(id);
        if (!u.permissions.isEmpty()) out.add(new Layer("player", List.copyOf(u.permissions)));
        for (String r : chain(rankOf(id))) {
            out.add(new Layer("rank " + r, List.copyOf(rank(r).permissions)));
        }
        CACHE.put(id, out);
        return out;
    }

    /** Most exact match in one list. TRUE / FALSE / null (no match). */
    static Boolean match(List<String> entries, String node) {
        int best = -1;
        Boolean result = null;
        for (String e : entries) {
            boolean deny = e.startsWith("-");
            String p = deny ? e.substring(1) : e;
            int score;
            if (p.equals(node)) score = Integer.MAX_VALUE;
            else if (p.equals("*")) score = 0;
            else if (p.endsWith(".*") && node.startsWith(p.substring(0, p.length() - 1))) score = p.length();
            else continue;
            if (score > best || (score == best && deny)) {
                best = score;
                result = !deny;
            }
        }
        return result;
    }

    /** Clear cached permissions. Called after any change. */
    public static synchronized void changed() {
        CACHE.clear();
    }
}
