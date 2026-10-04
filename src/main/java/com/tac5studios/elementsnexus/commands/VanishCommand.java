package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.moderation.StaffLog;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** /vanish - staff only. Toggle being hidden from players. */
public final class VanishCommand {

    private VanishCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("vanish")) return;
        d.register(Commands.literal("vanish")
                .requires(s -> s.getPlayer() != null && Perm.has(s, Perm.VANISH))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    boolean on = !Vanish.isVanished(p);
                    Vanish.set(p, on);
                    StaffLog.add(c.getSource(), on ? "vanish" : "unvanish", p.getUUID(), p.getGameProfile().getName(), null, null);
                    c.getSource().sendSuccess(() -> Text.color(on ? "&7You are now &fvanished&7." : "&7You are &fvisible &7again."), false);
                    return 1;
                }));
    }
}
