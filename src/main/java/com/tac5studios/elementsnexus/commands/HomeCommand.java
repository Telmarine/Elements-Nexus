package com.tac5studios.elementsnexus.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.homes.Homes;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.teleport.Teleports;
import com.tac5studios.elementsnexus.teleport.Teleports.Spot;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * /home [name]              go home ("home" if no name, or your only home)
 * /home <player>:<name>     staff: visit someone else's home
 * /sethome [name]
 * /delhome <name>
 * /homes [player]           list (staff can list someone else's)
 */
public final class HomeCommand {

    private HomeCommand() {}

    private static final SuggestionProvider<CommandSourceStack> MY_HOMES = (c, b) -> {
        ServerPlayer p = c.getSource().getPlayer();
        return SharedSuggestionProvider.suggest(p == null ? java.util.List.of() : Homes.of(p.getUUID()).keySet(), b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("homes")) return;

        if (Features.on("homes", "home")) {
            d.register(Commands.literal("home").requires(HomeCommand::canUse)
                    .executes(c -> home(c, null))
                    .then(Commands.argument("name", StringArgumentType.greedyString()).suggests(MY_HOMES)
                            .executes(c -> home(c, StringArgumentType.getString(c, "name")))));
        }
        if (Features.on("homes", "sethome")) {
            d.register(Commands.literal("sethome").requires(HomeCommand::canUse)
                    .executes(c -> sethome(c, "home"))
                    .then(Commands.argument("name", StringArgumentType.word())
                            .executes(c -> sethome(c, StringArgumentType.getString(c, "name")))));
        }
        if (Features.on("homes", "delhome")) {
            d.register(Commands.literal("delhome").requires(HomeCommand::canUse)
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(MY_HOMES)
                            .executes(HomeCommand::delhome)));
        }
        if (Features.on("homes", "homes_list")) {
            var homes = Commands.literal("homes").requires(HomeCommand::canUse).executes(c -> list(c, null));
            if (Features.on("homes", "visit_others")) {
                homes.then(Commands.argument("player", StringArgumentType.word())
                        .requires(s -> Perm.has(s, Perm.HOME_OTHERS))
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
                        .executes(c -> list(c, StringArgumentType.getString(c, "player"))));
            }
            d.register(homes);
        }
    }

    private static boolean canUse(CommandSourceStack s) {
        return s.getPlayer() != null && Perm.has(s, Perm.HOME_USE);
    }

    // ---------- /home ----------

    private static int home(CommandContext<CommandSourceStack> c, String arg) {
        ServerPlayer p = c.getSource().getPlayer();
        String name = arg == null ? null : arg.trim().toLowerCase(Locale.ROOT);

        // Someone else's home: player:name
        if (name != null && name.contains(":")) {
            if (!Features.on("homes", "visit_others") || !Perm.has(p, Perm.HOME_OTHERS)) {
                return fail(c, "You can't visit other players' homes.");
            }
            String[] parts = name.split(":", 2);
            Optional<GameProfile> who = RankCommand.profile(c.getSource().getServer(), parts[0]);
            if (who.isEmpty()) return fail(c, "No Minecraft account with that name.");
            Spot s = Homes.of(who.get().getId()).get(parts[1]);
            if (s == null) return fail(c, who.get().getName() + " has no home called " + parts[1] + ".");
            Teleports.go(p, s, who.get().getName() + "'s home " + parts[1]);
            return 1;
        }

        TreeMap<String, Spot> homes = Homes.of(p.getUUID());
        if (homes.isEmpty()) return fail(c, "You have no homes yet. Use /sethome to set one.");
        if (name == null) name = homes.size() == 1 ? homes.firstKey() : "home";
        Spot s = homes.get(name);
        if (s == null) return fail(c, "You have no home called " + name + ". Your homes: " + String.join(", ", homes.keySet()));
        if (Homes.blockedWorld(p, s.dim())) return fail(c, "Homes don't work in " + Teleports.worldName(s.dim()) + ". Delete it with /delhome " + name + ".");
        Teleports.go(p, s, "home " + name);
        return 1;
    }

    // ---------- /sethome ----------

    private static int sethome(CommandContext<CommandSourceStack> c, String raw) {
        ServerPlayer p = c.getSource().getPlayer();
        String name = raw.toLowerCase(Locale.ROOT);
        if (!Homes.VALID_NAME.matcher(name).matches()) return fail(c, "Home names use a-z, 0-9, _ and - (max 16).");
        String here = p.level().dimension().location().toString();
        if (Homes.blockedWorld(p, here)) return fail(c, "You can't set a home in " + Teleports.worldName(here) + ".");
        TreeMap<String, Spot> homes = Homes.of(p.getUUID());
        int limit = Homes.limit(p);
        if (!homes.containsKey(name) && homes.size() >= limit) {
            return fail(c, "You have " + homes.size() + "/" + limit + " homes. Delete one with /delhome first.");
        }
        boolean moved = homes.put(name, Spot.of(p)) != null;
        Homes.save(p.getUUID(), homes);
        String count = limit == Homes.UNLIMITED ? homes.size() + "" : homes.size() + "/" + limit;
        return ok(c, "&aHome " + name + (moved ? " moved here." : " set.") + " &7(" + count + ")");
    }

    // ---------- /delhome ----------

    private static int delhome(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        String name = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
        TreeMap<String, Spot> homes = Homes.of(p.getUUID());
        if (homes.remove(name) == null) return fail(c, "You have no home called " + name + ".");
        Homes.save(p.getUUID(), homes);
        return ok(c, "&aHome " + name + " deleted.");
    }

    // ---------- /homes ----------

    private static int list(CommandContext<CommandSourceStack> c, String player) {
        ServerPlayer me = c.getSource().getPlayer();
        String who = me.getGameProfile().getName();
        TreeMap<String, Spot> homes;
        String limitText;
        if (player == null) {
            homes = Homes.of(me.getUUID());
            int limit = Homes.limit(me);
            limitText = limit == Homes.UNLIMITED ? "no limit" : homes.size() + "/" + limit;
        } else {
            Optional<GameProfile> prof = RankCommand.profile(c.getSource().getServer(), player);
            if (prof.isEmpty()) return fail(c, "No Minecraft account with that name.");
            homes = Homes.of(prof.get().getId());
            who = prof.get().getName();
            limitText = homes.size() + "";
        }
        if (homes.isEmpty()) return ok(c, "&7" + (player == null ? "You have" : who + " has") + " no homes.");
        StringBuilder sb = new StringBuilder("&6" + (player == null ? "Your homes" : who + "'s homes") + " &7(" + limitText + "):");
        for (Map.Entry<String, Spot> e : homes.entrySet()) {
            Spot s = e.getValue();
            sb.append("\n&7- &f").append(e.getKey()).append(" &8").append(Teleports.worldName(s.dim()))
                    .append(" ").append((int) Math.floor(s.x())).append(", ").append((int) Math.floor(s.y()))
                    .append(", ").append((int) Math.floor(s.z()));
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
