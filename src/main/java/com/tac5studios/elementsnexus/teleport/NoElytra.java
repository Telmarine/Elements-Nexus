package com.tac5studios.elementsnexus.teleport;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.TeleportConfig;
import com.tac5studios.elementsnexus.perms.Perm;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Stops elytra gliding in the worlds listed in teleport.toml [elytra] no_elytra_worlds.
 * The player just drops out of the glide; the elytra is never taken off.
 * Switch: features.toml [elytra] no_elytra_worlds.
 */
public final class NoElytra {

    private NoElytra() {}

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.isFallFlying()) return;
        if (!Features.on("elytra", "no_elytra_worlds")) return;
        if (!blocked(p.level().dimension().location().toString())) return;
        if (Perm.has(p, Perm.TELEPORT_BYPASS)) return;
        p.stopFallFlying();
        p.displayClientMessage(Component.literal("Elytras can't be used in this world.").withStyle(ChatFormatting.RED), true);
    }

    private static boolean blocked(String dim) {
        for (String w : TeleportConfig.NO_ELYTRA_WORLDS.get()) {
            if (w.trim().equalsIgnoreCase(dim)) return true;
        }
        return false;
    }
}
