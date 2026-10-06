package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.toggles.Phantoms;
import com.tac5studios.elementsnexus.toggles.Pvp;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * /pvp [on|off]            - turn your own PvP on or off (no word = switch it)
 * /pvp server [on|off]     - staff: PvP for the whole server
 * /phantoms [on|off]       - turn phantom spawning on or off for yourself
 */
public final class ToggleCommands {

    private ToggleCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (Features.on("pvp")) {
            LiteralArgumentBuilder<CommandSourceStack> pvp = Commands.literal("pvp")
                    .requires(s -> (s.getPlayer() != null && Features.on("pvp", "player_toggle") && Perm.has(s, Perm.PVP_TOGGLE))
                            || (Features.on("pvp", "server_toggle") && Perm.has(s, Perm.PVP_SERVER)));
            if (Features.on("pvp", "player_toggle")) {
                pvp.executes(c -> pvpSelf(c, null))
                        .then(Commands.literal("on").executes(c -> pvpSelf(c, true)))
                        .then(Commands.literal("off").executes(c -> pvpSelf(c, false)));
            }
            if (Features.on("pvp", "server_toggle")) {
                pvp.then(Commands.literal("server").requires(s -> Perm.has(s, Perm.PVP_SERVER))
                        .executes(c -> pvpServer(c, null))
                        .then(Commands.literal("on").executes(c -> pvpServer(c, true)))
                        .then(Commands.literal("off").executes(c -> pvpServer(c, false))));
            }
            d.register(pvp);
        }
        if (Features.on("phantoms", "player_toggle")) {
            d.register(Commands.literal("phantoms")
                    .requires(s -> s.getPlayer() != null && Perm.has(s, Perm.PHANTOMS_TOGGLE))
                    .executes(c -> phantoms(c, null))
                    .then(Commands.literal("on").executes(c -> phantoms(c, true)))
                    .then(Commands.literal("off").executes(c -> phantoms(c, false))));
        }
    }

    private static int pvpSelf(CommandContext<CommandSourceStack> c, Boolean want) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) {
            c.getSource().sendFailure(Text.color("&cOnly players have their own PvP. Use /pvp server on|off."));
            return 0;
        }
        boolean now = Pvp.wants(p.getUUID());
        boolean next = want == null ? !now : want;
        if (next == now) {
            c.getSource().sendSuccess(() -> Text.color("&7Your PvP is already &f" + (now ? "on" : "off") + "&7."), false);
            return 1;
        }
        String no = Pvp.set(p, next, Perm.has(p, Perm.PVP_SERVER));
        if (no != null) {
            c.getSource().sendFailure(Text.color("&c" + no));
            return 0;
        }
        c.getSource().sendSuccess(() -> Text.color(next
                ? "&7PvP &con&7. Other players with PvP on can hurt you."
                : "&7PvP &aoff&7. Other players can't hurt you, and you can't hurt them."), false);
        if (next && !Pvp.serverOn()) {
            c.getSource().sendSuccess(() -> Text.color("&8PvP is turned off for the whole server right now."), false);
        }
        return 1;
    }

    private static int pvpServer(CommandContext<CommandSourceStack> c, Boolean want) {
        boolean now = Pvp.serverOn();
        if (want == null) {
            c.getSource().sendSuccess(() -> Text.color("&7Server PvP is &f" + (now ? "on" : "off") + "&7."), false);
            return 1;
        }
        Pvp.setServer(want);
        c.getSource().getServer().getPlayerList().broadcastSystemMessage(Text.color(want
                ? "&c⚔ PvP is now on for the server. Players with PvP on can fight."
                : "&a⚔ PvP is now off for the whole server."), false);
        return 1;
    }

    private static int phantoms(CommandContext<CommandSourceStack> c, Boolean want) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        boolean now = Phantoms.wants(p.getUUID());
        boolean next = want == null ? !now : want;
        Phantoms.set(p.getUUID(), next);
        c.getSource().sendSuccess(() -> Text.color(next
                ? "&7Phantoms &con&7. They can spawn when you skip sleep."
                : "&7Phantoms &aoff&7. They won't spawn for you."), false);
        return 1;
    }
}
