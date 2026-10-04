package com.tac5studios.elementsnexus.mixin;

import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

/** Name suggestions in commands (from any mod) leave out vanished players. */
@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin {

    @Inject(method = "getOnlinePlayerNames", at = @At("RETURN"), cancellable = true)
    private void nexus$hideVanishedNames(CallbackInfoReturnable<Collection<String>> cir) {
        ServerPlayer viewer = ((CommandSourceStack) (Object) this).getPlayer();
        if (viewer == null || !Vanish.hideEverywhere()) return;
        List<String> names = Vanish.namesHiddenFrom(viewer);
        if (names.isEmpty()) return;
        cir.setReturnValue(cir.getReturnValue().stream().filter(n -> !names.contains(n)).toList());
    }
}
