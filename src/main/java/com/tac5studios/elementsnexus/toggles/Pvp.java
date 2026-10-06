package com.tac5studios.elementsnexus.toggles;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.TogglesConfig;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Player PvP choice (/pvp) and the server-wide switch (/pvp server on|off).
 * Only damage between players is blocked (melee, arrows and other projectiles, and tamed pets),
 * so right-click interactions between players keep working.
 */
public final class Pvp {

    private static final String COLLECTION = "pvp";        // uuid -> true/false (only when the player chose)
    private static final String SERVER = "pvp_server";     // "enabled" -> true/false
    private static final Map<UUID, Long> LAST_CHANGE = new HashMap<>();
    private static final Map<UUID, Long> LAST_FIGHT = new HashMap<>();
    private static final Map<UUID, Long> LAST_WARN = new HashMap<>();

    private Pvp() {}

    // ---------- state ----------

    public static boolean serverOn() {
        Boolean v = Storage.get().get(SERVER, "enabled", Boolean.class);
        return v == null || v;
    }

    public static void setServer(boolean on) {
        if (on) Storage.get().remove(SERVER, "enabled");
        else Storage.get().put(SERVER, "enabled", false);
    }

    public static boolean wants(UUID id) {
        Boolean choice = Storage.get().get(COLLECTION, id.toString(), Boolean.class);
        return choice != null ? choice : TogglesConfig.PVP_DEFAULT_ON.get();
    }

    /** Change a player's choice. Returns a reason it can't change right now, or null when it changed. */
    public static String set(ServerPlayer p, boolean on, boolean bypassWaits) {
        UUID id = p.getUUID();
        long now = System.currentTimeMillis();
        if (!bypassWaits) {
            int combat = TogglesConfig.PVP_COMBAT.get();
            Long fight = LAST_FIGHT.get(id);
            if (combat > 0 && fight != null && now - fight < combat * 1000L) {
                return "You were just in a fight. Try again in " + ((combat * 1000L - (now - fight)) / 1000 + 1) + "s.";
            }
            int cd = TogglesConfig.PVP_COOLDOWN.get();
            Long last = LAST_CHANGE.get(id);
            if (cd > 0 && last != null && now - last < cd * 1000L) {
                return "You changed PvP recently. Try again in " + ((cd * 1000L - (now - last)) / 1000 + 1) + "s.";
            }
        }
        if (on == TogglesConfig.PVP_DEFAULT_ON.get()) Storage.get().remove(COLLECTION, id.toString());
        else Storage.get().put(COLLECTION, id.toString(), on);
        LAST_CHANGE.put(id, now);
        return null;
    }

    public static void forget(UUID id) {
        LAST_CHANGE.remove(id);
        LAST_FIGHT.remove(id);
        LAST_WARN.remove(id);
    }

    // ---------- damage ----------

    /** The player behind a hit: the player, the shooter of a projectile, or the owner of a tamed pet. */
    private static ServerPlayer playerBehind(Entity e) {
        if (e instanceof ServerPlayer p) return p;
        if (e instanceof OwnableEntity pet && Features.on("pvp", "protect_pets")
                && pet.getOwner() instanceof ServerPlayer owner) return owner;
        return null;
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!Features.on("pvp")) return;
        Entity target = event.getEntity();
        ServerPlayer victim = target instanceof ServerPlayer p ? p
                : (target instanceof OwnableEntity && Features.on("pvp", "protect_pets") ? playerBehind(target) : null);
        if (victim == null) return;
        ServerPlayer attacker = playerBehind(event.getSource().getEntity());
        if (attacker == null || attacker == victim) return;

        String why = null;
        if (!serverOn()) why = "PvP is turned off on this server.";
        else if (!wants(attacker.getUUID())) why = "Your PvP is off. Turn it on with /pvp.";
        else if (!wants(victim.getUUID())) why = victim.getGameProfile().getName() + " has PvP off.";

        if (why != null) {
            event.setCanceled(true);
            long now = System.currentTimeMillis();
            Long warned = LAST_WARN.get(attacker.getUUID());
            if (warned == null || now - warned > 3000) {
                LAST_WARN.put(attacker.getUUID(), now);
                attacker.displayClientMessage(Text.color("&c" + why), true);
            }
            return;
        }
        long now = System.currentTimeMillis();
        LAST_FIGHT.put(attacker.getUUID(), now);
        LAST_FIGHT.put(victim.getUUID(), now);
    }
}
