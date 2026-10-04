package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.tac5studios.elementsnexus.afk.Afk;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.perms.Perm;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** /afk - mark yourself AFK (or back). */
public final class AfkCommand {

    private AfkCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("afk", "afk_command")) return;
        d.register(Commands.literal("afk")
                .requires(s -> s.getPlayer() != null && Perm.has(s, Perm.AFK_USE))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    Afk.setAfk(p, !Afk.isAfk(p), true);
                    return 1;
                }));
    }
}
