package com.tac5studios.elementsnexus.mixin;

import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A vanished player's death message only goes to staff. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void nexus$quietDeathStart(DamageSource source, CallbackInfo ci) {
        Vanish.beginQuiet((ServerPlayer) (Object) this);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void nexus$quietDeathEnd(DamageSource source, CallbackInfo ci) {
        Vanish.endQuiet();
    }
}
