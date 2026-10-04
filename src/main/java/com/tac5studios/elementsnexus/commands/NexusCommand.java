package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.announce.Announcements;
import com.tac5studios.elementsnexus.chat.Chat;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.help.Help;
import com.tac5studios.elementsnexus.messages.Messages;
import com.tac5studios.elementsnexus.moderation.StaffLog;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.rules.Rules;
import com.tac5studios.elementsnexus.tablist.Tablist;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.fml.ModList;

/**
 * /nexus            - version
 * /nexus reload     - reload the hand-written .toml files
 */
public final class NexusCommand {

    private NexusCommand() {}

    private static int migrateList(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        StringBuilder sb = new StringBuilder("&6Import data from:");
        for (var s : com.tac5studios.elementsnexus.migrate.Migrations.enabled()) {
            boolean found = s.found(c.getSource().getServer());
            sb.append("\n &e").append(s.id()).append(" &7- ").append(s.label())
                    .append(found ? " &a(data found)" : " &8(no data found)");
        }
        sb.append("\n&7Use &f/nexus migrate <name> &7to see what would be imported.");
        String msg = sb.toString();
        c.getSource().sendSuccess(() -> Text.color(msg), false);
        return 1;
    }

    private static int migrate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, boolean apply) {
        String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "source").toLowerCase(java.util.Locale.ROOT);
        var src = com.tac5studios.elementsnexus.migrate.Migrations.get(id);
        var server = c.getSource().getServer();
        if (src == null) {
            c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Unknown source. Use /nexus migrate to see the list."));
            return 0;
        }
        if (!src.found(server)) {
            c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("No " + src.label() + " data was found on this server."));
            return 0;
        }
        var im = new com.tac5studios.elementsnexus.migrate.Importer(server, apply);
        try {
            src.run(im);
        } catch (Exception e) {
            ElementsNexus.LOGGER.error("[Nexus] Import from {} failed.", src.label(), e);
            c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Import failed: " + e + ". See the console."));
            return 0;
        }
        im.finish();

        StringBuilder sb = new StringBuilder(apply ? "&aImported from " + src.label() + ":" : "&6Preview of " + src.label() + " import &7(nothing changed yet)&6:");
        if (im.counts().isEmpty()) sb.append("\n &7Nothing to import.");
        for (var e : im.counts().entrySet()) {
            int[] n = e.getValue();
            sb.append("\n &e").append(e.getKey()).append("&7: &f").append(n[0]).append(apply ? " added" : " new");
            if (n[1] > 0) sb.append("&7, ").append(n[1]).append(" already in Nexus (kept)");
        }
        int shown = 0;
        for (String note : im.notes()) {
            ElementsNexus.LOGGER.info("[Nexus] Import {}: {}", src.id(), note);
            if (shown++ < 8) sb.append("\n &8- &7").append(note);
        }
        if (im.notes().size() > 8) sb.append("\n &8- &7").append(im.notes().size() - 8).append(" more notes in the console.");
        if (!apply) {
            sb.append("\n&7Nexus data is never overwritten. Run &f/nexus migrate ").append(src.id()).append(" confirm &7to import.");
        } else {
            sb.append("\n&7You can remove ").append(src.label()).append(" once you're happy with the result.");
            StaffLog.add(c.getSource(), "migrate", null, null, src.id(), null);
        }
        String msg = sb.toString();
        c.getSource().sendSuccess(() -> Text.color(msg), apply);
        return 1;
    }

    private static int convert(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        String to = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "to").toLowerCase(java.util.Locale.ROOT);
        if (!java.util.List.of("json", "yaml", "sqlite", "mysql").contains(to)) {
            c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Use json, yaml, sqlite or mysql."));
            return 0;
        }
        String current = com.tac5studios.elementsnexus.storage.Storage.get().backend().name();
        if (to.equals(current)) {
            c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Nexus is already using " + to + "."));
            return 0;
        }
        if (com.tac5studios.elementsnexus.storage.StorageConvert.running()) {
            c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("A convert is already running."));
            return 0;
        }
        CommandSourceStack src = c.getSource();
        net.minecraft.server.MinecraftServer server = src.getServer();
        src.sendSuccess(() -> Text.color("&7Copying all data from &f" + current + " &7to &f" + to + "&7..."), true);
        StaffLog.add(src, "storage convert", null, null, current + " -> " + to, null);
        com.tac5studios.elementsnexus.storage.StorageConvert.start(to, error -> server.execute(() -> {
            if (error == null) {
                src.sendSuccess(() -> Text.color("&aDone. All data is now also in &f" + to + "&a.\n"
                        + "&7To use it: set &fbackend = \"" + to + "\" &7in storage.toml, then restart."), true);
            } else {
                src.sendFailure(net.minecraft.network.chat.Component.literal("Convert failed: " + error + " (nothing was changed). See the console."));
            }
        }));
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("admin")) return;
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("nexus")
                .requires(s -> Perm.has(s, Perm.ADMIN))
                .executes(c -> {
                    String v = ModList.get().getModContainerById(ElementsNexus.MOD_ID)
                            .map(m -> m.getModInfo().getVersion().toString()).orElse("?");
                    c.getSource().sendSuccess(() -> Text.color("&6&lElements: Nexus &7v" + v), false);
                    return 1;
                });

        if (Features.on("admin", "reload")) {
            root.then(Commands.literal("reload").executes(c -> {
                long start = System.currentTimeMillis();
                Chat.load();          // chat.toml + chat_filter.toml
                Rules.load();         // rules.toml
                Help.load();          // help.toml
                Announcements.load(); // announcements.toml
                Messages.load();      // messages.toml
                Tablist.refresh(c.getSource().getServer());
                long ms = System.currentTimeMillis() - start;
                StaffLog.add(c.getSource(), "nexus reload", null, null, null, null);
                c.getSource().sendSuccess(() -> Text.color(
                        "&aReloaded &7(" + ms + " ms)&a.\n"
                                + "&7Other settings files update on their own when saved.\n"
                                + "&7Turning features or commands on/off needs a restart."), true);
                return 1;
            }));
        }
        if (Features.on("migration")) {
            root.then(Commands.literal("migrate")
                    .executes(NexusCommand::migrateList)
                    .then(Commands.argument("source", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                    com.tac5studios.elementsnexus.migrate.Migrations.enabled().stream()
                                            .map(com.tac5studios.elementsnexus.migrate.Migrations.Source::id).toList(), b))
                            .executes(c -> migrate(c, false))
                            .then(Commands.literal("confirm").executes(c -> migrate(c, true)))));
        }
        if (Features.on("admin", "storage_convert")) {
            root.then(Commands.literal("storage")
                    .then(Commands.literal("convert")
                            .then(Commands.argument("to", com.mojang.brigadier.arguments.StringArgumentType.word())
                                    .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                            java.util.List.of("json", "yaml", "sqlite", "mysql"), b))
                                    .executes(NexusCommand::convert))));
        }
        d.register(root);
    }
}
