package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.moderation.StaffLog;
import com.tac5studios.elementsnexus.nick.Nick;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /nick <nickname|off>            - your own nickname
 * /nick <player> <nickname|off>   - staff: someone else's
 * /realname <nickname>            - who is behind a nickname
 */
public final class NickCommand {

    private NickCommand() {}

    private static boolean canSelf(CommandSourceStack s) {
        if (s.getPlayer() == null) return false;
        return (Features.on("nicknames", "self_nick") && Perm.has(s, Perm.NICK_SELF)) || canOthers(s);
    }

    private static boolean canOthers(CommandSourceStack s) {
        return Features.on("nicknames", "nick_others") && Perm.has(s, Perm.NICK_OTHERS);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("nicknames")) return;
        d.register(Commands.literal("nick")
                .requires(s -> canSelf(s) || canOthers(s))
                // One greedy argument so color codes (& and #) can be typed:
                // /nick <nickname>  or  /nick <player> <nickname>
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .suggests((c, b) -> canOthers(c.getSource())
                                ? SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b)
                                : SharedSuggestionProvider.suggest(List.of("off"), b))
                        .executes(c -> {
                            String[] parts = StringArgumentType.getString(c, "nickname").trim().split("\\s+", 2);
                            if (parts.length == 2) {
                                if (!canOthers(c.getSource())) return fail(c, "Nicknames can't have spaces.");
                                ServerPlayer target = c.getSource().getServer().getPlayerList().getPlayerByName(parts[0]);
                                if (target == null) return fail(c, "That player is not online.");
                                return apply(c, target, parts[1]);
                            }
                            if (!canSelf(c.getSource())) return fail(c, "Use /nick <player> <nickname>.");
                            return apply(c, c.getSource().getPlayer(), parts[0]);
                        })));

        if (Features.on("nicknames", "realname")) {
            d.register(Commands.literal("realname")
                    .requires(s -> Perm.has(s, Perm.NICK_REALNAME))
                    .then(Commands.argument("nickname", StringArgumentType.greedyString())
                            .executes(c -> {
                                List<String> names = Nick.realNames(StringArgumentType.getString(c, "nickname"));
                                if (names.isEmpty()) return fail(c, "No one has that nickname.");
                                return ok(c, "&7Real name: &f" + String.join("&7, &f", names));
                            })));
        }
    }

    private static int apply(CommandContext<CommandSourceStack> c, ServerPlayer target, String nick) {
        ServerPlayer me = c.getSource().getPlayer();
        boolean self = me == target;
        String realName = target.getGameProfile().getName();

        if (nick.equalsIgnoreCase("off")) {
            if (Nick.get(target.getUUID()) == null) return fail(c, self ? "You don't have a nickname." : realName + " doesn't have a nickname.");
            Nick.set(target, null);
            if (!self) {
                StaffLog.add(c.getSource(), "nick off", target.getUUID(), realName, null, null);
                target.sendSystemMessage(Text.color("&7Your nickname was removed."));
            }
            return ok(c, self ? "&7Nickname removed." : "&7Removed " + realName + "'s nickname.");
        }

        String problem = Nick.problem(target, nick, Perm.has(c.getSource(), Perm.NICK_COLOR));
        if (problem != null) return fail(c, problem);
        Nick.set(target, nick);
        if (!self) {
            StaffLog.add(c.getSource(), "nick", target.getUUID(), realName, Nick.plain(nick), null);
            target.sendSystemMessage(Text.color("&7Your nickname is now &f" + nick + "&7."));
        }
        return ok(c, (self ? "&7Your nickname is now &f" : "&7" + realName + "'s nickname is now &f") + nick + "&7.");
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
