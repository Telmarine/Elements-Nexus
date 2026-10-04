package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.util.Brig;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Replaces vanilla /list so vanished staff are left out for players. */
public final class ListCommand {

    private ListCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("vanish") || !Vanish.hideEverywhere()) return;
        Brig.remove(d, "list");
        d.register(Commands.literal("list")
                .executes(c -> show(c.getSource(), false))
                .then(Commands.literal("uuids")
                        .requires(s -> s.hasPermission(3))
                        .executes(c -> show(c.getSource(), true))));
    }

    private static int show(CommandSourceStack src, boolean uuids) {
        ServerPlayer viewer = src.getPlayer();
        List<ServerPlayer> shown = new ArrayList<>();
        for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
            if (viewer == null || !Vanish.hiddenFrom(p, viewer)) shown.add(p);
        }
        MutableComponent names = Component.empty();
        for (int i = 0; i < shown.size(); i++) {
            ServerPlayer p = shown.get(i);
            if (i > 0) names.append(Component.literal(", "));
            if (Vanish.isVanished(p)) names.append(Component.literal("[V] ").withStyle(net.minecraft.ChatFormatting.GRAY));
            Component name = p.getTabListDisplayName() != null ? p.getTabListDisplayName() : p.getDisplayName();
            names.append(name);
            if (uuids) names.append(Component.literal(" (" + p.getUUID() + ")"));
        }
        int max = src.getServer().getPlayerList().getMaxPlayers();
        Component out = Component.translatable("commands.list.players", shown.size(), max, names);
        src.sendSuccess(() -> out, false);
        return shown.size();
    }
}
