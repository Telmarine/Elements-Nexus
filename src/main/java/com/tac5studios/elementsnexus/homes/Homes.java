package com.tac5studios.elementsnexus.homes;

import com.google.gson.reflect.TypeToken;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.TeleportConfig;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.teleport.Teleports.Spot;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/** Player homes ("homes" collection, key = UUID, value = name -> spot). */
public final class Homes {

    public static final String COLLECTION = "homes";
    public static final String LIMIT_NODE = "nexus.home.limit.";
    public static final int UNLIMITED = Integer.MAX_VALUE;
    public static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,16}");

    private static final Type TYPE = new TypeToken<TreeMap<String, Spot>>() {}.getType();

    private Homes() {}

    public static TreeMap<String, Spot> of(UUID id) {
        TreeMap<String, Spot> m = Storage.get().get(COLLECTION, id.toString(), TYPE);
        return m != null ? m : new TreeMap<>();
    }

    public static void save(UUID id, Map<String, Spot> homes) {
        if (homes.isEmpty()) Storage.get().remove(COLLECTION, id.toString());
        else Storage.get().put(COLLECTION, id.toString(), homes);
    }

    /**
     * How many homes a player may have.
     * Highest "nexus.home.limit.<n>" they get from their ranks. "nexus.home.limit.unlimited" = no limit.
     * Nothing set = default_limit in teleport.toml. OPs have no limit.
     */
    public static int limit(ServerPlayer p) {
        if (Features.on("ranks", "ops_bypass") && Perm.isOp(p)) return UNLIMITED;
        if (!Features.on("ranks")) return TeleportConfig.DEFAULT_HOMES.get();
        int best = -1;
        for (String n : Ranks.allowedNodes(p.getUUID())) {
            if (!n.startsWith(LIMIT_NODE)) continue;
            String v = n.substring(LIMIT_NODE.length());
            if (v.equals("unlimited") || v.equals("*")) return UNLIMITED;
            try {
                best = Math.max(best, Integer.parseInt(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return best >= 0 ? best : TeleportConfig.DEFAULT_HOMES.get();
    }
}
