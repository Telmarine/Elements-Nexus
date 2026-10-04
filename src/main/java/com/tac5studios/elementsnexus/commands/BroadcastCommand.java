package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tac5studios.elementsnexus.broadcast.Broadcast;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.moderation.StaffLog;
import com.tac5studios.elementsnexus.perms.Perm;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * /broadcast <message>                    - chat
 * /broadcast screen <title> | <subtitle>  - big text on screen
 */
public final class BroadcastCommand {

    private BroadcastCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("broadcast")) return;
        boolean chat = Features.on("broadcast", "chat");
        boolean screen = Features.on("broadcast", "screen");
        if (!chat && !screen) return;

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("broadcast")
                .requires(s -> Perm.has(s, Perm.BROADCAST));
        if (screen) {
            root.then(Commands.literal("screen")
                    .then(Commands.argument("text", StringArgumentType.greedyString())
                            .executes(c -> {
                                String text = StringArgumentType.getString(c, "text");
                                Broadcast.screen(c.getSource().getServer(), text);
                                StaffLog.add(c.getSource(), "broadcast screen", null, null, text, null);
                                return 1;
                            })));
        }
        if (chat) {
            root.then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(c -> {
                        String text = StringArgumentType.getString(c, "message");
                        Broadcast.chat(c.getSource().getServer(), text);
                        StaffLog.add(c.getSource(), "broadcast", null, null, text, null);
                        return 1;
                    }));
        }
        d.register(root);
    }
}
