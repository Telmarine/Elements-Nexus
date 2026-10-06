package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The master control file: config/elements_nexus/features.toml
 * One switch per feature, plus one switch per command or function inside it.
 * Feature off = everything under it is off.
 */
public final class Features {

    public static final ModConfigSpec SPEC;

    /** Every switch, keyed by its full path, e.g. "homes.sethome". */
    private static final Map<String, ModConfigSpec.BooleanValue> SWITCHES = new LinkedHashMap<>();

    /** The custom console name text. */
    public static final ModConfigSpec.ConfigValue<String> CONSOLE_NAME;

    private static final ModConfigSpec.BooleanValue OVERLAP_GUARD;
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> FORCE_ON;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment(
                "MASTER CONTROL FILE",
                "true = on, false = off.",
                "Turning a feature off turns off everything under it.",
                "Changes to command switches need a server restart."
        );

        b.comment("Turn Nexus features off when another mod already does the same job.").push("overlap_guard");
        OVERLAP_GUARD = b.comment("Turn the overlap guard on or off.").define("enabled", true);
        FORCE_ON = b.comment("Features to keep on anyway, e.g. [\"tablist\", \"homes\"].")
                .defineListAllowEmpty("force_on", java.util.ArrayList::new, () -> "", o -> o instanceof String);
        b.pop();

        section(b, "ranks", "Ranks and permissions.",
                "permission_handler", true, "Let other mods see Elements: Nexus ranks. Also set permissionHandler = \"elements_nexus:permissions\" in neoforge-server.toml.",
                "ops_bypass", true, "OPs have every Elements: Nexus permission.",
                "rank_set", true, "/rank set - put a player in a rank.",
                "rank_info", true, "/rank info - show a player's rank.",
                "rank_check", true, "/rank check - see if a player has a permission.",
                "group_admin", true, "/rank group - create and edit ranks.");

        section(b, "tablist", "The Tab key player list.",
                "rank_tags", true, "Show rank tags in the list.",
                "nametags", true, "Show rank tags above player heads.",
                "sorting", true, "Sort the list by rank.",
                "header_footer", true, "Show the lines above and below the list.",
                "afk_marker", true, "Mark AFK players in the list.");

        section(b, "sidepanel", "The side panel on the right of the screen.",
                "player_toggle", true, "/sidepanel - players can hide or show it.");

        section(b, "chat", "Chat look and rules.",
                "format", true, "Use the chat format from chat.toml.",
                "prefixes", true, "Show rank prefixes in chat.",
                "title_scrolls_hook", true, "Show the player's Title Scrolls title in chat.",
                "filter", true, "Use the word filter from chat_filter.toml.");

        section(b, "messaging", "Private messages.",
                "msg", true, "/msg - send a private message.",
                "reply", true, "/r - reply to the last message.",
                "ignore", true, "/ignore - hide messages from a player.",
                "socialspy", true, "/msg spy - staff can see private messages.");

        section(b, "staffchat", "Staff-only chat channel (/sc).");

        section(b, "homes", "Player homes.",
                "home", true, "/home - go to a home.",
                "sethome", true, "/sethome - set a home.",
                "delhome", true, "/delhome - delete a home.",
                "homes_list", true, "/homes - list your homes.",
                "visit_others", false, "Staff can visit other players' homes.",
                "no_home_worlds", true, "Block homes in the worlds listed in teleport.toml no_home_worlds (empty list = nothing blocked).");

        section(b, "tpa", "Teleport requests between players (/tpo and /tpi for players).",
                "tpo", true, "/tpo <player> - ask to go to a player.",
                "tpi", true, "/tpi <player> - ask a player to come to you.",
                "accept", true, "/tpaccept - accept a request (or click [Accept]).",
                "deny", true, "/tpdeny - deny a request (or click [Deny]).");

        section(b, "teleport", "Staff teleports.",
                "tp_player", true, "Staff using /tpo and /tpi teleport right away, no request needed.",
                "tp_coords", true, "/tppos - go to coordinates.");

        section(b, "spawn", "Server spawn point.",
                "spawn", true, "/spawn - players can teleport to spawn.",
                "spawn_set", true, "/spawn set - set the spawn point.");

        section(b, "back", "/back - return to where you were before your last teleport or death.");

        section(b, "warps", "Named teleport points.",
                "warp", true, "/warp - go to a warp.",
                "warps_list", true, "/warps - list warps.",
                "warp_set", true, "/warp set - create a warp.",
                "warp_delete", true, "/warp delete - delete a warp.");

        section(b, "teleport_safety", "Safety rules for all teleports.",
                "warmup", true, "Players must stand still for a few seconds first.",
                "combat_lock", true, "No teleporting right after a fight.");

        section(b, "afk", "Away-from-keyboard detection.",
                "auto_detect", true, "Mark players AFK when they stop moving.",
                "afk_command", true, "/afk - players can mark themselves AFK.",
                "afk_kick", false, "Kick players who stay AFK too long.",
                "announce", true, "Tell everyone in chat when a player goes AFK or comes back.");

        section(b, "kits", "Item kits.",
                "kit", true, "/kit - claim a kit.",
                "kit_list", true, "/kit list - list kits.",
                "kit_create", true, "/kit create - make a kit.",
                "kit_delete", true, "/kit delete - delete a kit.",
                "kit_give", true, "/kit give - give a kit to a player.");

        section(b, "moderation", "Staff moderation tools.",
                "warn", true, "/warn",
                "mute", true, "/mute and /unmute",
                "kick", true, "/kick",
                "ban", true, "/ban and /unban",
                "tempban", true, "Bans with a time limit.",
                "banip", true, "/banip and /unbanip - block every account from an IP address.",
                "freeze", true, "/freeze - stop a player from moving.",
                "jail", true, "/jail and /unjail",
                "inv", true, "/inv - view a player's inventory and ender chest.",
                "inv_edit", true, "Let admins change items in /inv.",
                "history", true, "/history - see a player's warns, mutes and bans.");

        section(b, "vanish", "/vanish - staff become invisible.",
                "hide_everywhere", true, "Also hide from the tab list, chat and join messages.");

        section(b, "nicknames", "Nicknames.",
                "self_nick", false, "/nick - players can set their own nickname.",
                "nick_others", true, "/nick <player> - staff can set nicknames.",
                "realname", true, "/realname - see who is behind a nickname.");

        section(b, "rules", "Server rules.",
                "view", true, "/rules - show the rules.",
                "edit", true, "/rules edit - change the rules in game.");

        section(b, "pvp", "Player PvP choice. Only damage is blocked, so riding and other right-clicks still work. Settings in toggles.toml.",
                "player_toggle", true, "/pvp - players turn their own PvP on or off.",
                "server_toggle", true, "/pvp server on|off - staff turn PvP off for everyone.",
                "protect_pets", true, "Tamed pets count as their owner: they can't hurt or be hurt by players with PvP off.");

        section(b, "phantoms", "Player phantom choice. Settings in toggles.toml.",
                "player_toggle", true, "/phantoms - players turn phantom spawning on or off for themselves.");

        sectionOff(b, "waystones", "Rules for waystones per dimension (Waystones and similar mods). Rules go in waystones.toml.",
                "place", true, "Apply the placing rules.",
                "activate", true, "Apply the activating rules (Waystones).",
                "teleport", true, "Apply the teleport rules (Waystones).");

        section(b, "help", "/help - lists only the commands you can use.");

        section(b, "holograms", "Floating text (/holo).");

        section(b, "staff_only_commands", "Make the commands listed in commands.toml staff only.");

        section(b, "broadcast", "Server-wide messages from staff.",
                "chat", true, "/broadcast - message in chat.",
                "screen", true, "/broadcast screen - big text in the middle of the screen.");

        section(b, "announcements", "Automatic messages.",
                "timed", true, "Messages that repeat on a timer.",
                "restart_warnings", true, "Warn players before a server restart.");

        section(b, "messages", "Join, leave and welcome messages.",
                "join_leave", true, "Show a message when players join or leave.",
                "first_join", true, "Show a welcome message for new players.",
                "motd", true, "Show the message of the day on join.");

        section(b, "rank_ups", "Rank-ups.",
                "vote_promotions", true, "Run each rank's on_promote commands when a player ranks up.",
                "announce", true, "Tell the server when someone ranks up.");

        sectionOff(b, "temp_ranks", "Ranks that expire after a set time.");

        section(b, "staff_log", "Save a log of every staff action.");

        b.comment("Replace \"Server\" with your own name in console messages (/say, /msg, staff chat, bans). & colors work.").push("console_name");
        SWITCHES.put("console_name.enabled", b.comment("Turn the custom console name on or off.").define("enabled", true));
        CONSOLE_NAME = b.comment("The name to show.").define("name", "Server");
        b.pop();

        b.comment("Discord link. Bot settings go in discord.toml.").push("discord");
        put(b, "discord", "enabled", false, "Turn the Discord link on or off.");
        put(b, "discord", "link", true, "/link - players link their Discord account.");
        put(b, "discord", "role_sync", true, "Give players their rank's Discord role.");
        put(b, "discord", "role_to_rank", false, "Give players a rank from their Discord role.");
        b.comment("Events posted to Discord.").push("events");
        put(b, "discord.events", "start_stop", true, "Server start and stop.");
        put(b, "discord.events", "crash", true, "Server crash.");
        put(b, "discord.events", "join_leave", true, "Players joining and leaving.");
        put(b, "discord.events", "rank_up", true, "Rank-ups.");
        put(b, "discord.events", "moderation", true, "Warns, mutes, kicks and bans.");
        b.pop();
        b.comment("Chat between the game and Discord.").push("chat_bridge");
        put(b, "discord.chat_bridge", "to_discord", true, "Send game chat to Discord.");
        put(b, "discord.chat_bridge", "to_game", true, "Send Discord chat to the game.");
        put(b, "discord.chat_bridge", "staff_chat", true, "Link staff chat to a staff Discord channel.");
        b.pop();
        b.pop();

        section(b, "admin", "/nexus admin command.",
                "reload", true, "/nexus reload - reload the config files without a restart.",
                "storage_convert", true, "/nexus storage convert - copy all data to another storage type.");

        section(b, "migration", "Import data from other mods. Only offered if that mod's data is found.",
                "neoessentials", true, "NeoEssentials",
                "ftb_ranks", true, "FTB Ranks",
                "ftb_essentials", true, "FTB Essentials",
                "luckperms", true, "LuckPerms",
                "tab", true, "TAB");

        SPEC = b.build();
    }

    private Features() {}

    /** A feature with an "enabled" switch (default on) and child switches. */
    private static void section(ModConfigSpec.Builder b, String name, String comment, Object... children) {
        build(b, name, comment, true, children);
    }

    /** Same as section(), but the feature starts turned off. */
    private static void sectionOff(ModConfigSpec.Builder b, String name, String comment, Object... children) {
        build(b, name, comment, false, children);
    }

    /** A feature with an "enabled" switch and child switches given as (key, default, comment) triples. */
    private static void build(ModConfigSpec.Builder b, String name, String comment, boolean enabledDefault, Object... children) {
        b.comment(comment).push(name);
        put(b, name, "enabled", enabledDefault, "Turn this feature on or off.");
        for (int i = 0; i < children.length; i += 3) {
            put(b, name, (String) children[i], (Boolean) children[i + 1], (String) children[i + 2]);
        }
        b.pop();
    }

    private static void put(ModConfigSpec.Builder b, String path, String key, boolean def, String comment) {
        SWITCHES.put(path + "." + key, b.comment(comment).define(key, def));
    }

    /** True if the feature is on. Example: on("homes") */
    public static boolean on(String feature) {
        return get(feature + ".enabled") && parentsOn(feature) && !Overlap.blocks(feature);
    }

    /** True if the feature AND this child switch are on. Example: on("homes", "sethome") */
    public static boolean on(String feature, String child) {
        return on(feature) && get(feature + "." + child) && !Overlap.blocks(feature + "." + child);
    }

    /** Nested groups (e.g. "discord.events") follow their parent feature. */
    private static boolean parentsOn(String feature) {
        int dot = feature.lastIndexOf('.');
        if (dot < 0) return true;
        return on(feature.substring(0, dot));
    }

    private static boolean get(String path) {
        ModConfigSpec.BooleanValue v = SWITCHES.get(path);
        return v == null || v.get(); // groups without their own switch (e.g. discord.events) count as on
    }

    static boolean overlapGuardOn() {
        try {
            return OVERLAP_GUARD.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    static java.util.List<? extends String> forceOn() {
        try {
            return FORCE_ON.get();
        } catch (IllegalStateException notLoaded) {
            return java.util.List.of();
        }
    }

    public static Map<String, ModConfigSpec.BooleanValue> all() {
        return SWITCHES;
    }
}
