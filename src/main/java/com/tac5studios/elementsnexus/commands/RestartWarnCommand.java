package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementsnexus.announce.Announcements;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.moderation.StaffLog;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import com.tac5studios.elementsnexus.util.Time;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * /restartwarn <time>   - start the restart countdown (e.g. 30m). Does not stop the server.
 * /restartwarn cancel   - stop it
 * /restartwarn          - time left
 */
public final class RestartWarnCommand {

    private RestartWarnCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("announcements", "restart_warnings")) return;
        d.register(Commands.literal("restartwarn")
                .requires(s -> Perm.has(s, Perm.RESTART_WARN))
                .executes(c -> Announcements.restartRunning()
                        ? ok(c, "&eRestart in &f" + Announcements.words(Announcements.secondsLeft()) + "&e.")
                        : fail(c, "No restart countdown is running."))
                .then(Commands.literal("cancel")
                        .executes(c -> {
                            if (!Announcements.restartRunning()) return fail(c, "No restart countdown is running.");
                            Announcements.cancelRestart(c.getSource().getServer());
                            StaffLog.add(c.getSource(), "restart cancel", null, null, null, null);
                            return ok(c, "&aRestart countdown cancelled.");
                        }))
                .then(Commands.argument("time", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("30m", "15m", "10m", "5m", "1m"), b))
                        .executes(c -> {
                            Long secs = Time.parse(StringArgumentType.getString(c, "time"));
                            if (secs == null) return fail(c, "Use a time like 30m, 5m or 90s.");
                            Announcements.startRestart(c.getSource().getServer(), secs);
                            StaffLog.add(c.getSource(), "restart warn", null, null, null, Time.text(secs));
                            com.tac5studios.elementsnexus.discord.Discord.restartWarn(Announcements.words(secs));
                            return ok(c, "&aRestart countdown started: &f" + Announcements.words(secs) + "&a.");
                        })));
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
