package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.tac5studios.elementsnexus.chat.Chat;
import com.tac5studios.elementsnexus.chat.ChatData;
import com.tac5studios.elementsnexus.chat.ChatFilter;
import com.tac5studios.elementsnexus.chat.StaffChat;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Brig;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /msg <player> <message>   (also /tell and /w)
 * /msg spy                  (staff: see private messages)
 * /r <message>
 * /ignore <player>          (again to stop ignoring)
 * /sc [message]             (staff chat; no message = toggle staff-chat mode)
 */
public final class ChatCommands {

    /** Who each player last messaged or got a message from (for /r). */
    private static final Map<UUID, UUID> REPLY = new HashMap<>();

    private ChatCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (Features.on("messaging")) {
            if (Features.on("messaging", "msg")) {
                // Replace vanilla /msg, /tell and /w with ours.
                Brig.remove(d, "msg");
                Brig.remove(d, "tell");
                Brig.remove(d, "w");
                LiteralArgumentBuilder<CommandSourceStack> msg = Commands.literal("msg")
                        .requires(s -> s.getPlayer() == null || Perm.has(s, Perm.MSG_USE))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(ChatCommands::msg)));
                if (Features.on("messaging", "socialspy")) {
                    msg.then(Commands.literal("spy").requires(s -> s.getPlayer() != null && Perm.has(s, Perm.MSG_SPY))
                            .executes(ChatCommands::spy));
                }
                LiteralCommandNode<CommandSourceStack> node = d.register(msg);
                d.register(Commands.literal("tell").requires(node.getRequirement()).redirect(node));
                d.register(Commands.literal("w").requires(node.getRequirement()).redirect(node));
            }
            if (Features.on("messaging", "reply")) {
                d.register(Commands.literal("r").requires(s -> s.getPlayer() != null && Perm.has(s, Perm.MSG_USE))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ChatCommands::reply)));
            }
            if (Features.on("messaging", "ignore")) {
                d.register(Commands.literal("ignore").requires(s -> s.getPlayer() != null && Perm.has(s, Perm.MSG_USE))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
                                .executes(ChatCommands::ignore)));
            }
        }
        if (Features.on("staffchat")) {
            d.register(Commands.literal("sc").requires(s -> Perm.has(s, Perm.STAFFCHAT))
                    .executes(ChatCommands::scToggle)
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(c -> {
                                StaffChat.send(c.getSource().getServer(), c.getSource().getPlayer(),
                                        StringArgumentType.getString(c, "message"));
                                return 1;
                            })));
        }
    }

    // ---------- /msg ----------

    private static int msg(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        ServerPlayer to = com.tac5studios.elementsnexus.vanish.Vanish.findVisible(c.getSource(), StringArgumentType.getString(c, "player"));
        if (to == null) return fail(c, "That player is not online.");
        return send(c, to, StringArgumentType.getString(c, "message"));
    }

    private static int reply(CommandContext<CommandSourceStack> c) {
        ServerPlayer from = c.getSource().getPlayer();
        UUID last = REPLY.get(from.getUUID());
        ServerPlayer to = last == null ? null : c.getSource().getServer().getPlayerList().getPlayer(last);
        if (to == null) return fail(c, "There is no one to reply to.");
        return send(c, to, StringArgumentType.getString(c, "message"));
    }

    private static int send(CommandContext<CommandSourceStack> c, ServerPlayer to, String text) {
        MinecraftServer server = c.getSource().getServer();
        ServerPlayer from = c.getSource().getPlayer();
        String fromName = from == null ? com.tac5studios.elementsnexus.util.ConsoleName.plain() : from.getGameProfile().getName();
        String toName = to.getGameProfile().getName();
        String fromShown = from == null ? com.tac5studios.elementsnexus.util.ConsoleName.raw() : com.tac5studios.elementsnexus.nick.Nick.display(from);
        String toShown = com.tac5studios.elementsnexus.nick.Nick.display(to);
        boolean staff = from == null || Perm.has(from, Perm.CHAT_STAFF);

        if (from != null) {
            if (from == to) return fail(c, "You can't message yourself.");
            if (Chat.muted.test(from)) return fail(c, "You are muted.");
            String jailNote = Chat.publicBlocked.apply(from);
            if (jailNote != null && !Perm.has(to, Perm.STAFFCHAT)) {
                from.sendSystemMessage(Text.color(jailNote));
                return 0;
            }
            if (Features.on("chat", "filter") && !Perm.has(from, ChatFilter.bypassNode())) {
                ChatFilter.Result r = ChatFilter.check(text);
                if (r.kind() == ChatFilter.Kind.BLOCK) {
                    if (ChatFilter.alertStaff()) StaffChat.alert(server, "&c[Filter] &f" + fromName + " &7(msg to " + toName + "): " + text);
                    return fail(c, "That message was not sent. Please keep chat friendly.");
                }
                text = r.text();
            }
            if (!staff && ChatData.of(to.getUUID()).ignores.contains(from.getUUID().toString())) {
                return fail(c, toName + " is not taking messages from you.");
            }
            REPLY.put(from.getUUID(), to.getUUID());
        }
        REPLY.put(to.getUUID(), from == null ? null : from.getUUID());

        var f = Chat.file();
        c.getSource().sendSystemMessage(line(server, f.str("private_messages.sent", "&7[me -> {to}] &f{message}"), fromShown, toShown, text, staff));
        to.sendSystemMessage(line(server, f.str("private_messages.received", "&7[{from} -> me] &f{message}"), fromShown, toShown, text, staff));

        if (Features.on("messaging", "socialspy")) {
            Component spy = line(server, f.str("private_messages.spy", "&8[Spy] {from} -> {to}: {message}"), fromName, toName, text, false);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p == from || p == to) continue;
                if (Perm.has(p, Perm.MSG_SPY) && ChatData.of(p.getUUID()).spy) p.sendSystemMessage(spy);
            }
        }
        return 1;
    }

    private static Component line(MinecraftServer server, String fmt, String from, String to, String text, boolean colors) {
        fmt = fmt.replace("{from}", from).replace("{to}", to);
        int at = fmt.indexOf("{message}");
        String before = at < 0 ? fmt : fmt.substring(0, at);
        String after = at < 0 ? "" : fmt.substring(at + "{message}".length());
        // The message keeps the last color used before {message}.
        String color = lastCodes(before);
        MutableComponent out = Component.empty().append(Text.color(Fancy.apply(before, 0)));
        out.append(Chat.message(server, color, text, colors));
        out.append(Text.color(Fancy.apply(after, 0)));
        return out;
    }

    private static String lastCodes(String s) {
        int i = s.lastIndexOf('&');
        if (i < 0 || i + 1 >= s.length()) return "&f";
        if (s.charAt(i + 1) == '#' && i + 8 <= s.length()) return s.substring(i, i + 8);
        return s.substring(i, i + 2);
    }

    // ---------- /msg spy ----------

    private static int spy(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        ChatData d = ChatData.of(p.getUUID());
        d.spy = !d.spy;
        d.save(p.getUUID());
        return ok(c, d.spy ? "&aSocial spy on." : "&7Social spy off.");
    }

    // ---------- /ignore ----------

    private static int ignore(CommandContext<CommandSourceStack> c) {
        ServerPlayer me = c.getSource().getPlayer();
        var who = RankCommand.profile(c.getSource().getServer(), StringArgumentType.getString(c, "player"));
        if (who.isEmpty()) return fail(c, "No Minecraft account with that name.");
        UUID id = who.get().getId();
        if (id.equals(me.getUUID())) return fail(c, "You can't ignore yourself.");
        ChatData d = ChatData.of(me.getUUID());
        String key = id.toString();
        if (d.ignores.remove(key)) {
            d.save(me.getUUID());
            return ok(c, "&7You are no longer ignoring " + who.get().getName() + ".");
        }
        ServerPlayer online = c.getSource().getServer().getPlayerList().getPlayer(id);
        if (online != null && Perm.has(online, Perm.CHAT_STAFF)) return fail(c, "You can't ignore staff.");
        d.ignores.add(key);
        d.save(me.getUUID());
        return ok(c, "&7You are now ignoring " + who.get().getName() + ". Use the same command again to stop.");
    }

    // ---------- /sc ----------

    private static int scToggle(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return fail(c, "Use /sc <message> from the console.");
        boolean on = Chat.STAFF_MODE.add(p.getUUID());
        if (!on) Chat.STAFF_MODE.remove(p.getUUID());
        return ok(c, on ? "&cStaff chat mode on. &7Your chat now goes to staff only." : "&7Staff chat mode off.");
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
