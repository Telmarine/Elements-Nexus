package com.tac5studios.elementsnexus.broadcast;

import com.tac5studios.elementsnexus.config.BroadcastConfig;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Server-wide messages: in chat, or big text on screen. */
public final class Broadcast {

    private Broadcast() {}

    public static void chat(MinecraftServer server, String message) {
        Component line = Text.color(Fancy.apply(BroadcastConfig.CHAT_FORMAT.get().replace("{message}", message), 0));
        server.sendSystemMessage(line);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(line);
            sound(p);
        }
    }

    /** text = "title" or "title | subtitle". */
    public static void screen(MinecraftServer server, String text) {
        String title = text, subtitle = "";
        int bar = text.indexOf('|');
        if (bar >= 0) {
            title = text.substring(0, bar).trim();
            subtitle = text.substring(bar + 1).trim();
        }
        Component t = Text.color(Fancy.apply(BroadcastConfig.TITLE_COLOR.get() + title, 0));
        Component s = Text.color(Fancy.apply(BroadcastConfig.SUBTITLE_COLOR.get() + subtitle, 0));
        var anim = new ClientboundSetTitlesAnimationPacket(BroadcastConfig.FADE_IN.get() * 20,
                BroadcastConfig.STAY.get() * 20, BroadcastConfig.FADE_OUT.get() * 20);
        server.sendSystemMessage(Text.color("&7[Screen broadcast] &f" + title + (subtitle.isEmpty() ? "" : " &7| &f" + subtitle)));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(anim);
            p.connection.send(new ClientboundSetSubtitleTextPacket(s));
            p.connection.send(new ClientboundSetTitleTextPacket(t));
            sound(p);
        }
    }

    public static void sound(ServerPlayer p) {
        String id = BroadcastConfig.SOUND.get();
        if (id == null || id.isBlank()) return;
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) return;
        SoundEvent e = BuiltInRegistries.SOUND_EVENT.get(rl);
        if (e != null) p.playNotifySound(e, SoundSource.MASTER, 1f, 1f);
    }
}
