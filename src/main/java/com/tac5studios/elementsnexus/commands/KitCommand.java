package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.kits.Kit;
import com.tac5studios.elementsnexus.kits.Kits;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/**
 * /kit [name]                               claim a kit (no name = list)
 * /kit list
 * /kit create <name> <cooldown> [locked]    from your inventory. cooldown: once, none, 30s, 10m, 2h, 1d
 * /kit delete <name>
 * /kit give <name> <player>                 give it now (ignores cooldown). Works from console and NPCs.
 * /kit reset <name> <player>                let a player claim it again
 */
public final class KitCommand {

    private KitCommand() {}

    private static final SuggestionProvider<CommandSourceStack> KITS =
            (c, b) -> SharedSuggestionProvider.suggest(Kits.names(), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("kits")) return;

        LiteralArgumentBuilder<CommandSourceStack> kit = Commands.literal("kit")
                .requires(s -> Perm.has(s, Perm.KIT_USE) || Perm.has(s, Perm.KIT_ADMIN));

        if (Features.on("kits", "kit")) {
            kit.executes(KitCommand::list)
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(KITS)
                            .requires(s -> s.getPlayer() != null && Perm.has(s, Perm.KIT_USE))
                            .executes(KitCommand::claim));
        }
        if (Features.on("kits", "kit_list")) {
            kit.then(Commands.literal("list").executes(KitCommand::list));
        }
        if (Features.on("kits", "kit_create")) {
            kit.then(Commands.literal("create").requires(s -> s.getPlayer() != null && Perm.has(s, Perm.KIT_ADMIN))
                    .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("cooldown", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("once", "none", "1h", "1d", "7d"), b))
                                    .executes(c -> create(c, false))
                                    .then(Commands.literal("locked").executes(c -> create(c, true))))));
        }
        if (Features.on("kits", "kit_delete")) {
            kit.then(Commands.literal("delete").requires(s -> Perm.has(s, Perm.KIT_ADMIN))
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(KITS)
                            .executes(KitCommand::delete)));
        }
        if (Features.on("kits", "kit_give")) {
            kit.then(Commands.literal("give").requires(s -> Perm.has(s, Perm.KIT_ADMIN))
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(KITS)
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(KitCommand::give))));
            kit.then(Commands.literal("reset").requires(s -> Perm.has(s, Perm.KIT_ADMIN))
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(KITS)
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(KitCommand::reset))));
        }
        d.register(kit);
    }

    // ---------- /kit <name> ----------

    private static int claim(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        String name = arg(c);
        Kit kit = Kits.get(name);
        if (kit == null) return fail(c, "There is no kit called " + name + ".");
        if (!Kits.allowed(p, name, kit)) return fail(c, "You can't use the " + name + " kit.");
        long left = Kits.waitLeft(p.getUUID(), name, kit);
        if (left < 0) return fail(c, "You already claimed the " + name + " kit. It can only be claimed once.");
        if (left > 0) return fail(c, "You can claim the " + name + " kit again in " + Kits.timeText(left) + ".");
        Kits.markClaimed(p.getUUID(), name); // mark first, so a double-click can't claim twice
        int n = Kits.give(p, kit);
        return ok(c, "&aYou got the " + name + " kit &7(" + n + " items).");
    }

    // ---------- /kit list ----------

    private static int list(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        List<String> names = Kits.names();
        if (names.isEmpty()) return ok(c, "&7There are no kits yet.");
        StringBuilder sb = new StringBuilder("&6Kits:");
        boolean any = false;
        for (String n : names) {
            Kit k = Kits.get(n);
            if (p != null && !Kits.allowed(p, n, k)) continue;
            any = true;
            sb.append("\n&7- &f").append(n).append(" &8(").append(Kits.timeText(k.cooldown)).append(k.locked ? ", locked" : "").append(")");
            if (p != null) {
                long left = Kits.waitLeft(p.getUUID(), n, k);
                sb.append(left == 0 ? " &aready" : left < 0 ? " &7claimed" : " &e" + Kits.timeText(left));
            }
        }
        if (!any) return ok(c, "&7There are no kits you can use.");
        return ok(c, sb.toString());
    }

    // ---------- /kit create ----------

    private static int create(CommandContext<CommandSourceStack> c, boolean locked) {
        ServerPlayer p = c.getSource().getPlayer();
        String name = arg(c);
        if (!Kits.VALID_NAME.matcher(name).matches()) return fail(c, "Kit names use a-z, 0-9, _ and - (max 32).");
        if (java.util.Set.of("list", "create", "delete", "give", "reset").contains(name))
            return fail(c, "\"" + name + "\" is a /kit command word. Pick another name.");
        Long cd = Kits.parseCooldown(StringArgumentType.getString(c, "cooldown"));
        if (cd == null) return fail(c, "Cooldown must be once, none, or a time like 30s, 10m, 2h, 1d.");
        Kit kit = new Kit();
        kit.cooldown = cd;
        kit.locked = locked;
        kit.items = Kits.fromInventory(p);
        if (kit.items.isEmpty()) return fail(c, "Your inventory is empty. Put the kit items in your inventory first.");
        boolean replaced = Kits.get(name) != null;
        Kits.save(name, kit);
        com.tac5studios.elementsnexus.moderation.StaffLog.add(c.getSource(), replaced ? "kit update" : "kit create", null, name, null, null);
        return ok(c, "&aKit " + name + (replaced ? " updated" : " created") + " with " + kit.items.size()
                + " items &7(" + Kits.timeText(cd) + (locked ? ", needs nexus.kit." + name : ", everyone") + ").");
    }

    // ---------- /kit delete ----------

    private static int delete(CommandContext<CommandSourceStack> c) {
        String name = arg(c);
        if (Kits.get(name) == null) return fail(c, "There is no kit called " + name + ".");
        Kits.delete(name);
        com.tac5studios.elementsnexus.moderation.StaffLog.add(c.getSource(), "kit delete", null, name, null, null);
        return ok(c, "&aKit " + name + " deleted.");
    }

    // ---------- /kit give / reset ----------

    private static int give(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String name = arg(c);
        Kit kit = Kits.get(name);
        if (kit == null) return fail(c, "There is no kit called " + name + ".");
        ServerPlayer to = EntityArgument.getPlayer(c, "player");
        int n = Kits.give(to, kit);
        to.sendSystemMessage(Text.color("&aYou got the " + name + " kit."));
        return ok(c, "&aGave the " + name + " kit to " + to.getGameProfile().getName() + " &7(" + n + " items).");
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String name = arg(c);
        ServerPlayer to = EntityArgument.getPlayer(c, "player");
        Kits.clearClaim(to.getUUID(), name);
        return ok(c, "&a" + to.getGameProfile().getName() + " can claim the " + name + " kit again.");
    }

    private static String arg(CommandContext<CommandSourceStack> c) {
        return StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
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
