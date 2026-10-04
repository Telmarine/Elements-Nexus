package com.tac5studios.elementsnexus.mixin;

import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;
import java.net.SocketAddress;

/** Expired temp bans are cleared before the login check (vanilla crashes the login on them).
 *  Vanished staff log in quietly: no "joined the game" line or Tab list entry for players. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "canPlayerLogin", at = @At("HEAD"))
    private void nexus$clearExpiredBans(SocketAddress address, GameProfile profile,
                                        CallbackInfoReturnable<Component> cir) {
        PlayerList list = (PlayerList) (Object) this;
        list.getBans().get(profile);   // get() drops expired entries
        list.getIpBans().get(address);
    }

    /** Death and advancement messages of vanished players go to staff only. */
    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void nexus$quietBroadcast(Component message, java.util.function.Function<ServerPlayer, Component> perPlayer,
                                      boolean overlay, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ServerPlayer quiet = Vanish.quietSource();
        if (quiet == null || overlay) return;
        Vanish.tellStaffOnly(((PlayerList) (Object) this).getServer(), quiet, message);
        ci.cancel();
    }

    @Redirect(method = "placeNewPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void nexus$quietJoin(PlayerList list, Component message, boolean overlay,
                                 Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        Component line = com.tac5studios.elementsnexus.messages.Messages.join(player, message);
        if (line == null) return;
        if (Vanish.savedVanished(player.getUUID())) Vanish.tellStaffOnly(list.getServer(), player, line);
        else list.broadcastSystemMessage(line, overlay);
    }

    @Redirect(method = "placeNewPlayer", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    private void nexus$quietTabEntry(PlayerList list, Packet<?> packet,
                                     Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        if (!Vanish.savedVanished(player.getUUID())) {
            list.broadcastAll(packet);
            return;
        }
        for (ServerPlayer p : list.getPlayers()) {
            if (p == player || Perm.has(p, Perm.VANISH_SEE)) p.connection.send(packet);
        }
    }
}
