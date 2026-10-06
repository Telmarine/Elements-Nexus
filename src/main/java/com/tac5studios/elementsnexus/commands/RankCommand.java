package com.tac5studios.elementsnexus.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.ranks.Rank;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /rank set <player> <rank>
 * /rank info <player>
 * /rank check <player> <node>
 * /rank group list | info | create | delete | default | priority | prefix | color | chatcolor | inherit add/remove | perm add/remove
 */
public final class RankCommand {

    private RankCommand() {}

    private static final SuggestionProvider<CommandSourceStack> RANKS =
            (ctx, b) -> SharedSuggestionProvider.suggest(Ranks.names(), b);

    private static final SuggestionProvider<CommandSourceStack> PLAYERS =
            (ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("ranks")) return;

        // Hide /rank completely from anyone who can't use any part of it.
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("rank").requires(s ->
                Perm.has(s, Perm.RANK_SET) || Perm.has(s, Perm.RANK_INFO)
                        || Perm.has(s, Perm.RANK_CHECK) || Perm.has(s, Perm.RANK_ADMIN));

        if (Features.on("ranks", "rank_set")) {
            root.then(Commands.literal("set").requires(s -> Perm.has(s, Perm.RANK_SET))
                    .then(Commands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
                            .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                    .executes(RankCommand::set))));
        }
        if (Features.on("ranks", "rank_info")) {
            root.then(Commands.literal("info").requires(s -> Perm.has(s, Perm.RANK_INFO))
                    .then(Commands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
                            .executes(RankCommand::info)));
        }
        if (Features.on("ranks", "rank_check")) {
            root.then(Commands.literal("check").requires(s -> Perm.has(s, Perm.RANK_CHECK))
                    .then(Commands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                    .executes(RankCommand::check))));
        }
        if (Features.on("ranks", "group_admin")) {
            root.then(group());
        }
        d.register(root);
    }

    // ---------- /rank set ----------

    private static int set(CommandContext<CommandSourceStack> c) {
        String rank = StringArgumentType.getString(c, "rank").toLowerCase(Locale.ROOT);
        if (!Ranks.exists(rank)) return fail(c, "There is no rank called " + rank + ".");
        Optional<GameProfile> who = profile(c.getSource().getServer(), StringArgumentType.getString(c, "player"));
        if (who.isEmpty()) return fail(c, "No Minecraft account with that name.");
        GameProfile p = who.get();

        String old = Ranks.user(p.getId()).rank;
        Ranks.setRank(p.getId(), p.getName(), rank);
        refresh(c.getSource().getServer(), p);
        com.tac5studios.elementsnexus.ranks.RankUps.changed(c.getSource().getServer(), p.getId(), p.getName(),
                old == null || old.isEmpty() ? Ranks.defaultRankName() : old, rank);
        com.tac5studios.elementsnexus.moderation.StaffLog.add(c.getSource(), "rank set", p.getId(), p.getName(), null, rank);
        return ok(c, "&a" + p.getName() + " is now " + rank + ".");
    }

    // ---------- /rank info ----------

    private static int info(CommandContext<CommandSourceStack> c) {
        Optional<GameProfile> who = profile(c.getSource().getServer(), StringArgumentType.getString(c, "player"));
        if (who.isEmpty()) return fail(c, "No Minecraft account with that name.");
        GameProfile p = who.get();
        String rank = Ranks.rankOf(p.getId());
        Rank r = Ranks.rank(rank);
        List<String> chain = Ranks.chain(rank);
        String gets = chain.size() > 1 ? String.join(", ", chain.subList(1, chain.size())) : "nothing";
        return ok(c, "&6" + p.getName() + "&7: rank &f" + rank + "&7, tag " + (r == null ? "" : r.prefix)
                + "&7, also gets permissions from: &f" + gets);
    }

    // ---------- /rank check ----------

    private static int check(CommandContext<CommandSourceStack> c) {
        String node = StringArgumentType.getString(c, "node").trim().toLowerCase(Locale.ROOT);
        MinecraftServer server = c.getSource().getServer();
        Optional<GameProfile> who = profile(server, StringArgumentType.getString(c, "player"));
        if (who.isEmpty()) return fail(c, "No Minecraft account with that name.");
        GameProfile p = who.get();

        // Number and text nodes come from ranks only (OP doesn't change them), so show the value first.
        Ranks.Value v = Ranks.value(p.getId(), node);
        if (v != null) {
            return ok(c, "&a" + p.getName() + " has " + node + " = &f" + v.value() + " &7(from " + v.source() + ")");
        }
        if (Features.on("ranks", "ops_bypass") && server.getPlayerList().isOp(p)) {
            return ok(c, "&a" + p.getName() + " has " + node + " &7(they are OP)");
        }
        Ranks.Result r = Ranks.check(p.getId(), node);
        if (r != null) {
            return ok(c, (r.allowed() ? "&a" + p.getName() + " has " : "&c" + p.getName() + " does not have ")
                    + node + " &7(from " + r.source() + ")");
        }
        ServerPlayer online = server.getPlayerList().getPlayer(p.getId());
        String def = Perm.defaultText(node);
        if (online != null && !def.equals("not a Nexus node")) {
            boolean yes = Perm.defaultFor(online, node);
            return ok(c, (yes ? "&a" + p.getName() + " has " : "&c" + p.getName() + " does not have ")
                    + node + " &7(no rank sets it; default is " + def + ")");
        }
        return ok(c, "&7No rank sets " + node + " for " + p.getName() + ". Default: " + def + ".");
    }

    // ---------- /rank group ----------

    private static LiteralArgumentBuilder<CommandSourceStack> group() {
        return Commands.literal("group").requires(s -> Perm.has(s, Perm.RANK_ADMIN))
                .then(Commands.literal("list").executes(RankCommand::groupList))
                .then(Commands.literal("info")
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                .executes(RankCommand::groupInfo)))
                .then(Commands.literal("create")
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .executes(RankCommand::groupCreate)))
                .then(Commands.literal("delete")
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                .executes(RankCommand::groupDelete)))
                .then(Commands.literal("default")
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                .executes(RankCommand::groupDefault)))
                .then(Commands.literal("priority")
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                .then(Commands.argument("number", IntegerArgumentType.integer(0, 1000))
                                        .executes(RankCommand::groupPriority))))
                .then(Commands.literal("prefix")
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(RankCommand::groupPrefix))))
                .then(Commands.literal("homes")
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                .then(Commands.argument("number", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("1", "2", "3", "5", "10", "unlimited"), b))
                                        .executes(RankCommand::groupHomes))))
                .then(Commands.literal("color")
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                .then(Commands.argument("color", StringArgumentType.greedyString())
                                        .executes(RankCommand::groupColor))))
                .then(Commands.literal("chatcolor")
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                .then(Commands.argument("color", StringArgumentType.greedyString())
                                        .executes(RankCommand::groupChatColor))))
                .then(Commands.literal("inherit")
                        .then(Commands.literal("add")
                                .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                        .then(Commands.argument("parent", StringArgumentType.word()).suggests(RANKS)
                                                .executes(c -> groupInherit(c, true)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                        .then(Commands.argument("parent", StringArgumentType.word()).suggests(RANKS)
                                                .executes(c -> groupInherit(c, false))))))
                .then(Commands.literal("announce")
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                .executes(RankCommand::announceShow)
                                .then(Commands.literal("on").executes(c -> announceSet(c, true)))
                                .then(Commands.literal("off").executes(c -> announceSet(c, false)))))
                .then(Commands.literal("onpromote")
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                .executes(RankCommand::promoteList)
                                .then(Commands.literal("add")
                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                .executes(RankCommand::promoteAdd)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("number", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .executes(RankCommand::promoteRemove)))))
                .then(Commands.literal("perm")
                        .then(Commands.literal("add")
                                .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                        .then(Commands.argument("node", StringArgumentType.greedyString())
                                                .executes(c -> groupPerm(c, true)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                        .then(Commands.argument("node", StringArgumentType.greedyString())
                                                .executes(c -> groupPerm(c, false))))));
    }

    private static int groupList(CommandContext<CommandSourceStack> c) {
        StringBuilder sb = new StringBuilder("&6Ranks (highest first):");
        for (String n : Ranks.names()) {
            Rank r = Ranks.rank(n);
            sb.append("\n&7- &f").append(n).append(" &7(").append(r.priority).append(") ").append(r.prefix)
                    .append(r.isDefault ? " &e[default]" : "");
        }
        return ok(c, sb.toString());
    }

    private static int groupInfo(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        return ok(c, "&6" + n + (r.isDefault ? " &e[default]" : "")
                + "\n&7Tag: " + r.prefix
                + "\n&7Color: " + (r.color == null || r.color.isEmpty() ? "&fnone" : r.color + "this")
                + "\n&7Chat color: " + (r.chatColor == null || r.chatColor.isEmpty() ? "&fnone" : r.chatColor + "this")
                + "\n&7Priority: &f" + r.priority
                + "\n&7Inherits: &f" + (r.inherits.isEmpty() ? "nothing" : String.join(", ", r.inherits))
                + "\n&7Permissions: &f" + (r.permissions.isEmpty() ? "none" : String.join(", ", r.permissions)));
    }

    private static int groupCreate(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        if (!Ranks.VALID_NAME.matcher(n).matches()) return fail(c, "Rank names use only a-z, 0-9 and _ (max 32).");
        if (Ranks.exists(n)) return fail(c, "Rank " + n + " already exists.");
        Ranks.saveRank(n, new Rank());
        return ok(c, "&aCreated rank " + n + ".");
    }

    private static int groupDelete(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        if (r.isDefault) return fail(c, "You can't delete the default rank. Pick another default first.");
        Ranks.deleteRank(n);
        refreshAll(c.getSource().getServer());
        return ok(c, "&aDeleted rank " + n + ". Players in it now use the default rank.");
    }

    private static int groupDefault(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        if (!Ranks.exists(n)) return fail(c, "There is no rank called " + n + ".");
        Ranks.setDefault(n);
        refreshAll(c.getSource().getServer());
        return ok(c, "&a" + n + " is now the default rank.");
    }

    private static int groupPriority(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        r.priority = IntegerArgumentType.getInteger(c, "number");
        Ranks.saveRank(n, r);
        return ok(c, "&a" + n + " priority set to " + r.priority + ".");
    }

    private static int groupPrefix(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        String text = StringArgumentType.getString(c, "text");
        // Strip outer quotes so a trailing space can be kept: "&6[VIP] "
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length() - 1);
        r.prefix = text.equalsIgnoreCase("none") ? "" : text;
        Ranks.saveRank(n, r);
        return ok(c, "&a" + n + " tag set to: &r" + r.prefix);
    }

    /** Set how many homes a rank gets (writes the nexus.home.limit.<n> node). */
    private static int groupHomes(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        String v = StringArgumentType.getString(c, "number").toLowerCase(Locale.ROOT);
        if (!v.equals("unlimited")) {
            try {
                int num = Integer.parseInt(v);
                if (num < 0 || num > 1000) return fail(c, "Use a number from 0 to 1000, or unlimited.");
                v = String.valueOf(num);
            } catch (NumberFormatException e) {
                return fail(c, "Use a number from 0 to 1000, or unlimited.");
            }
        }
        r.permissions.removeIf(p -> p.startsWith("nexus.home.limit."));
        r.permissions.add("nexus.home.limit." + v);
        Ranks.saveRank(n, r);
        return ok(c, "&a" + n + " can now have " + (v.equals("unlimited") ? "unlimited" : v) + " homes.");
    }

    private static int groupColor(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        String col = StringArgumentType.getString(c, "color").trim();
        if (col.matches("#[0-9a-fA-F]{6}")) col = "&" + col;
        if (!col.matches("&[0-9a-fA-F]|&#[0-9a-fA-F]{6}")) return fail(c, "Use a color like &6 or #FFD700.");
        r.color = col;
        Ranks.saveRank(n, r);
        return ok(c, "&a" + n + " color set to " + col + "this&a.");
    }

    /** Chat message color for a rank. "none" clears it (chat.toml's color is used). */
    private static int groupChatColor(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        String col = StringArgumentType.getString(c, "color").trim();
        if (col.equalsIgnoreCase("none")) {
            r.chatColor = "";
            Ranks.saveRank(n, r);
            return ok(c, "&a" + n + " chat color cleared.");
        }
        if (col.matches("#[0-9a-fA-F]{6}")) col = "&" + col;
        if (!col.matches("&[0-9a-fA-F]|&#[0-9a-fA-F]{6}")) return fail(c, "Use a color like &b or #2ECC71, or none.");
        r.chatColor = col;
        Ranks.saveRank(n, r);
        return ok(c, "&a" + n + " chat color set to " + col + "this&a.");
    }

    private static int groupInherit(CommandContext<CommandSourceStack> c, boolean add) {
        String n = rankArg(c);
        String parent = StringArgumentType.getString(c, "parent").toLowerCase(Locale.ROOT);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        if (add) {
            if (!Ranks.exists(parent)) return fail(c, "There is no rank called " + parent + ".");
            if (r.inherits.contains(parent)) return fail(c, n + " already gets permissions from " + parent + ".");
            if (Ranks.wouldLoop(n, parent)) return fail(c, "That would make a loop (" + parent + " already gets permissions from " + n + ").");
            r.inherits.add(parent);
        } else if (!r.inherits.remove(parent)) {
            return fail(c, n + " does not get permissions from " + parent + ".");
        }
        Ranks.saveRank(n, r);
        refreshAll(c.getSource().getServer());
        return ok(c, "&a" + n + (add ? " now gets permissions from " : " no longer gets permissions from ") + parent + ".");
    }

    private static int groupPerm(CommandContext<CommandSourceStack> c, boolean add) {
        String n = rankArg(c);
        String node = StringArgumentType.getString(c, "node").trim().toLowerCase(Locale.ROOT);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        if (node.isEmpty() || node.contains(" ")) return fail(c, "A node is one word, like nexus.home.use, nexus.home.* or xaero.pac_max_claims=200");
        int eq = node.indexOf('=');
        if (eq == 0 || eq == node.length() - 1) return fail(c, "A value node looks like xaero.pac_max_claims=200");
        if (add) {
            if (r.permissions.contains(node)) return fail(c, n + " already has " + node + ".");
            // A new value for the same node replaces the old one.
            if (eq > 0) {
                String key = node.substring(0, eq + 1);
                r.permissions.removeIf(e -> e.startsWith(key));
            }
            r.permissions.add(node);
        } else if (!r.permissions.remove(node)) {
            // "remove xaero.pac_max_claims" also removes "xaero.pac_max_claims=<any value>"
            String key = node + "=";
            if (eq > 0 || !r.permissions.removeIf(e -> e.startsWith(key))) {
                return fail(c, n + " does not have " + node + ".");
            }
        }
        Ranks.saveRank(n, r);
        refreshAll(c.getSource().getServer());
        return ok(c, "&a" + (add ? "Added " : "Removed ") + node + (add ? " to " : " from ") + n + ".");
    }

    // ---------- /rank group announce ----------

    private static int announceShow(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        return ok(c, "&7Rank-ups to " + n + " are " + (r.announce ? "&aannounced" : "&cnot announced") + "&7.");
    }

    private static int announceSet(CommandContext<CommandSourceStack> c, boolean on) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        r.announce = on;
        Ranks.saveRank(n, r);
        com.tac5studios.elementsnexus.moderation.StaffLog.add(c.getSource(), "rank announce " + (on ? "on" : "off"), null, null, n, null);
        return ok(c, "&aRank-ups to " + n + " are now " + (on ? "announced" : "not announced") + ".");
    }

    // ---------- /rank group onpromote ----------

    private static int promoteList(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        if (r.onPromote.isEmpty()) return ok(c, "&7" + n + " runs no commands on rank-up.");
        StringBuilder sb = new StringBuilder("&6Run when a player ranks up to " + n + ":");
        for (int i = 0; i < r.onPromote.size(); i++) sb.append("\n&e").append(i + 1).append(". &f").append(r.onPromote.get(i));
        return ok(c, sb.toString());
    }

    private static int promoteAdd(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        String cmd = StringArgumentType.getString(c, "command").trim();
        r.onPromote.add(cmd);
        Ranks.saveRank(n, r);
        com.tac5studios.elementsnexus.moderation.StaffLog.add(c.getSource(), "rank onpromote add", null, null, n + ": " + cmd, null);
        return ok(c, "&aAdded. " + n + " now runs " + r.onPromote.size() + " command(s) on rank-up.");
    }

    private static int promoteRemove(CommandContext<CommandSourceStack> c) {
        String n = rankArg(c);
        Rank r = Ranks.rank(n);
        if (r == null) return fail(c, "There is no rank called " + n + ".");
        int i = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "number");
        if (i > r.onPromote.size()) return fail(c, n + " has only " + r.onPromote.size() + " command(s).");
        String old = r.onPromote.remove(i - 1);
        Ranks.saveRank(n, r);
        com.tac5studios.elementsnexus.moderation.StaffLog.add(c.getSource(), "rank onpromote remove", null, null, n + ": " + old, null);
        return ok(c, "&aRemoved: &f" + old);
    }

    // ---------- helpers ----------

    private static String rankArg(CommandContext<CommandSourceStack> c) {
        return StringArgumentType.getString(c, "rank").toLowerCase(Locale.ROOT);
    }

    /** Find a player by name: online first, then anyone who has joined before. */
    static Optional<GameProfile> profile(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return Optional.of(online.getGameProfile());
        return server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(name);
    }

    /** Resend the command list so the player sees commands they just gained or lost. */
    private static void refresh(MinecraftServer server, GameProfile p) {
        ServerPlayer online = server.getPlayerList().getPlayer(p.getId());
        if (online != null) server.getCommands().sendCommands(online);
    }

    private static void refreshAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(p);
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
