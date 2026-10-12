package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import com.tac5studios.elementsnexus.vote.Vote;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** /vote - the vote sites from vote.toml, with clickable links. */
public final class VoteCommand {

    private VoteCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("vote")) return;
        d.register(Commands.literal("vote")
                .requires(s -> Perm.has(s, Perm.VOTE))
                .executes(c -> {
                    if (Vote.lines().isEmpty()) {
                        c.getSource().sendFailure(Component.literal("There are no vote sites set."));
                        return 0;
                    }
                    MutableComponent out = Component.empty();
                    boolean first = true;
                    if (!Vote.header().isEmpty()) {
                        out.append(Text.color(Fancy.apply(Vote.header(), 0)));
                        first = false;
                    }
                    for (String line : Vote.lines()) {
                        if (!first) out.append("\n");
                        first = false;
                        out.append(Text.color(Fancy.apply(line, 0)));
                    }
                    if (!Vote.footer().isEmpty()) out.append("\n").append(Text.color(Fancy.apply(Vote.footer(), 0)));
                    c.getSource().sendSystemMessage(out);
                    return 1;
                }));
    }
}
