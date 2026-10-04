package com.tac5studios.elementsnexus.moderation;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.teleport.Teleports.Spot;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Mutes, jail and freeze for one player ("punish" collection, key = UUID). */
public class Punish {

    /** until = time it ends (ms). 0 = never ends. */
    public static class Timed {
        public long until;
        public String reason = "";
        public String by = "";

        public boolean active() {
            return until == 0 || System.currentTimeMillis() < until;
        }

        public long secondsLeft() {
            return until == 0 ? -1 : Math.max(0, (until - System.currentTimeMillis()) / 1000);
        }
    }

    public static class Jail extends Timed {
        /** Where the player was before jail. They go back here when released. */
        public Spot back;
    }

    public Timed mute;
    public Jail jail;
    public boolean frozen;

    private static final String COLLECTION = "punish";

    public static Punish of(UUID id) {
        Punish p = Storage.get().get(COLLECTION, id.toString(), Punish.class);
        return p != null ? p : new Punish();
    }

    public void save(UUID id) {
        if (mute == null && jail == null && !frozen) Storage.get().remove(COLLECTION, id.toString());
        else Storage.get().put(COLLECTION, id.toString(), this);
    }

    /** True while a player is muted. Clears a mute that has run out. */
    public static boolean isMuted(ServerPlayer p) {
        if (!Features.on("moderation", "mute")) return false;
        Punish d = of(p.getUUID());
        if (d.mute == null) return false;
        if (d.mute.active()) return true;
        d.mute = null;
        d.save(p.getUUID());
        return false;
    }

    public static boolean isJailed(ServerPlayer p) {
        if (!Features.on("moderation", "jail")) return false;
        Punish d = of(p.getUUID());
        return d.jail != null;
    }
}
