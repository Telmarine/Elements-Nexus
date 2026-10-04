package com.tac5studios.elementsnexus.mixin;

import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A vanished player's advancement message only goes to staff. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"))
    private void nexus$quietAwardStart(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        Vanish.beginQuiet(player);
    }

    @Inject(method = "award", at = @At("RETURN"))
    private void nexus$quietAwardEnd(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        Vanish.endQuiet();
    }
}
