package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.moderation.StaffLog;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.rules.Rules;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * /rules [page]
 * /rules edit add <rule>
 * /rules edit set <number> <rule>
 * /rules edit remove <number>
 * /rules edit move <number> <to>
 * /rules edit reload
 */
public final class RulesCommand {

    private RulesCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("rules")) return;
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("rules")
                .requires(s -> (Features.on("rules", "view") && Perm.has(s, Perm.RULES_VIEW))
                        || (Features.on("rules", "edit") && Perm.has(s, Perm.RULES_EDIT)));

        if (Features.on("rules", "view")) {
            root.executes(c -> show(c, 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(c -> show(c, IntegerArgumentType.getInteger(c, "page"))));
        }

        if (Features.on("rules", "edit")) {
            root.then(Commands.literal("edit")
                    .requires(s -> Perm.has(s, Perm.RULES_EDIT))
                    .then(Commands.literal("add")
                            .then(Commands.argument("rule", StringArgumentType.greedyString())
                                    .executes(c -> {
                                        String rule = StringArgumentType.getString(c, "rule");
                                        Rules.list().add(rule);
                                        return saved(c, "rules add", "&aAdded rule " + Rules.list().size() + ".", rule);
                                    })))
                    .then(Commands.literal("set")
                            .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                    .then(Commands.argument("rule", StringArgumentType.greedyString())
                                            .executes(c -> {
                                                int n = IntegerArgumentType.getInteger(c, "number");
                                                if (n > Rules.list().size()) return fail(c, "There is no rule " + n + ".");
                                                String rule = StringArgumentType.getString(c, "rule");
                                                Rules.list().set(n - 1, rule);
                                                return saved(c, "rules set", "&aChanged rule " + n + ".", n + ": " + rule);
                                            }))))
                    .then(Commands.literal("remove")
                            .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                    .executes(c -> {
                                        int n = IntegerArgumentType.getInteger(c, "number");
                                        if (n > Rules.list().size()) return fail(c, "There is no rule " + n + ".");
                                        String old = Rules.list().remove(n - 1);
                                        return saved(c, "rules remove", "&aRemoved rule " + n + ".", n + ": " + old);
                                    })))
                    .then(Commands.literal("move")
                            .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                    .then(Commands.argument("to", IntegerArgumentType.integer(1))
                                            .executes(c -> {
                                                int n = IntegerArgumentType.getInteger(c, "number");
                                                int to = IntegerArgumentType.getInteger(c, "to");
                                                List<String> l = Rules.list();
                                                if (n > l.size() || to > l.size()) return fail(c, "There are only " + l.size() + " rules.");
                                                l.add(to - 1, l.remove(n - 1));
                                                return saved(c, "rules move", "&aMoved rule " + n + " to " + to + ".", n + " -> " + to);
                                            }))))
                    .then(Commands.literal("reload")
                            .executes(c -> {
                                Rules.load();
                                return ok(c, "&aReloaded rules.toml (" + Rules.list().size() + " rules).");
                            })));
        }
        d.register(root);
    }

    private static int show(CommandContext<CommandSourceStack> c, int page) {
        List<String> l = Rules.list();
        if (l.isEmpty()) return fail(c, "There are no rules set.");
        int per = Rules.perPage();
        int pages = (l.size() + per - 1) / per;
        if (page > pages) return fail(c, "There are only " + pages + " pages.");

        MutableComponent out = Component.empty();
        boolean first = true;
        if (!Rules.header().isEmpty()) {
            out.append(Text.color(Fancy.apply(Rules.header(), 0)));
            first = false;
        }
        for (int i = (page - 1) * per; i < Math.min(l.size(), page * per); i++) {
            if (!first) out.append("\n");
            first = false;
            out.append(Text.color(Rules.line().replace("{number}", String.valueOf(i + 1)).replace("{rule}", l.get(i))));
        }
        if (pages > 1) {
            out.append("\n").append(Text.color("&7Page " + page + "/" + pages + " "));
            if (page < pages) {
                out.append(Text.color("&e[Next]").withStyle(s -> s.withClickEvent(
                        new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rules " + (page + 1)))));
            }
        }
        if (!Rules.footer().isEmpty()) out.append("\n").append(Text.color(Fancy.apply(Rules.footer(), 0)));
        c.getSource().sendSystemMessage(out);
        return 1;
    }

    private static int saved(CommandContext<CommandSourceStack> c, String action, String msg, String detail) {
        if (!Rules.save()) {
            Rules.load(); // undo the change in memory
            return fail(c, "Could not save rules.toml. Check the console.");
        }
        StaffLog.add(c.getSource(), action, null, null, detail, null);
        return ok(c, msg);
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Text.color(msg), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }
}
