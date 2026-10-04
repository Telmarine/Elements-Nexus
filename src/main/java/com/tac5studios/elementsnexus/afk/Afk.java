package com.tac5studios.elementsnexus.afk;

import com.tac5studios.elementsnexus.config.AfkConfig;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who is AFK.
 * Counts as active: walking (not just being pushed by water or riding), looking around,
 * chatting and using commands.
 */
public final class Afk {

    private record Seen(Vec3 pos, float yaw, float pitch) {}

    private static final Map<UUID, Seen> LAST_SEEN = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> AFK_SINCE = new HashMap<>();
    private static final Set<UUID> MANUAL = new HashSet<>();
    private static long tick;

    private Afk() {}

    public static boolean isAfk(ServerPlayer p) {
        return AFK_SINCE.containsKey(p.getUUID());
    }

    /** Called every server tick. Checks each player once a second. */
    public static void tick(MinecraftServer server) {
        tick++;
        if (!Features.on("afk") || tick % 20 != 0) return;
        long idleTicks = AfkConfig.IDLE_MINUTES.get() * 60L * 20L;
        long kickTicks = AfkConfig.KICK_MINUTES.get() * 60L * 20L;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            Seen now = new Seen(p.position(), p.getYRot(), p.getXRot());
            Seen before = LAST_SEEN.put(id, now);
            if (before != null && moved(p, before, now)) active(p);
            LAST_ACTIVE.putIfAbsent(id, tick);

            if (!isAfk(p) && Features.on("afk", "auto_detect") && tick - LAST_ACTIVE.get(id) >= idleTicks) {
                setAfk(p, true, false);
            }
            if (isAfk(p) && Features.on("afk", "afk_kick") && !Perm.has(p, Perm.AFK_EXEMPT)
                    && tick - AFK_SINCE.get(id) >= kickTicks) {
                p.connection.disconnect(Text.color(AfkConfig.KICK.get()));
            }
        }
    }

    private static boolean moved(ServerPlayer p, Seen a, Seen b) {
        boolean looked = Math.abs(a.yaw() - b.yaw()) > 1.0f || Math.abs(a.pitch() - b.pitch()) > 1.0f;
        if (looked) return true;
        if (p.isPassenger() || p.isInWater() || p.isInLava()) return false; // being carried doesn't count
        double dx = a.pos().x - b.pos().x, dz = a.pos().z - b.pos().z;
        return dx * dx + dz * dz > 0.01;
    }

    /** The player did something. Ends AFK. */
    public static void active(ServerPlayer p) {
        LAST_ACTIVE.put(p.getUUID(), tick);
        if (isAfk(p)) setAfk(p, false, false);
    }

    /** Turn AFK on or off. manual = the player used /afk. */
    public static void setAfk(ServerPlayer p, boolean afk, boolean manual) {
        UUID id = p.getUUID();
        if (afk) {
            if (AFK_SINCE.containsKey(id)) return;
            AFK_SINCE.put(id, tick);
            if (manual) MANUAL.add(id);
            LAST_SEEN.put(id, new Seen(p.position(), p.getYRot(), p.getXRot())); // start fresh from here
            announce(p, AfkConfig.NOW_AFK.get());
        } else {
            if (AFK_SINCE.remove(id) == null) return;
            MANUAL.remove(id);
            LAST_ACTIVE.put(id, tick);
            announce(p, AfkConfig.BACK.get());
        }
        p.refreshTabListName();
    }

    private static void announce(ServerPlayer p, String msg) {
        if (!Features.on("afk", "announce") || msg.isEmpty()) return;
        if (com.tac5studios.elementsnexus.vanish.Vanish.isVanished(p)) return; // don't give away vanished staff
        Component line = Text.color(msg.replace("{player}", com.tac5studios.elementsnexus.nick.Nick.display(p)));
        p.server.getPlayerList().broadcastSystemMessage(line, false);
    }

    public static void forget(UUID id) {
        LAST_SEEN.remove(id);
        LAST_ACTIVE.remove(id);
        AFK_SINCE.remove(id);
        MANUAL.remove(id);
    }
}
