package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.holograms.Hologram;
import com.tac5studios.elementsnexus.holograms.Holograms;
import com.tac5studios.elementsnexus.moderation.StaffLog;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * /holo list | create <id> <text> | delete <id> | movehere <id> | tp <id>
 * /holo line <id> add <text> | set <n> <text> | insert <n> <text> | remove <n>
 * /holo animate <id> <line> <ticks> <frame | frame | ...>   (ticks 0 = stop)
 * /holo scale | spacing | width | background | shadow | facing | seethrough <id> <value>
 * /holo face <id>              - stop turning and face where you are standing
 * /holo rotate <id> <degrees>  - stop turning and face this direction
 */
public final class HoloCommand {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final SuggestionProvider<CommandSourceStack> IDS =
            (c, b) -> SharedSuggestionProvider.suggest(Holograms.ids(), b);

    private HoloCommand() {}

    private static RequiredArgumentBuilder<CommandSourceStack, String> id() {
        return Commands.argument("id", StringArgumentType.word()).suggests(IDS);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (!Features.on("holograms")) return;
        d.register(Commands.literal("holo")
                .requires(s -> Perm.has(s, Perm.HOLO))
                .then(Commands.literal("list").executes(HoloCommand::list))
                .then(Commands.literal("create").requires(s -> s.getPlayer() != null)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(HoloCommand::create))))
                .then(Commands.literal("delete").then(id().executes(HoloCommand::delete)))
                .then(Commands.literal("movehere").requires(s -> s.getPlayer() != null).then(id().executes(HoloCommand::moveHere)))
                .then(Commands.literal("tp").requires(s -> s.getPlayer() != null).then(id().executes(HoloCommand::tp)))
                .then(Commands.literal("line").then(id()
                        .then(Commands.literal("add").then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(c -> edit(c, (h, x) -> { h.lines.add(line(x.text)); return "Line added."; }))))
                        .then(Commands.literal("set").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(c -> edit(c, (h, x) -> {
                                            if (x.n > h.lines.size()) return null;
                                            h.lines.get(x.n - 1).text = x.text;
                                            return "Line " + x.n + " changed.";
                                        })))))
                        .then(Commands.literal("insert").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(c -> edit(c, (h, x) -> {
                                            if (x.n > h.lines.size() + 1) return null;
                                            h.lines.add(x.n - 1, line(x.text));
                                            return "Line inserted at " + x.n + ".";
                                        })))))
                        .then(Commands.literal("remove").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(c -> edit(c, (h, x) -> {
                                    if (x.n > h.lines.size()) return null;
                                    h.lines.remove(x.n - 1);
                                    return "Line " + x.n + " removed.";
                                }))))))
                .then(Commands.literal("animate").then(id()
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 1200))
                                        .executes(c -> animate(c, ""))
                                        .then(Commands.argument("frames", StringArgumentType.greedyString())
                                                .executes(c -> animate(c, StringArgumentType.getString(c, "frames"))))))))
                .then(Commands.literal("scale").then(id().then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 10f))
                        .executes(c -> edit(c, (h, x) -> { h.scale = FloatArgumentType.getFloat(c, "value"); return "Scale set."; })))))
                .then(Commands.literal("spacing").then(id().then(Commands.argument("value", FloatArgumentType.floatArg(0.05f, 3f))
                        .executes(c -> edit(c, (h, x) -> { h.spacing = FloatArgumentType.getFloat(c, "value"); return "Line spacing set."; })))))
                .then(Commands.literal("width").then(id().then(Commands.argument("value", IntegerArgumentType.integer(10, 2000))
                        .executes(c -> edit(c, (h, x) -> { h.width = IntegerArgumentType.getInteger(c, "value"); return "Width set."; })))))
                .then(Commands.literal("shadow").then(id().then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(c -> edit(c, (h, x) -> { h.shadow = BoolArgumentType.getBool(c, "value"); return "Shadow set."; })))))
                .then(Commands.literal("seethrough").then(id().then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(c -> edit(c, (h, x) -> { h.seeThrough = BoolArgumentType.getBool(c, "value"); return "See-through set."; })))))
                .then(Commands.literal("facing").then(id().then(Commands.argument("value", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("center", "fixed", "vertical", "horizontal"), b))
                        .executes(c -> edit(c, (h, x) -> {
                            String v = StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
                            if (!List.of("center", "fixed", "vertical", "horizontal").contains(v)) return null;
                            h.facing = v;
                            return "Facing set.";
                        })))))
                .then(Commands.literal("face").requires(s -> s.getPlayer() != null).then(id()
                        .executes(c -> edit(c, (h, x) -> {
                            ServerPlayer p = c.getSource().getPlayer();
                            // Face the player: the opposite of the way they are looking, snapped to 15 degrees.
                            float yaw = Math.round((p.getYRot() + 180f) / 15f) * 15f;
                            h.facing = "fixed";
                            h.yaw = net.minecraft.util.Mth.wrapDegrees(yaw);
                            return "Now fixed, facing you (" + (int) h.yaw + "°).";
                        }))))
                .then(Commands.literal("rotate").then(id().then(Commands.argument("degrees", FloatArgumentType.floatArg(-360f, 360f))
                        .executes(c -> edit(c, (h, x) -> {
                            h.facing = "fixed";
                            h.yaw = net.minecraft.util.Mth.wrapDegrees(FloatArgumentType.getFloat(c, "degrees"));
                            return "Now fixed at " + (int) h.yaw + "°.";
                        })))))
                .then(Commands.literal("background").then(id().then(Commands.argument("value", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("none", "#80000000"), b))
                        .executes(c -> edit(c, (h, x) -> {
                            String v = StringArgumentType.getString(c, "value");
                            if (v.equalsIgnoreCase("none")) h.background = 0;
                            else {
                                try {
                                    h.background = (int) Long.parseLong(v.replace("#", ""), 16);
                                } catch (NumberFormatException e) {
                                    return null;
                                }
                            }
                            return "Background set.";
                        }))))));
    }

    /** What an edit needs: the line number and text, if the command has them. */
    private record Args(int n, String text) {}

    private static Hologram.Line line(String text) {
        Hologram.Line l = new Hologram.Line();
        l.text = text;
        return l;
    }

    private static int edit(CommandContext<CommandSourceStack> c, BiFunction<Hologram, Args, String> change) {
        String id = StringArgumentType.getString(c, "id").toLowerCase(Locale.ROOT);
        Hologram h = Holograms.get(id);
        if (h == null) return fail(c, "There is no hologram called " + id + ".");
        int n = 0;
        String text = "";
        try { n = IntegerArgumentType.getInteger(c, "n"); } catch (IllegalArgumentException ignored) { }
        try { text = StringArgumentType.getString(c, "text"); } catch (IllegalArgumentException ignored) { }
        String msg = change.apply(h, new Args(n, text));
        if (msg == null) return fail(c, "That doesn't fit this hologram (it has " + h.lines.size() + " lines).");
        Holograms.save(c.getSource().getServer(), id, h);
        StaffLog.add(c.getSource(), "holo edit", null, null, id, null);
        return ok(c, "&a" + msg);
    }

    private static int animate(CommandContext<CommandSourceStack> c, String frames) {
        int ticks = IntegerArgumentType.getInteger(c, "ticks");
        return edit(c, (h, x) -> {
            if (x.n > h.lines.size()) return null;
            Hologram.Line l = h.lines.get(x.n - 1);
            if (ticks == 0 || frames.isBlank()) {
                l.frames = new ArrayList<>();
                return "Animation stopped on line " + x.n + ".";
            }
            l.frames = new ArrayList<>(Arrays.stream(frames.split("\\s*\\|\\s*")).filter(s -> !s.isEmpty()).toList());
            l.interval = ticks;
            if (!l.frames.isEmpty()) l.text = l.frames.get(0);
            return "Line " + x.n + " now changes every " + ticks + " ticks (" + l.frames.size() + " frames).";
        });
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<String> ids = Holograms.ids();
        if (ids.isEmpty()) return ok(c, "&7No holograms yet. Make one with /holo create <id> <text>.");
        StringBuilder sb = new StringBuilder("&6Holograms:");
        for (String id : ids) {
            Hologram h = Holograms.get(id);
            sb.append("\n&e").append(id).append(" &7- ").append(h.world).append(" ")
                    .append((int) h.x).append(" ").append((int) h.y).append(" ").append((int) h.z)
                    .append(" (").append(h.lines.size()).append(" lines)");
        }
        String msg = sb.toString();
        c.getSource().sendSuccess(() -> Text.color(msg), false);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id").toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) return fail(c, "Use letters, numbers, - and _ for the id.");
        if (Holograms.get(id) != null) return fail(c, "There is already a hologram called " + id + ".");
        ServerPlayer p = c.getSource().getPlayer();
        Hologram h = new Hologram();
        h.world = p.level().dimension().location().toString();
        h.x = p.getX();
        h.y = p.getY() + 1.5;
        h.z = p.getZ();
        h.lines.add(line(StringArgumentType.getString(c, "text")));
        Holograms.save(c.getSource().getServer(), id, h);
        StaffLog.add(c.getSource(), "holo create", null, null, id, null);
        return ok(c, "&aHologram " + id + " made. Add lines with /holo line " + id + " add <text>.");
    }

    private static int delete(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id").toLowerCase(Locale.ROOT);
        if (Holograms.get(id) == null) return fail(c, "There is no hologram called " + id + ".");
        Holograms.delete(id);
        StaffLog.add(c.getSource(), "holo delete", null, null, id, null);
        return ok(c, "&aHologram " + id + " deleted.");
    }

    private static int moveHere(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        return edit(c, (h, x) -> {
            h.world = p.level().dimension().location().toString();
            h.x = p.getX();
            h.y = p.getY() + 1.5;
            h.z = p.getZ();
            return "Moved to you.";
        });
    }

    private static int tp(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id").toLowerCase(Locale.ROOT);
        Hologram h = Holograms.get(id);
        if (h == null) return fail(c, "There is no hologram called " + id + ".");
        com.tac5studios.elementsnexus.teleport.Teleports.now(c.getSource().getPlayer(),
                new com.tac5studios.elementsnexus.teleport.Teleports.Spot(h.world, h.x, h.y - 1.5, h.z + 2, 180, 0), "hologram " + id);
        return 1;
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
