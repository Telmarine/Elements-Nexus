package com.tac5studios.elementsnexus.moderation;

import com.tac5studios.elementsnexus.chat.Chat;
import com.tac5studios.elementsnexus.chat.ChatFilter;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.ModerationConfig;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.teleport.Teleports;
import com.tac5studios.elementsnexus.teleport.Teleports.Spot;
import com.tac5studios.elementsnexus.util.Text;
import com.tac5studios.elementsnexus.util.Time;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runs mutes, freeze and jail while the server is up. */
public final class Moderation {

    private static final Map<UUID, Vec3> FROZEN_AT = new HashMap<>();
    /** Jailed players who are online (kept in memory so the every-tick check is cheap). */
    private static final java.util.Set<UUID> JAILED = new java.util.HashSet<>();
    private static long tick;

    private Moderation() {}

    /** Connect moderation to chat and teleports. Called once at startup. */
    public static void setup() {
        Chat.muted = Punish::isMuted;
        Chat.publicBlocked = p -> JAILED.contains(p.getUUID()) && ModerationConfig.JAIL_BLOCKS_CHAT.get()
                ? ModerationConfig.JAIL_NOTICE.get() : null;
        Teleports.blocked = p -> {
            if (Features.on("moderation", "freeze") && FROZEN_AT.containsKey(p.getUUID())) return "You are frozen.";
            if (Punish.isJailed(p)) return "You can't teleport while in jail.";
            return null;
        };
    }

    // ---------- jail spot ----------

    public static Spot jailSpot() {
        return Storage.get().get("settings", "jail", Spot.class);
    }

    public static void setJailSpot(Spot s) {
        Storage.get().put("settings", "jail", s);
    }

    // ---------- freeze ----------

    public static boolean isFrozen(ServerPlayer p) {
        return FROZEN_AT.containsKey(p.getUUID());
    }

    public static void freeze(ServerPlayer p, boolean on) {
        Punish d = Punish.of(p.getUUID());
        d.frozen = on;
        d.save(p.getUUID());
        if (on) FROZEN_AT.put(p.getUUID(), p.position());
        else FROZEN_AT.remove(p.getUUID());
    }

    // ---------- jail ----------

    /** Put a player in jail. Returns false if no jail spot is set. */
    public static boolean jail(ServerPlayer p, long seconds, String reason, String by) {
        Spot jail = jailSpot();
        if (jail == null) return false;
        Punish d = Punish.of(p.getUUID());
        Punish.Jail j = new Punish.Jail();
        j.until = seconds > 0 ? System.currentTimeMillis() + seconds * 1000 : 0;
        j.reason = reason;
        j.by = by;
        j.back = d.jail != null ? d.jail.back : Spot.of(p); // re-jailing keeps the original spot
        d.jail = j;
        d.save(p.getUUID());
        JAILED.add(p.getUUID());
        Teleports.now(p, jail, "jail", false);
        showJailScreen(p);
        return true;
    }

    /** The jail message in the middle of the player's screen (only they see it). */
    public static void showJailScreen(ServerPlayer p) {
        showJailScreen(p, true);
    }

    /** fadeIn = false is used for the repeats, so the text stays steady instead of flickering. */
    private static void showJailScreen(ServerPlayer p, boolean fadeIn) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(fadeIn ? 10 : 0, 200, 20));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Text.color(ModerationConfig.JAIL_SUBTITLE.get())));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Text.color(ModerationConfig.JAIL_TITLE.get())));
    }

    /** Release a player (online). */
    public static void release(ServerPlayer p) {
        Punish d = Punish.of(p.getUUID());
        if (d.jail == null) return;
        Spot back = d.jail.back;
        d.jail = null;
        JAILED.remove(p.getUUID());
        d.save(p.getUUID());
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true)); // take the jail text off their screen
        if (back != null) Teleports.now(p, back, "where you were");
        Storage.get().remove("back", p.getUUID().toString()); // so /back can't lead into the jail
        p.sendSystemMessage(Text.color("&aYou are out of jail."));
    }

    // ---------- every tick ----------

    public static void tick(MinecraftServer server) {
        tick++;
        if (!Features.on("moderation")) return;

        // Frozen players are held in place every tick.
        if (Features.on("moderation", "freeze")) {
            for (Map.Entry<UUID, Vec3> e : FROZEN_AT.entrySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null) continue;
                Vec3 at = e.getValue();
                if (p.position().distanceToSqr(at) > 0.01) {
                    p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
                }
            }
        }

        if (!Features.on("moderation", "jail") || JAILED.isEmpty()) return;
        Spot jail = jailSpot();

        // Every tick: pull jailed players straight back if they step outside the jail radius.
        if (jail != null) {
            double r = ModerationConfig.JAIL_RADIUS.get();
            Vec3 center = new Vec3(jail.x(), jail.y(), jail.z());
            for (UUID id : JAILED) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                boolean sameWorld = p.level().dimension().location().toString().equals(jail.dim());
                if (!sameWorld) {
                    Teleports.now(p, jail, "jail", false);
                } else if (p.position().distanceToSqr(center) > r * r) {
                    p.connection.teleport(jail.x(), jail.y(), jail.z(), p.getYRot(), p.getXRot());
                }
            }
        }

        // Once a second: release anyone whose time is up. Every 5 seconds: keep the jail text on screen.
        if (tick % 20 != 0) return;
        boolean refreshText = tick % 100 == 0;
        for (UUID id : List.copyOf(JAILED)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            Punish d = Punish.of(id);
            if (d.jail == null) JAILED.remove(id);
            else if (!d.jail.active()) release(p);
            else if (refreshText) showJailScreen(p, false);
        }
    }

    /** When a player joins: put frozen players back in place, jailed players back in jail. */
    public static void onJoin(ServerPlayer p) {
        Punish d = Punish.of(p.getUUID());
        if (d.frozen && Features.on("moderation", "freeze")) {
            FROZEN_AT.put(p.getUUID(), p.position());
            p.sendSystemMessage(Component.literal("You are frozen.").withStyle(ChatFormatting.RED));
        }
        if (d.jail != null && Features.on("moderation", "jail")) {
            if (!d.jail.active()) release(p);
            else {
                JAILED.add(p.getUUID());
                if (jailSpot() != null) Teleports.now(p, jailSpot(), "jail", false);
                showJailScreen(p);
            }
        }
    }

    public static void forget(UUID id) {
        FROZEN_AT.remove(id);
        JAILED.remove(id);
    }

    // ---------- signs and books ----------

    /** Only the star-replacement part of the filter (no messages, nothing blocked). */
    public static List<String> cleanOnly(ServerPlayer p, List<String> lines) {
        if (!Features.on("chat", "filter") || Perm.has(p, ChatFilter.bypassNode())) return lines;
        return lines.stream().map(l -> ChatFilter.check(l).kind() == ChatFilter.Kind.HIDE ? ChatFilter.check(l).text() : l).toList();
    }

    /**
     * Check text a player wrote on a sign or in a book.
     * Returns null to block it, or the (possibly starred) lines to use.
     */
    public static List<String> checkWriting(ServerPlayer p, List<String> lines) {
        if (Features.on("moderation", "mute") && Punish.isMuted(p)) {
            p.sendSystemMessage(Component.literal("You can't write on signs or in books while muted.").withStyle(ChatFormatting.RED));
            return null;
        }
        if (!Features.on("chat", "filter") || Perm.has(p, ChatFilter.bypassNode())) return lines;
        String all = String.join(" ", lines);
        ChatFilter.Result r = ChatFilter.check(all);
        if (r.kind() == ChatFilter.Kind.BLOCK) {
            p.sendSystemMessage(Component.literal("That text was not saved. Please keep it friendly.").withStyle(ChatFormatting.RED));
            return null;
        }
        if (r.kind() == ChatFilter.Kind.OK) return lines;
        return lines.stream().map(l -> ChatFilter.check(l).text()).toList();
    }
}
