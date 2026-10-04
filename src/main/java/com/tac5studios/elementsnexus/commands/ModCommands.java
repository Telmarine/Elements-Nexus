package com.tac5studios.elementsnexus.commands;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.moderation.InvView;
import com.tac5studios.elementsnexus.moderation.Moderation;
import com.tac5studios.elementsnexus.moderation.Punish;
import com.tac5studios.elementsnexus.moderation.StaffLog;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.teleport.Teleports.Spot;
import com.tac5studios.elementsnexus.util.Brig;
import com.tac5studios.elementsnexus.util.Text;
import com.tac5studios.elementsnexus.util.Time;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * /warn <player> <reason>
 * /mute <player> [time] [reason]      /unmute <player>
 * /kick <player> [reason]
 * /ban <player> [time] [reason]       /unban <player>          (no time = permanent)
 * /banip <player|ip> [time] [reason]  /unbanip <player|ip>     (blocks every account from that IP)
 * /freeze <player>                    (again to unfreeze)
 * /jail <player> [time] [reason]      /unjail <player>      /jail set
 * /inv <player> [ender]
 * /history <player>
 * Time: 30s, 10m, 2h, 1d, 1w (or 1d12h). No time = until removed.
 */
public final class ModCommands {

    private ModCommands() {}

    /** A time (optional) and a reason, read from "[time] [reason...]". */
    private record TimeReason(long seconds, String reason) {
        static TimeReason of(String rest, boolean allowTime) {
            rest = rest == null ? "" : rest.trim();
            if (allowTime && !rest.isEmpty()) {
                String first = rest.split(" ", 2)[0];
                Long t = Time.parse(first);
                if (t != null) return new TimeReason(t, rest.length() > first.length() ? rest.substring(first.length()).trim() : "");
            }
            return new TimeReason(0, rest);
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("moderation")) return;
        String m = "moderation";

        if (Features.on(m, "warn")) {
            d.register(Commands.literal("warn").requires(s -> Perm.has(s, Perm.MOD_WARN))
                    .then(player().then(Commands.argument("reason", StringArgumentType.greedyString())
                            .executes(ModCommands::warn))));
        }
        if (Features.on(m, "mute")) {
            d.register(Commands.literal("mute").requires(s -> Perm.has(s, Perm.MOD_MUTE))
                    .then(player().executes(c -> mute(c, null))
                            .then(Commands.argument("time_and_reason", StringArgumentType.greedyString())
                                    .executes(c -> mute(c, StringArgumentType.getString(c, "time_and_reason"))))));
            d.register(Commands.literal("unmute").requires(s -> Perm.has(s, Perm.MOD_MUTE))
                    .then(player().executes(ModCommands::unmute)));
        }
        if (Features.on(m, "kick")) {
            Brig.remove(d, "kick");
            d.register(Commands.literal("kick").requires(s -> Perm.has(s, Perm.MOD_KICK))
                    .then(player().executes(c -> kick(c, ""))
                            .then(Commands.argument("reason", StringArgumentType.greedyString())
                                    .executes(c -> kick(c, StringArgumentType.getString(c, "reason"))))));
        }
        if (Features.on(m, "ban")) {
            Brig.remove(d, "ban");
            d.register(Commands.literal("ban").requires(s -> Perm.has(s, Perm.MOD_BAN))
                    .then(player().executes(c -> ban(c, null))
                            .then(Commands.argument("time_and_reason", StringArgumentType.greedyString())
                                    .executes(c -> ban(c, StringArgumentType.getString(c, "time_and_reason"))))));
            d.register(Commands.literal("unban").requires(s -> Perm.has(s, Perm.MOD_BAN))
                    .then(Commands.argument("player", StringArgumentType.word()).executes(ModCommands::unban)));
        }
        if (Features.on(m, "banip")) {
            d.register(Commands.literal("banip").requires(s -> Perm.has(s, Perm.MOD_BANIP))
                    .then(Commands.argument("player_or_ip", StringArgumentType.string())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
                            .executes(c -> banIp(c, null))
                            .then(Commands.argument("time_and_reason", StringArgumentType.greedyString())
                                    .executes(c -> banIp(c, StringArgumentType.getString(c, "time_and_reason"))))));
            d.register(Commands.literal("unbanip").requires(s -> Perm.has(s, Perm.MOD_BANIP))
                    .then(Commands.argument("player_or_ip", StringArgumentType.string()).executes(ModCommands::unbanIp)));
        }
        if (Features.on(m, "freeze")) {
            d.register(Commands.literal("freeze").requires(s -> Perm.has(s, Perm.MOD_FREEZE))
                    .then(player().executes(ModCommands::freeze)));
        }
        if (Features.on(m, "jail")) {
            d.register(Commands.literal("jail").requires(s -> Perm.has(s, Perm.MOD_JAIL))
                    .then(Commands.literal("set").requires(s -> s.getPlayer() != null && Perm.has(s, Perm.MOD_JAIL_SET))
                            .executes(ModCommands::jailSet))
                    .then(player().executes(c -> jail(c, null))
                            .then(Commands.argument("time_and_reason", StringArgumentType.greedyString())
                                    .executes(c -> jail(c, StringArgumentType.getString(c, "time_and_reason"))))));
            d.register(Commands.literal("unjail").requires(s -> Perm.has(s, Perm.MOD_JAIL))
                    .then(player().executes(ModCommands::unjail)));
        }
        if (Features.on(m, "inv")) {
            d.register(Commands.literal("inv").requires(s -> s.getPlayer() != null && Perm.has(s, Perm.MOD_INV))
                    .then(player().executes(c -> inv(c, false))
                            .then(Commands.literal("ender").executes(c -> inv(c, true)))));
        }
        if (Features.on(m, "history")) {
            d.register(Commands.literal("history").requires(s -> Perm.has(s, Perm.MOD_HISTORY))
                    .then(player().executes(ModCommands::history)));
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> player() {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b));
    }

    // ---------- helpers ----------

    private static Optional<GameProfile> target(CommandContext<CommandSourceStack> c) {
        return RankCommand.profile(c.getSource().getServer(), StringArgumentType.getString(c, "player"));
    }

    private static ServerPlayer online(CommandContext<CommandSourceStack> c, GameProfile p) {
        return c.getSource().getServer().getPlayerList().getPlayer(p.getId());
    }

    private static String staffName(CommandContext<CommandSourceStack> c) {
        return c.getSource().getPlayer() == null ? com.tac5studios.elementsnexus.util.ConsoleName.plain() : c.getSource().getPlayer().getGameProfile().getName();
    }

    /** Staff can't act on OPs or on players with nexus.mod.exempt (the console can). */
    private static boolean protectedFrom(CommandContext<CommandSourceStack> c, GameProfile p) {
        if (c.getSource().getPlayer() == null) return false;
        if (c.getSource().getPlayer().getUUID().equals(p.getId())) return true;
        MinecraftServer server = c.getSource().getServer();
        if (server.getPlayerList().isOp(p)) return true;
        ServerPlayer on = server.getPlayerList().getPlayer(p.getId());
        if (on != null) return Perm.has(on, Perm.MOD_EXEMPT);
        Ranks.Result r = Ranks.check(p.getId(), Perm.MOD_EXEMPT);
        return r != null && r.allowed();
    }

    private static int withTarget(CommandContext<CommandSourceStack> c, Function<GameProfile, Integer> action) {
        Optional<GameProfile> p = target(c);
        if (p.isEmpty()) return fail(c, "No Minecraft account with that name.");
        if (protectedFrom(c, p.get())) return fail(c, "You can't do that to " + p.get().getName() + ".");
        return action.apply(p.get());
    }

    private static String forText(long seconds) {
        return seconds > 0 ? " for " + Time.text(seconds) : "";
    }

    private static String because(String reason) {
        return reason == null || reason.isEmpty() ? "" : " (" + reason + ")";
    }

    // ---------- /warn ----------

    private static int warn(CommandContext<CommandSourceStack> c) {
        return withTarget(c, p -> {
            String reason = StringArgumentType.getString(c, "reason");
            ServerPlayer on = online(c, p);
            if (on != null) on.sendSystemMessage(Text.color("&c&lWarning: &r&c" + reason));
            StaffLog.add(c.getSource(), "warn", p.getId(), p.getName(), reason, null);
            return ok(c, "&aWarned " + p.getName() + because(reason) + (on == null ? " &7(they'll see it in /history only)" : ""));
        });
    }

    // ---------- /mute ----------

    private static int mute(CommandContext<CommandSourceStack> c, String rest) {
        return withTarget(c, p -> {
            TimeReason tr = TimeReason.of(rest, true);
            Punish d = Punish.of(p.getId());
            Punish.Timed mute = new Punish.Timed();
            mute.until = tr.seconds() > 0 ? System.currentTimeMillis() + tr.seconds() * 1000 : 0;
            mute.reason = tr.reason();
            mute.by = staffName(c);
            d.mute = mute;
            d.save(p.getId());
            ServerPlayer on = online(c, p);
            if (on != null) on.sendSystemMessage(Text.color("&cYou have been muted" + forText(tr.seconds()) + because(tr.reason()) + "."));
            StaffLog.add(c.getSource(), "mute", p.getId(), p.getName(), tr.reason(), tr.seconds() > 0 ? Time.text(tr.seconds()) : "until removed");
            return ok(c, "&aMuted " + p.getName() + forText(tr.seconds()) + because(tr.reason()) + ".");
        });
    }

    private static int unmute(CommandContext<CommandSourceStack> c) {
        Optional<GameProfile> p = target(c);
        if (p.isEmpty()) return fail(c, "No Minecraft account with that name.");
        Punish d = Punish.of(p.get().getId());
        if (d.mute == null) return fail(c, p.get().getName() + " is not muted.");
        d.mute = null;
        d.save(p.get().getId());
        ServerPlayer on = online(c, p.get());
        if (on != null) on.sendSystemMessage(Text.color("&aYou can chat again."));
        StaffLog.add(c.getSource(), "unmute", p.get().getId(), p.get().getName(), null, null);
        return ok(c, "&aUnmuted " + p.get().getName() + ".");
    }

    // ---------- /kick ----------

    private static int kick(CommandContext<CommandSourceStack> c, String reason) {
        return withTarget(c, p -> {
            ServerPlayer on = online(c, p);
            if (on == null) return fail(c, p.getName() + " is not online.");
            on.connection.disconnect(Component.literal(reason.isEmpty() ? "You were kicked." : "You were kicked: " + reason));
            StaffLog.add(c.getSource(), "kick", p.getId(), p.getName(), reason, null);
            return ok(c, "&aKicked " + p.getName() + because(reason) + ".");
        });
    }

    // ---------- /ban ----------

    private static int ban(CommandContext<CommandSourceStack> c, String rest) {
        return withTarget(c, p -> {
            TimeReason tr = TimeReason.of(rest, Features.on("moderation", "tempban"));
            Date until = tr.seconds() > 0 ? new Date(System.currentTimeMillis() + tr.seconds() * 1000) : null;
            String reason = tr.reason().isEmpty() ? "Banned by staff." : tr.reason();
            var bans = c.getSource().getServer().getPlayerList().getBans();
            bans.add(new UserBanListEntry(p, new Date(), staffName(c), until, reason));
            ServerPlayer on = online(c, p);
            if (on != null) {
                on.connection.disconnect(Component.literal("You are banned" + (tr.seconds() > 0 ? forText(tr.seconds()) : " permanently") + ": " + reason));
            }
            StaffLog.add(c.getSource(), "ban", p.getId(), p.getName(), tr.reason(), tr.seconds() > 0 ? Time.text(tr.seconds()) : "permanent");
            return ok(c, "&aBanned " + p.getName() + (tr.seconds() > 0 ? forText(tr.seconds()) : " permanently") + because(tr.reason()) + ".");
        });
    }

    private static int unban(CommandContext<CommandSourceStack> c) {
        Optional<GameProfile> p = target(c);
        if (p.isEmpty()) return fail(c, "No Minecraft account with that name.");
        var bans = c.getSource().getServer().getPlayerList().getBans();
        if (!bans.isBanned(p.get())) return fail(c, p.get().getName() + " is not banned.");
        bans.remove(p.get());
        StaffLog.add(c.getSource(), "unban", p.get().getId(), p.get().getName(), null, null);
        return ok(c, "&aUnbanned " + p.get().getName() + ".");
    }

    // ---------- /banip ----------

    private static final java.util.regex.Pattern IP = java.util.regex.Pattern.compile("^[0-9a-fA-F:.]+$");

    /** An IP typed directly, or the IP of a player (online, or the last one they joined from). */
    private static String ipOf(CommandContext<CommandSourceStack> c, String who) {
        if (who.contains(".") && IP.matcher(who).matches() || who.contains(":") && IP.matcher(who).matches()) return who;
        Optional<GameProfile> p = RankCommand.profile(c.getSource().getServer(), who);
        if (p.isEmpty()) return null;
        ServerPlayer on = online(c, p.get());
        if (on != null) return on.getIpAddress();
        String last = Ranks.user(p.get().getId()).lastIp;
        return last == null || last.isEmpty() ? null : last;
    }

    private static int banIp(CommandContext<CommandSourceStack> c, String rest) {
        String who = StringArgumentType.getString(c, "player_or_ip");
        Optional<GameProfile> p = IP.matcher(who).matches() && (who.contains(".") || who.contains(":"))
                ? Optional.empty() : RankCommand.profile(c.getSource().getServer(), who);
        if (p.isPresent() && protectedFrom(c, p.get())) return fail(c, "You can't do that to " + p.get().getName() + ".");
        String ip = ipOf(c, who);
        if (ip == null) return fail(c, "No IP known for " + who + ". They need to have joined at least once.");

        TimeReason tr = TimeReason.of(rest, Features.on("moderation", "tempban"));
        Date until = tr.seconds() > 0 ? new Date(System.currentTimeMillis() + tr.seconds() * 1000) : null;
        String reason = tr.reason().isEmpty() ? "Banned by staff." : tr.reason();
        c.getSource().getServer().getPlayerList().getIpBans()
                .add(new net.minecraft.server.players.IpBanListEntry(ip, new Date(), staffName(c), until, reason));
        int kicked = 0;
        for (ServerPlayer on : List.copyOf(c.getSource().getServer().getPlayerList().getPlayers())) {
            if (ip.equals(on.getIpAddress())) {
                on.connection.disconnect(Component.literal("You are banned" + forText(tr.seconds()) + ": " + reason));
                kicked++;
            }
        }
        StaffLog.add(c.getSource(), "ban ip", p.map(GameProfile::getId).orElse(null), p.map(GameProfile::getName).orElse(ip),
                tr.reason(), tr.seconds() > 0 ? Time.text(tr.seconds()) : "permanent");
        return ok(c, "&aBanned IP of " + p.map(GameProfile::getName).orElse(ip) + (tr.seconds() > 0 ? forText(tr.seconds()) : " permanently")
                + because(tr.reason()) + (kicked > 0 ? " &7(" + kicked + " kicked)" : "") + ".");
    }

    private static int unbanIp(CommandContext<CommandSourceStack> c) {
        String who = StringArgumentType.getString(c, "player_or_ip");
        String ip = ipOf(c, who);
        if (ip == null) return fail(c, "No IP known for " + who + ".");
        var bans = c.getSource().getServer().getPlayerList().getIpBans();
        if (!bans.isBanned(ip)) return fail(c, ip.equals(who) ? who + " is not banned." : who + "'s IP is not banned.");
        bans.remove(ip);
        StaffLog.add(c.getSource(), "unban ip", null, who, null, null);
        return ok(c, "&aUnbanned IP of " + who + ".");
    }

    // ---------- /freeze ----------

    private static int freeze(CommandContext<CommandSourceStack> c) {
        return withTarget(c, p -> {
            ServerPlayer on = online(c, p);
            if (on == null) return fail(c, p.getName() + " is not online.");
            boolean now = !Moderation.isFrozen(on);
            Moderation.freeze(on, now);
            on.sendSystemMessage(Text.color(now ? "&cYou have been frozen by staff." : "&aYou can move again."));
            StaffLog.add(c.getSource(), now ? "freeze" : "unfreeze", p.getId(), p.getName(), null, null);
            return ok(c, "&a" + (now ? "Froze " : "Unfroze ") + p.getName() + ".");
        });
    }

    // ---------- /jail ----------

    private static int jailSet(CommandContext<CommandSourceStack> c) {
        Moderation.setJailSpot(Spot.of(c.getSource().getPlayer()));
        StaffLog.add(c.getSource(), "jail set", null, null, null, null);
        return ok(c, "&aJail set here.");
    }

    private static int jail(CommandContext<CommandSourceStack> c, String rest) {
        return withTarget(c, p -> {
            ServerPlayer on = online(c, p);
            if (on == null) return fail(c, p.getName() + " is not online.");
            TimeReason tr = TimeReason.of(rest, true);
            if (!Moderation.jail(on, tr.seconds(), tr.reason(), staffName(c))) return fail(c, "No jail is set. Stand in the jail and use /jail set.");
            StaffLog.add(c.getSource(), "jail", p.getId(), p.getName(), tr.reason(), tr.seconds() > 0 ? Time.text(tr.seconds()) : "until removed");
            return ok(c, "&aJailed " + p.getName() + forText(tr.seconds()) + because(tr.reason()) + ".");
        });
    }

    private static int unjail(CommandContext<CommandSourceStack> c) {
        Optional<GameProfile> p = target(c);
        if (p.isEmpty()) return fail(c, "No Minecraft account with that name.");
        Punish d = Punish.of(p.get().getId());
        if (d.jail == null) return fail(c, p.get().getName() + " is not in jail.");
        ServerPlayer on = online(c, p.get());
        if (on != null) {
            Moderation.release(on);
        } else {
            d.jail.until = 1; // ends now; they're released when they next join
            d.save(p.get().getId());
        }
        StaffLog.add(c.getSource(), "unjail", p.get().getId(), p.get().getName(), null, null);
        return ok(c, "&aReleased " + p.get().getName() + (on == null ? " &7(when they next join)" : "") + ".");
    }

    // ---------- /inv ----------

    private static int inv(CommandContext<CommandSourceStack> c, boolean ender) {
        Optional<GameProfile> p = target(c);
        if (p.isEmpty()) return fail(c, "No Minecraft account with that name.");
        ServerPlayer on = online(c, p.get());
        if (on == null) return fail(c, p.get().getName() + " is not online.");
        ServerPlayer staff = c.getSource().getPlayer();
        boolean edit = Features.on("moderation", "inv_edit") && Perm.has(staff, Perm.MOD_INV_EDIT) && !protectedFrom(c, p.get());
        if (ender) InvView.openEnderChest(staff, on, edit);
        else InvView.openInventory(staff, on, edit);
        if (edit) StaffLog.add(c.getSource(), ender ? "ender chest edit" : "inventory edit", p.get().getId(), p.get().getName(), null, null);
        return 1;
    }

    // ---------- /history ----------

    private static int history(CommandContext<CommandSourceStack> c) {
        Optional<GameProfile> p = target(c);
        if (p.isEmpty()) return fail(c, "No Minecraft account with that name.");
        List<JsonObject> lines = StaffLog.about(c.getSource().getServer(), p.get().getId(), 10);
        Punish d = Punish.of(p.get().getId());
        StringBuilder sb = new StringBuilder("&6History for " + p.get().getName() + ":");
        if (d.mute != null && d.mute.active()) sb.append("\n&cMuted now").append(d.mute.secondsLeft() > 0 ? " (" + Time.text(d.mute.secondsLeft()) + " left)" : "");
        if (d.jail != null) sb.append("\n&cIn jail now");
        if (c.getSource().getServer().getPlayerList().getBans().get(p.get()) != null) sb.append("\n&cBanned now");
        if (lines.isEmpty()) sb.append("\n&7Nothing on record.");
        for (JsonObject o : lines) {
            sb.append("\n&8").append(o.get("time").getAsString(), 0, 10).append(" &f").append(o.get("action").getAsString())
                    .append(" &7by ").append(o.get("staff").getAsString());
            if (o.has("duration")) sb.append(" &7(").append(o.get("duration").getAsString()).append(")");
            if (o.has("reason")) sb.append(" &7- ").append(o.get("reason").getAsString());
        }
        return ok(c, sb.toString());
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
