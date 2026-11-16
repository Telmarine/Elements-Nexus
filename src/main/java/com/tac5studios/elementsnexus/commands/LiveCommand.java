package com.tac5studios.elementsnexus.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.discord.Live;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * /live                                     - a creator announces their stream
 * /live link                                - show your stream links
 * /live link add|remove <link>              - change your own links
 * /live creator add <player> <link>         - staff: make a creator file (or add a link to one)
 * /live creator remove <player>             - staff: delete the creator file
 * /live creator list                        - staff
 * Creator files live in config/elements_nexus/creators/ (one per creator) and can be edited by hand.
 */
public final class LiveCommand {

    private LiveCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Live.on()) return;
        d.register(Commands.literal("live")
                .requires(s -> s.getPlayer() == null || Perm.has(s, Perm.LIVE_USE))
                .executes(LiveCommand::announce)
                .then(Commands.literal("link")
                        .executes(LiveCommand::showLinks)
                        .then(Commands.literal("add").then(Commands.argument("link", StringArgumentType.greedyString())
                                .executes(c -> ownLink(c, true))))
                        .then(Commands.literal("remove").then(Commands.argument("link", StringArgumentType.greedyString())
                                .executes(c -> ownLink(c, false)))))
                .then(Commands.literal("creator").requires(s -> Perm.has(s, Perm.LIVE_ADMIN))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("link", StringArgumentType.greedyString()).executes(LiveCommand::add))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(LiveCommand::remove)))
                        .then(Commands.literal("list").executes(LiveCommand::list))));
    }

    private static Live.Creator mine(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        Live.Creator creator = Live.creator(p.getGameProfile().getName());
        if (creator == null) c.getSource().sendFailure(Component.literal("Only registered creators can use /live. Ask staff to add you."));
        return creator;
    }

    private static int announce(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Live.Creator creator = mine(c, p);
        if (creator == null) return 0;
        if (!creator.enabled() || creator.links().isEmpty()) {
            c.getSource().sendFailure(Component.literal(creator.enabled()
                    ? "You have no stream link yet. Add one with /live link add <link>." : "Your go-live announcements are turned off."));
            return 0;
        }
        long left = Live.cooldownLeft(creator.player());
        if (left > 0 || !Live.fromCommand(c.getSource().getServer(), creator)) {
            c.getSource().sendFailure(Component.literal("Your stream was already announced. Try again in " + Math.max(left, 1) + " min."));
            return 0;
        }
        return 1;
    }

    private static int showLinks(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Live.Creator creator = mine(c, c.getSource().getPlayerOrException());
        if (creator == null) return 0;
        String links = creator.links().isEmpty() ? "&8none" : String.join("&7, &f", creator.links());
        c.getSource().sendSuccess(() -> Text.color("&7Your stream links: &f" + links), false);
        return 1;
    }

    private static int ownLink(CommandContext<CommandSourceStack> c, boolean add) throws CommandSyntaxException {
        Live.Creator creator = mine(c, c.getSource().getPlayerOrException());
        if (creator == null) return 0;
        String link = StringArgumentType.getString(c, "link");
        if (add && !Live.validLink(link)) {
            c.getSource().sendFailure(Component.literal("That doesn't look like a link, e.g. twitch.tv/yourname"));
            return 0;
        }
        List<String> links = new ArrayList<>(creator.links());
        String clean = Live.cleanLink(link);
        if (add) links.add(clean);
        else if (!links.remove(clean)) {
            c.getSource().sendFailure(Component.literal("That link isn't on your list."));
            return 0;
        }
        if (writeFailed(c, creator.player(), links, creator.enabled())) return 0;
        c.getSource().sendSuccess(() -> Text.color(add ? "&7Added &f" + clean : "&7Removed &f" + clean), false);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String link = StringArgumentType.getString(c, "link");
        if (!Live.validLink(link)) {
            c.getSource().sendFailure(Component.literal("That doesn't look like a link, e.g. twitch.tv/name"));
            return 0;
        }
        int n = 0;
        for (GameProfile gp : GameProfileArgument.getGameProfiles(c, "player")) {
            Live.Creator old = Live.creator(gp.getName());
            List<String> links = new ArrayList<>(old == null ? List.of() : old.links());
            links.add(link);
            if (writeFailed(c, old == null ? gp.getName() : old.player(), links, old == null || old.enabled())) continue;
            n++;
            c.getSource().sendSuccess(() -> Text.color((old == null ? "&7Added creator &f" : "&7Added a link for &f")
                    + gp.getName() + " &7(" + Live.cleanLink(link) + ")"), true);
        }
        return n;
    }

    private static int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int n = 0;
        for (GameProfile gp : GameProfileArgument.getGameProfiles(c, "player")) {
            try {
                String removed = Live.delete(gp.getName());
                if (removed != null) {
                    n++;
                    c.getSource().sendSuccess(() -> Text.color("&7Removed creator &f" + removed), true);
                } else {
                    c.getSource().sendFailure(Component.literal(gp.getName() + " isn't a registered creator."));
                }
            } catch (Exception e) {
                ElementsNexus.LOGGER.error("[Nexus] Could not delete the creator file for {}", gp.getName(), e);
                c.getSource().sendFailure(Component.literal("Could not delete the file. See the server log."));
            }
        }
        return n;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<Live.Creator> all = Live.creators();
        if (all.isEmpty()) {
            c.getSource().sendSuccess(() -> Text.color("&7No creators registered."), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Text.color("&7Creators (" + all.size() + "):"), false);
        for (Live.Creator cr : all) {
            String links = cr.links().isEmpty() ? "&8no links" : String.join(", ", cr.links());
            c.getSource().sendSuccess(() -> Text.color("&f" + cr.player() + (cr.enabled() ? "" : " &c(off)") + " &7- " + links), false);
        }
        return all.size();
    }

    private static boolean writeFailed(CommandContext<CommandSourceStack> c, String player, List<String> links, boolean enabled) {
        try {
            Live.save(player, links, enabled);
            return false;
        } catch (Exception e) {
            ElementsNexus.LOGGER.error("[Nexus] Could not write the creator file for {}", player, e);
            c.getSource().sendFailure(Component.literal("Could not write the creator file. See the server log."));
            return true;
        }
    }
}
