package com.tac5studios.elementsnexus.perms;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.ranks.Ranks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every Elements: Nexus permission node, and the one place that checks them.
 * Nodes are also registered with NeoForge so any permission mod can manage them.
 */
public final class Perm {

    /** Who gets a node when no rank says otherwise. */
    public enum Who {
        PLAYER(0),  // everyone
        STAFF(2),   // OP level 2 and up
        ADMIN(3);   // OP level 3 and up

        final int opLevel;

        Who(int opLevel) {
            this.opLevel = opLevel;
        }
    }

    private static final Map<String, PermissionNode<Boolean>> NODES = new LinkedHashMap<>();
    private static final Map<String, Who> DEFAULTS = new LinkedHashMap<>();

    // ---------- ranks ----------
    public static final String RANK_SET = node("rank.set", Who.ADMIN);
    public static final String RANK_INFO = node("rank.info", Who.STAFF);
    public static final String RANK_CHECK = node("rank.check", Who.STAFF);
    public static final String RANK_ADMIN = node("rank.admin", Who.ADMIN);

    // ---------- chat ----------
    public static final String MSG_USE = node("msg.use", Who.PLAYER);
    public static final String MSG_SPY = node("msg.spy", Who.STAFF);
    public static final String STAFFCHAT = node("staffchat.use", Who.STAFF);
    /** Staff message color, colors in chat, links, no cooldown, can't be ignored. */
    public static final String CHAT_STAFF = node("chat.staff", Who.STAFF);
    public static final String FILTER_BYPASS = node("chat.filter.bypass", Who.STAFF);

    // ---------- homes / teleports ----------
    public static final String HOME_USE = node("home.use", Who.PLAYER);
    public static final String HOME_OTHERS = node("home.others", Who.STAFF);
    public static final String TELEPORT_BYPASS = node("teleport.bypass", Who.STAFF);

    // ---------- kits ----------
    public static final String KIT_USE = node("kit.use", Who.PLAYER);
    public static final String KIT_ADMIN = node("kit.admin", Who.ADMIN);

    // ---------- afk ----------
    public static final String AFK_USE = node("afk.use", Who.PLAYER);
    public static final String AFK_EXEMPT = node("afk.exempt", Who.STAFF);

    // ---------- moderation ----------
    public static final String MOD_WARN = node("mod.warn", Who.STAFF);
    public static final String MOD_MUTE = node("mod.mute", Who.STAFF);
    public static final String MOD_KICK = node("mod.kick", Who.STAFF);
    public static final String MOD_BAN = node("mod.ban", Who.STAFF);
    public static final String MOD_BANIP = node("mod.banip", Who.ADMIN);
    public static final String MOD_FREEZE = node("mod.freeze", Who.STAFF);
    public static final String MOD_JAIL = node("mod.jail", Who.STAFF);
    public static final String MOD_JAIL_SET = node("mod.jail.set", Who.ADMIN);
    public static final String MOD_INV = node("mod.inv", Who.STAFF);
    public static final String MOD_INV_EDIT = node("mod.inv.edit", Who.ADMIN);
    public static final String MOD_HISTORY = node("mod.history", Who.STAFF);
    /** Can't be warned, muted, kicked, banned, frozen or jailed by other staff. */
    public static final String MOD_EXEMPT = node("mod.exempt", Who.ADMIN);

    // ---------- teleport commands ----------
    public static final String TP = node("tp.use", Who.STAFF);
    public static final String SPAWN_USE = node("spawn.use", Who.PLAYER);
    public static final String SPAWN_SET = node("spawn.set", Who.ADMIN);
    public static final String WARP_USE = node("warp.use", Who.PLAYER);
    public static final String WARP_ADMIN = node("warp.admin", Who.STAFF);
    public static final String BACK_USE = node("back.use", Who.PLAYER);
    public static final String TPA_USE = node("tpa.use", Who.PLAYER);

    // ---------- vanish ----------
    public static final String VANISH = node("mod.vanish", Who.STAFF);
    public static final String VANISH_SEE = node("mod.vanish.see", Who.STAFF);

    // ---------- nicknames ----------
    public static final String NICK_SELF = node("nick.self", Who.PLAYER);
    public static final String NICK_OTHERS = node("nick.others", Who.STAFF);
    /** Use & colors in nicknames. */
    public static final String NICK_COLOR = node("nick.color", Who.STAFF);
    public static final String NICK_REALNAME = node("nick.realname", Who.STAFF);

    // ---------- rules ----------
    public static final String RULES_VIEW = node("rules.view", Who.PLAYER);
    public static final String RULES_EDIT = node("rules.edit", Who.ADMIN);

    // ---------- broadcast ----------
    public static final String BROADCAST = node("broadcast", Who.STAFF);

    // ---------- announcements ----------
    public static final String RESTART_WARN = node("restartwarn", Who.ADMIN);

    // ---------- admin ----------
    /** /nexus and its subcommands. */
    public static final String ADMIN = node("admin", Who.ADMIN);

    // ---------- side panel / discord ----------
    public static final String SIDEPANEL = node("sidepanel.toggle", Who.PLAYER);
    public static final String DISCORD_LINK = node("discord.link", Who.PLAYER);

    // ---------- holograms ----------
    public static final String HOLO = node("holo", Who.ADMIN);

    // ---------- restricted commands ----------
    /** Can use the commands listed as staff_only in commands.toml. */
    public static final String COMMANDS_RESTRICTED = node("commands.restricted", Who.STAFF);

    private Perm() {}

    /** Create a node named "nexus.<path>". */
    private static String node(String path, Who who) {
        PermissionNode<Boolean> n = new PermissionNode<>("nexus", path, PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> player != null && player.hasPermissions(who.opLevel));
        NODES.put(n.getNodeName(), n);
        DEFAULTS.put(n.getNodeName(), who);
        return n.getNodeName();
    }

    /** Tell NeoForge about our nodes. */
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.values().toArray(new PermissionNode<?>[0]));
    }

    /** Can this command source use this node? Console and command blocks use their OP level. */
    public static boolean has(CommandSourceStack source, String node) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            Who who = DEFAULTS.getOrDefault(node, Who.ADMIN);
            return source.hasPermission(Math.max(who.opLevel, 2));
        }
        return has(player, node);
    }

    public static boolean has(ServerPlayer player, String node) {
        if (Features.on("ranks")) {
            if (Features.on("ranks", "ops_bypass") && isOp(player)) return true;
            Ranks.Result r = Ranks.check(player.getUUID(), node);
            if (r != null) return r.allowed();
            Who who = DEFAULTS.get(node);
            return who != null && player.hasPermissions(who.opLevel);
        }
        // Ranks are off: ask whatever permission handler NeoForge is using.
        PermissionNode<Boolean> n = NODES.get(node);
        if (n == null) return player.hasPermissions(2);
        try {
            return PermissionAPI.getPermission(player, n);
        } catch (RuntimeException e) {
            ElementsNexus.LOGGER.warn("[Nexus] Permission check failed for {}: {}", node, e.toString());
            return player.hasPermissions(2);
        }
    }

    public static boolean isOp(ServerPlayer player) {
        return player.server.getPlayerList().isOp(player.getGameProfile());
    }

    /** What a node gives when no rank mentions it (for /rank check). */
    public static String defaultText(String node) {
        Who who = DEFAULTS.get(node);
        return who == null ? "not a Nexus node" : switch (who) {
            case PLAYER -> "everyone";
            case STAFF -> "OP level 2+";
            case ADMIN -> "OP level 3+";
        };
    }

    public static boolean defaultFor(ServerPlayer player, String node) {
        Who who = DEFAULTS.get(node);
        return who != null && player.hasPermissions(who.opLevel);
    }
}
