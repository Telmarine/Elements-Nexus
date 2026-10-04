package com.tac5studios.elementsnexus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.moderation.StaffLog;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.teleport.Teleports;
import com.tac5studios.elementsnexus.teleport.Teleports.Spot;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Go/bring: /tpo <player> (go to them)   /tpi <player> (bring them to you)
 *           Staff teleport right away. Players send a request with clickable [Accept] [Deny].
 *           /tpaccept   /tpdeny
 * Staff:   /tppos <x y z>
 * Spawn:   /spawn           /spawn set
 * Warps:   /warp <name>     /warps    /warp set <name>    /warp delete <name>
 * Back:    /back
 */
public final class TeleportCommands {

    private static final Pattern WARP_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final long TPA_TIMEOUT_MS = 60_000;

    /** A teleport request: 'mover' goes to 'other'. Stored under the player who must accept. */
    private record Request(UUID from, boolean here, long time) {}

    private static final Map<UUID, Request> REQUESTS = new HashMap<>();

    private static final SuggestionProvider<CommandSourceStack> WARPS =
            (c, b) -> SharedSuggestionProvider.suggest(Storage.get().keys("warps"), b);

    private TeleportCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // ---- /tpo and /tpi (staff: instant, players: request) ----
        boolean staffTp = Features.on("teleport") && Features.on("teleport", "tp_player");
        boolean requests = Features.on("tpa");
        if (staffTp || (requests && Features.on("tpa", "tpo"))) {
            d.register(Commands.literal("tpo").requires(s -> s.getPlayer() != null && canTp(s, "tpo"))
                    .then(Commands.argument("player", EntityArgument.player()).executes(c -> tp(c, false))));
        }
        if (staffTp || (requests && Features.on("tpa", "tpi"))) {
            d.register(Commands.literal("tpi").requires(s -> s.getPlayer() != null && canTp(s, "tpi"))
                    .then(Commands.argument("player", EntityArgument.player()).executes(c -> tp(c, true))));
        }
        if (requests && Features.on("tpa", "accept")) {
            d.register(Commands.literal("tpaccept").requires(s -> s.getPlayer() != null).executes(TeleportCommands::tpaAccept));
        }
        if (requests && Features.on("tpa", "deny")) {
            d.register(Commands.literal("tpdeny").requires(s -> s.getPlayer() != null).executes(TeleportCommands::tpaDeny));
        }
        if (Features.on("teleport") && Features.on("teleport", "tp_coords")) {
            d.register(Commands.literal("tppos").requires(s -> s.getPlayer() != null && Perm.has(s, Perm.TP))
                    .then(Commands.argument("pos", Vec3Argument.vec3()).executes(TeleportCommands::tpPos)));
        }

        // ---- spawn ----
        if (Features.on("spawn")) {
            var spawn = Commands.literal("spawn").requires(s -> s.getPlayer() != null
                    && (Perm.has(s, Perm.SPAWN_USE) || Perm.has(s, Perm.SPAWN_SET)));
            if (Features.on("spawn", "spawn")) {
                spawn.executes(TeleportCommands::spawn);
            }
            if (Features.on("spawn", "spawn_set")) {
                spawn.then(Commands.literal("set").requires(s -> Perm.has(s, Perm.SPAWN_SET)).executes(TeleportCommands::spawnSet));
            }
            d.register(spawn);
        }

        // ---- warps ----
        if (Features.on("warps")) {
            var warp = Commands.literal("warp").requires(s -> s.getPlayer() != null
                    && (Perm.has(s, Perm.WARP_USE) || Perm.has(s, Perm.WARP_ADMIN)));
            if (Features.on("warps", "warp")) {
                warp.then(Commands.argument("name", StringArgumentType.word()).suggests(WARPS)
                        .requires(s -> Perm.has(s, Perm.WARP_USE)).executes(TeleportCommands::warp));
            }
            if (Features.on("warps", "warp_set")) {
                warp.then(Commands.literal("set").requires(s -> Perm.has(s, Perm.WARP_ADMIN))
                        .then(Commands.argument("name", StringArgumentType.word()).executes(TeleportCommands::warpSet)));
            }
            if (Features.on("warps", "warp_delete")) {
                warp.then(Commands.literal("delete").requires(s -> Perm.has(s, Perm.WARP_ADMIN))
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(WARPS).executes(TeleportCommands::warpDelete)));
            }
            d.register(warp);
            if (Features.on("warps", "warps_list")) {
                d.register(Commands.literal("warps").requires(s -> Perm.has(s, Perm.WARP_USE)).executes(TeleportCommands::warps));
            }
        }

        // ---- back ----
        if (Features.on("back")) {
            d.register(Commands.literal("back").requires(s -> s.getPlayer() != null && Perm.has(s, Perm.BACK_USE))
                    .executes(TeleportCommands::back));
        }

    }

    // ---------- staff ----------

    /** Staff with nexus.tp.use (and the staff switch on) teleport right away. */
    private static boolean staffInstant(CommandSourceStack s) {
        return Features.on("teleport") && Features.on("teleport", "tp_player") && Perm.has(s, Perm.TP);
    }

    private static boolean canTp(CommandSourceStack s, String key) {
        return staffInstant(s) || (Features.on("tpa") && Features.on("tpa", key) && Perm.has(s, Perm.TPA_USE));
    }

    /** bring = false: go to them (/tpo). bring = true: bring them here (/tpi). */
    private static int tp(CommandContext<CommandSourceStack> c, boolean bring) throws CommandSyntaxException {
        ServerPlayer me = c.getSource().getPlayer();
        ServerPlayer other = EntityArgument.getPlayer(c, "player");
        if (me != null && com.tac5studios.elementsnexus.vanish.Vanish.hiddenFrom(other, me)) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }
        if (other == me) return fail(c, "That's you.");
        if (staffInstant(c.getSource())) {
            if (bring) {
                Teleports.now(other, Spot.of(me), me.getGameProfile().getName());
                StaffLog.add(c.getSource(), "tp here", other.getUUID(), other.getGameProfile().getName(), null, null);
                return ok(c, "&aBrought " + other.getGameProfile().getName() + " to you.");
            }
            Teleports.now(me, Spot.of(other), other.getGameProfile().getName());
            StaffLog.add(c.getSource(), "tp to", other.getUUID(), other.getGameProfile().getName(), null, null);
            return 1;
        }
        return tpaAsk(c, me, other, bring);
    }

    private static int tpPos(CommandContext<CommandSourceStack> c) {
        ServerPlayer me = c.getSource().getPlayer();
        Vec3 v = Vec3Argument.getVec3(c, "pos");
        Teleports.now(me, new Spot(me.level().dimension().location().toString(), v.x, v.y, v.z, me.getYRot(), me.getXRot()),
                (int) v.x + ", " + (int) v.y + ", " + (int) v.z);
        return 1;
    }

    // ---------- spawn ----------

    private static int spawn(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (!Perm.has(p, Perm.SPAWN_USE)) return fail(c, "You can't use /spawn.");
        Spot s = Storage.get().get("settings", "spawn", Spot.class);
        if (s == null) {
            ServerLevel world = c.getSource().getServer().overworld();
            BlockPos pos = world.getSharedSpawnPos();
            s = new Spot(world.dimension().location().toString(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getSharedSpawnAngle(), 0);
        }
        Teleports.go(p, s, "spawn");
        return 1;
    }

    private static int spawnSet(CommandContext<CommandSourceStack> c) {
        Storage.get().put("settings", "spawn", Spot.of(c.getSource().getPlayer()));
        StaffLog.add(c.getSource(), "spawn set", null, null, null, null);
        return ok(c, "&aSpawn set here.");
    }

    // ---------- warps ----------

    private static int warp(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
        Spot s = Storage.get().get("warps", name, Spot.class);
        if (s == null) return fail(c, "There is no warp called " + name + ".");
        Teleports.go(c.getSource().getPlayer(), s, "warp " + name);
        return 1;
    }

    private static int warpSet(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
        if (!WARP_NAME.matcher(name).matches()) return fail(c, "Warp names use a-z, 0-9, _ and - (max 32).");
        boolean moved = Storage.get().has("warps", name);
        Storage.get().put("warps", name, Spot.of(c.getSource().getPlayer()));
        StaffLog.add(c.getSource(), moved ? "warp move" : "warp set", null, name, null, null);
        return ok(c, "&aWarp " + name + (moved ? " moved here." : " set."));
    }

    private static int warpDelete(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
        if (!Storage.get().has("warps", name)) return fail(c, "There is no warp called " + name + ".");
        Storage.get().remove("warps", name);
        StaffLog.add(c.getSource(), "warp delete", null, name, null, null);
        return ok(c, "&aWarp " + name + " deleted.");
    }

    private static int warps(CommandContext<CommandSourceStack> c) {
        List<String> names = Storage.get().keys("warps");
        if (names.isEmpty()) return ok(c, "&7There are no warps yet.");
        names.sort(String::compareTo);
        return ok(c, "&6Warps: &f" + String.join("&7, &f", names));
    }

    // ---------- back ----------

    private static int back(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        Spot s = Teleports.backSpot(p.getUUID());
        if (s == null) return fail(c, "There is nowhere to go back to yet.");
        Teleports.go(p, s, "your last location");
        return 1;
    }

    // ---------- tpa ----------

    private static int tpaAsk(CommandContext<CommandSourceStack> c, ServerPlayer me, ServerPlayer to, boolean here) {
        REQUESTS.put(to.getUUID(), new Request(me.getUUID(), here, System.currentTimeMillis()));
        String name = me.getGameProfile().getName();
        net.minecraft.network.chat.MutableComponent msg = Text.color(here
                ? "&e" + name + " wants you to teleport to them. "
                : "&e" + name + " wants to teleport to you. ");
        msg.append(button("[Accept]", ChatFormatting.GREEN, "/tpaccept", "Click to accept"));
        msg.append(Component.literal(" "));
        msg.append(button("[Deny]", ChatFormatting.RED, "/tpdeny", "Click to deny"));
        to.sendSystemMessage(msg);
        return ok(c, "&aRequest sent to " + to.getGameProfile().getName() + ". It expires in 60 seconds.");
    }

    private static Component button(String text, ChatFormatting color, String command, String hover) {
        return Component.literal(text).withStyle(st -> st.withColor(color).withBold(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static int tpaAccept(CommandContext<CommandSourceStack> c) {
        ServerPlayer me = c.getSource().getPlayer();
        Request r = REQUESTS.remove(me.getUUID());
        if (r == null || System.currentTimeMillis() - r.time() > TPA_TIMEOUT_MS) return fail(c, "You have no teleport request.");
        ServerPlayer from = c.getSource().getServer().getPlayerList().getPlayer(r.from());
        if (from == null) return fail(c, "That player is no longer online.");
        from.sendSystemMessage(Text.color("&a" + me.getGameProfile().getName() + " accepted your request."));
        if (r.here()) Teleports.go(me, Spot.of(from), from.getGameProfile().getName());
        else Teleports.go(from, Spot.of(me), me.getGameProfile().getName());
        return ok(c, "&aRequest accepted.");
    }

    private static int tpaDeny(CommandContext<CommandSourceStack> c) {
        ServerPlayer me = c.getSource().getPlayer();
        Request r = REQUESTS.remove(me.getUUID());
        if (r == null) return fail(c, "You have no teleport request.");
        ServerPlayer from = c.getSource().getServer().getPlayerList().getPlayer(r.from());
        if (from != null) from.sendSystemMessage(Text.color("&c" + me.getGameProfile().getName() + " denied your request."));
        return ok(c, "&7Request denied.");
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
