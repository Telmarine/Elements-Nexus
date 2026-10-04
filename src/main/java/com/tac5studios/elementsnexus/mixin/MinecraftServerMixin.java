package com.tac5studios.elementsnexus.mixin;

import com.mojang.authlib.GameProfile;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** The server list (multiplayer screen) doesn't count or show vanished players. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "buildPlayerStatus", at = @At("RETURN"), cancellable = true)
    private void nexus$hideVanishedInPing(CallbackInfoReturnable<ServerStatus.Players> cir) {
        if (!Vanish.hideEverywhere()) return;
        MinecraftServer server = (MinecraftServer) (Object) this;
        int hidden = Vanish.vanishedOnline(server);
        if (hidden == 0) return;
        ServerStatus.Players p = cir.getReturnValue();
        List<GameProfile> sample = p.sample().stream().filter(g -> !Vanish.isVanished(g.getId())).toList();
        cir.setReturnValue(new ServerStatus.Players(p.max(), Math.max(0, p.online() - hidden), sample));
    }
}
