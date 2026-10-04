package com.tac5studios.elementsnexus.mixin;

import com.tac5studios.elementsnexus.vanish.NexusTracked;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** Minecraft's per-entity tracker: never show a vanished player to someone who shouldn't see them. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements NexusTracked {

    @Shadow @Final Entity entity;
    @Shadow @Final private Set<ServerPlayerConnection> seenBy;

    @Shadow public abstract void removePlayer(ServerPlayer player);

    @Shadow public abstract void updatePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void nexus$hideVanished(ServerPlayer viewer, CallbackInfo ci) {
        if (entity instanceof ServerPlayer target && Vanish.hiddenFrom(target, viewer)) {
            if (seenBy.contains(viewer.connection)) removePlayer(viewer);
            ci.cancel();
        }
    }

    @Override
    public void nexus$update(ServerPlayer viewer) {
        updatePlayer(viewer);
    }
}
