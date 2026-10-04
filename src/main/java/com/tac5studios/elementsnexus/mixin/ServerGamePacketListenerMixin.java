package com.tac5studios.elementsnexus.mixin;

import com.tac5studios.elementsnexus.moderation.Moderation;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Signs and books: muted players can't write, blocked words aren't saved,
 * hidden words are turned into stars.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow
    public ServerPlayer player;

    // ---------- signs ----------

    @Inject(method = "updateSignText", at = @At("HEAD"), cancellable = true)
    private void nexus$signCheck(ServerboundSignUpdatePacket packet, List<FilteredText> lines, CallbackInfo ci) {
        if (Moderation.checkWriting(player, raw(lines)) == null) ci.cancel();
    }

    @ModifyVariable(method = "updateSignText", at = @At("HEAD"), argsOnly = true)
    private List<FilteredText> nexus$signClean(List<FilteredText> lines) {
        return clean(lines);
    }

    // ---------- books ----------

    @Inject(method = "updateBookContents", at = @At("HEAD"), cancellable = true)
    private void nexus$bookCheck(List<FilteredText> pages, int slot, CallbackInfo ci) {
        if (Moderation.checkWriting(player, raw(pages)) == null) ci.cancel();
    }

    @ModifyVariable(method = "updateBookContents", at = @At("HEAD"), argsOnly = true)
    private List<FilteredText> nexus$bookClean(List<FilteredText> pages) {
        return clean(pages);
    }

    @Inject(method = "signBook", at = @At("HEAD"), cancellable = true)
    private void nexus$signBookCheck(FilteredText title, List<FilteredText> pages, int slot, CallbackInfo ci) {
        List<String> all = new java.util.ArrayList<>(raw(pages));
        all.add(0, title.raw());
        if (Moderation.checkWriting(player, all) == null) ci.cancel();
    }

    @ModifyVariable(method = "signBook", at = @At("HEAD"), argsOnly = true)
    private List<FilteredText> nexus$signBookClean(List<FilteredText> pages) {
        return clean(pages);
    }

    // ---------- vanish: quiet leave ----------

    @Redirect(method = "removePlayerFromWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void nexus$quietLeave(net.minecraft.server.players.PlayerList list, net.minecraft.network.chat.Component message, boolean overlay) {
        net.minecraft.network.chat.Component line = com.tac5studios.elementsnexus.messages.Messages.leave(player, message);
        if (line == null) return;
        if (com.tac5studios.elementsnexus.vanish.Vanish.isVanished(player)) {
            com.tac5studios.elementsnexus.vanish.Vanish.tellStaffOnly(list.getServer(), player, line);
        } else {
            list.broadcastSystemMessage(line, overlay);
        }
    }

    // ---------- helpers ----------

    private static List<String> raw(List<FilteredText> list) {
        return list.stream().map(FilteredText::raw).toList();
    }

    /** Swap hidden words for stars. Lines are only replaced if something changed. */
    private List<FilteredText> clean(List<FilteredText> list) {
        List<String> before = raw(list);
        List<String> after = Moderation.cleanOnly(player, before);
        if (after.equals(before)) return list;
        return after.stream().map(FilteredText::passThrough).toList();
    }
}
