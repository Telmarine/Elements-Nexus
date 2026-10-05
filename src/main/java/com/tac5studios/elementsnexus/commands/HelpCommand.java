package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.help.Help;
import com.tac5studios.elementsnexus.util.Brig;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * /help [page]       - commands you can use
 * /help <command>    - how to use one command
 * Replaces vanilla /help.
 */
public final class HelpCommand {

    /** Our own commands (always listed, even when show_other_commands is off). */
    private static final Set<String> OURS = Set.of("afk", "back", "ban", "banip", "broadcast", "restartwarn", "nexus", "sidepanel", "link", "holo", "delhome", "freeze", "help", "history",
            "home", "homes", "ignore", "inv", "ender", "jail", "kick", "kit", "msg", "mute", "nick", "r", "rank",
            "realname", "rules", "sc", "sethome", "spawn", "tpaccept", "tpdeny", "tpi", "tpo", "tppos", "unban",
            "unbanip", "unjail", "unmute", "vanish", "warn", "warp", "warps", "tell", "w");

    private HelpCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("help")) return;
        Brig.remove(d, "help");
        d.register(Commands.literal("help")
                .executes(c -> list(c, d, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(c -> list(c, d, IntegerArgumentType.getInteger(c, "page"))))
                .then(Commands.argument("command", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(usable(d, c.getSource()), b))
                        .executes(c -> one(c, d, StringArgumentType.getString(c, "command")))));
    }

    /** Names of root commands this source can run, sorted. */
    private static List<String> usable(CommandDispatcher<CommandSourceStack> d, CommandSourceStack s) {
        List<String> out = new ArrayList<>();
        for (CommandNode<CommandSourceStack> n : d.getRoot().getChildren()) {
            if (!(n instanceof LiteralCommandNode)) continue;
            String name = n.getName();
            if (Help.hidden(name) || !n.canUse(s)) continue;
            if (!Help.showOther() && !OURS.contains(name)) continue;
            out.add(name);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static int list(CommandContext<CommandSourceStack> c, CommandDispatcher<CommandSourceStack> d, int page) {
        List<String> cmds = usable(d, c.getSource());
        int per = Help.perPage();
        int pages = Math.max(1, (cmds.size() + per - 1) / per);
        if (page > pages) return fail(c, "There are only " + pages + " pages.");

        MutableComponent out = Component.empty();
        out.append(Text.color(Fancy.apply(Help.header()
                .replace("{page}", String.valueOf(page)).replace("{pages}", String.valueOf(pages)), 0)));
        for (int i = (page - 1) * per; i < Math.min(cmds.size(), page * per); i++) {
            String name = cmds.get(i);
            String desc = Help.description(name);
            String line = Help.line().replace("{command}", name);
            if (desc == null && Help.noDescription().isEmpty()) {
                // No description to show: drop the separator too, so the line is just "/command".
                int at = line.indexOf("{description}");
                if (at >= 0) line = line.substring(0, at).replaceAll("\\s*((&[0-9a-fk-orA-FK-OR])|\\s)*[-:|]?((&[0-9a-fk-orA-FK-OR])|\\s)*$", "");
            } else {
                line = line.replace("{description}", desc == null ? Help.noDescription() : desc);
            }
            out.append("\n").append(Text.color(line).withStyle(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + name + " "))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.color("&7Click to use /" + name)))));
        }
        if (pages > 1) {
            out.append("\n");
            if (page > 1) out.append(Text.color("&e[Back] ").withStyle(s -> s.withClickEvent(
                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/help " + (page - 1)))));
            if (page < pages) out.append(Text.color("&e[Next]").withStyle(s -> s.withClickEvent(
                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/help " + (page + 1)))));
        }
        c.getSource().sendSystemMessage(out);
        return 1;
    }

    private static int one(CommandContext<CommandSourceStack> c, CommandDispatcher<CommandSourceStack> d, String name) {
        CommandNode<CommandSourceStack> node = d.getRoot().getChild(name);
        if (node == null || !node.canUse(c.getSource()) || Help.hidden(name)) return fail(c, "Unknown command: " + name);
        MutableComponent out = Component.empty().append(Text.color("&6/" + name));
        String desc = Help.description(name);
        if (desc != null) out.append(Text.color(" &7- &f" + desc));
        Map<CommandNode<CommandSourceStack>, String> usage = d.getSmartUsage(node, c.getSource());
        if (node.getCommand() != null) out.append("\n").append(Text.color("&e/" + name));
        for (String u : usage.values()) out.append("\n").append(Text.color("&e/" + name + " " + u));
        c.getSource().sendSystemMessage(out);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }
}
