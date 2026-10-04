package com.tac5studios.elementsnexus.teleport;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.TeleportConfig;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Every player teleport goes through here (homes, warps, spawn, tpa, back).
 * Handles the warm-up (stand still) and the combat lock.
 */
public final class Teleports {

    /** A place to go. */
    public record Spot(String dim, double x, double y, double z, float yaw, float pitch) {
        public static Spot of(ServerPlayer p) {
            return new Spot(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        }
    }

    private record Pending(Spot to, String what, Vec3 start, long due) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_FIGHT = new HashMap<>();
    private static long tick;

    /** Set by moderation: returns a reason the player can't teleport (jailed, frozen), or null. */
    public static java.util.function.Function<ServerPlayer, String> blocked = p -> null;

    private Teleports() {}

    /** Teleport a player, with warm-up and combat lock. 'what' is shown to them, e.g. "home". */
    public static void go(ServerPlayer p, Spot to, String what) {
        String no = blocked.apply(p);
        if (no != null) {
            p.sendSystemMessage(Component.literal(no).withStyle(ChatFormatting.RED));
            return;
        }
        boolean safety = Features.on("teleport_safety") && !Perm.has(p, Perm.TELEPORT_BYPASS);

        if (safety && Features.on("teleport_safety", "combat_lock")) {
            int lock = TeleportConfig.COMBAT_LOCK.get();
            Long last = LAST_FIGHT.get(p.getUUID());
            if (lock > 0 && last != null) {
                long left = (last + lock * 20L - tick + 19) / 20;
                if (left > 0) {
                    p.sendSystemMessage(Component.literal("You were just in a fight. Try again in " + left + "s.").withStyle(ChatFormatting.RED));
                    return;
                }
            }
        }

        int warm = safety && Features.on("teleport_safety", "warmup") ? TeleportConfig.WARMUP.get() : 0;
        if (warm <= 0) {
            now(p, to, what);
            return;
        }
        PENDING.put(p.getUUID(), new Pending(to, what, p.position(), tick + warm * 20L));
        p.sendSystemMessage(Text.color("&7Teleporting to " + what + " in " + warm + "s. Don't move."));
    }

    /** Teleport right away. Returns false if the world doesn't exist. */
    public static boolean now(ServerPlayer p, Spot to, String what) {
        return now(p, to, what, true);
    }

    /** Same, but say = false teleports without a chat message. */
    public static boolean now(ServerPlayer p, Spot to, String what, boolean say) {
        ResourceLocation id = ResourceLocation.tryParse(to.dim());
        ServerLevel level = id == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            p.sendSystemMessage(Component.literal("That place is in a world that no longer exists.").withStyle(ChatFormatting.RED));
            return false;
        }
        rememberBack(p);
        p.teleportTo(level, to.x(), to.y(), to.z(), to.yaw(), to.pitch());
        if (say) p.sendSystemMessage(Text.color("&aTeleported to " + what + "."));
        return true;
    }

    // ---------- /back ----------

    /** Save where a player is now, so /back can return them here. */
    public static void rememberBack(ServerPlayer p) {
        if (!com.tac5studios.elementsnexus.config.Features.on("back")) return;
        com.tac5studios.elementsnexus.storage.Storage.get().put("back", p.getUUID().toString(), Spot.of(p));
    }

    public static Spot backSpot(UUID id) {
        return com.tac5studios.elementsnexus.storage.Storage.get().get("back", id.toString(), Spot.class);
    }

    /** Every server tick: finish or cancel waiting teleports. */
    public static void tick(MinecraftServer server) {
        tick++;
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Pending w = e.getValue();
            if (p == null) {
                it.remove();
                continue;
            }
            if (p.position().distanceToSqr(w.start()) > 0.25) { // moved more than half a block
                it.remove();
                p.sendSystemMessage(Component.literal("Teleport cancelled because you moved.").withStyle(ChatFormatting.RED));
                continue;
            }
            if (tick >= w.due()) {
                it.remove();
                String no = blocked.apply(p); // frozen or jailed during the warm-up
                if (no != null) {
                    p.sendSystemMessage(Component.literal(no).withStyle(ChatFormatting.RED));
                    continue;
                }
                now(p, w.to(), w.what());
            }
        }
    }

    /** Getting hurt cancels a waiting teleport. A player hitting a player starts the combat lock for both. */
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer hurt)) return;
        if (PENDING.remove(hurt.getUUID()) != null) {
            hurt.sendSystemMessage(Component.literal("Teleport cancelled because you were hurt.").withStyle(ChatFormatting.RED));
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer hitter && hitter != hurt) {
            LAST_FIGHT.put(hurt.getUUID(), tick);
            LAST_FIGHT.put(hitter.getUUID(), tick);
            // Attacking a player also cancels your own waiting teleport.
            if (PENDING.remove(hitter.getUUID()) != null) {
                hitter.sendSystemMessage(Component.literal("Teleport cancelled because you are in a fight.").withStyle(ChatFormatting.RED));
            }
        }
    }

    public static void forget(UUID id) {
        PENDING.remove(id);
    }

    /** Short name of a world for messages, e.g. "overworld". */
    public static String worldName(String dim) {
        int i = dim.indexOf(':');
        return i < 0 ? dim : dim.substring(i + 1);
    }
}
