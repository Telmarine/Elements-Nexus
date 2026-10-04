package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.discord.Discord;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /link         - get a code to link your Discord account
 * /link remove  - unlink it
 */
public final class LinkCommand {

    private LinkCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("discord", "link")) return;
        d.register(Commands.literal("link")
                .requires(s -> s.getPlayer() != null && Perm.has(s, Perm.DISCORD_LINK))
                .executes(c -> {
                    if (!Discord.running()) {
                        c.getSource().sendFailure(Component.literal("Discord is not connected right now."));
                        return 0;
                    }
                    ServerPlayer p = c.getSource().getPlayer();
                    String code = Discord.newCode(p);
                    c.getSource().sendSuccess(() -> Text.color("&7In Discord, send &f!link " + code
                            + " &7to the bot (in a server channel it can see, or in a DM). The code works for 10 minutes."), false);
                    return 1;
                })
                .then(Commands.literal("remove").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (Discord.linkedUser(p.getUUID()) == null) {
                        c.getSource().sendFailure(Component.literal("Your account is not linked."));
                        return 0;
                    }
                    Discord.unlink(p.getUUID());
                    c.getSource().sendSuccess(() -> Text.color("&7Your Discord account is no longer linked."), false);
                    return 1;
                })));
    }
}
