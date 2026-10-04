package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.sidepanel.SidePanel;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** /sidepanel - show or hide your side panel. */
public final class SidePanelCommand {

    private SidePanelCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("sidepanel", "player_toggle")) return;
        d.register(Commands.literal("sidepanel")
                .requires(s -> s.getPlayer() != null && Perm.has(s, Perm.SIDEPANEL))
                .executes(c -> {
                    boolean on = SidePanel.toggle(c.getSource().getPlayer());
                    c.getSource().sendSuccess(() -> Text.color(on ? "&7Side panel &fon&7." : "&7Side panel &foff&7."), false);
                    return 1;
                }));
    }
}
