package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** config/elements_nexus/discord.toml - turn Discord on in features.toml first. */
public final class DiscordConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> TOKEN;
    public static final ModConfigSpec.ConfigValue<String> GUILD;
    public static final ModConfigSpec.ConfigValue<String> PUBLIC_CHANNEL;
    public static final ModConfigSpec.ConfigValue<String> STAFF_CHANNEL;
    public static final ModConfigSpec.ConfigValue<String> CHAT_CHANNEL;
    public static final ModConfigSpec.ConfigValue<String> STAFF_CHAT_CHANNEL;
    public static final ModConfigSpec.ConfigValue<String> STATUS_CHANNEL;
    public static final ModConfigSpec.ConfigValue<String> STATUS_ONLINE;
    public static final ModConfigSpec.ConfigValue<String> STATUS_OFFLINE;
    public static final ModConfigSpec.IntValue STATUS_MINUTES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RANK_ROLES;

    public static final ModConfigSpec.ConfigValue<String> TO_DISCORD;
    public static final ModConfigSpec.ConfigValue<String> TO_GAME;
    public static final ModConfigSpec.ConfigValue<String> STAFF_TO_DISCORD;
    public static final ModConfigSpec.ConfigValue<String> STAFF_TO_GAME;
    public static final ModConfigSpec.ConfigValue<String> START;
    public static final ModConfigSpec.ConfigValue<String> STOP;
    public static final ModConfigSpec.ConfigValue<String> RESTART;
    public static final ModConfigSpec.ConfigValue<String> CRASH;
    public static final ModConfigSpec.ConfigValue<String> JOIN;
    public static final ModConfigSpec.ConfigValue<String> LEAVE;
    public static final ModConfigSpec.ConfigValue<String> RANK_UP;
    public static final ModConfigSpec.ConfigValue<String> MODERATION;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Your Discord bot.",
                "Make one at discord.com/developers, add it to your server,",
                "and turn on \"Message Content Intent\" (and \"Server Members Intent\" if you use role_to_rank).").push("bot");
        TOKEN = b.comment("The bot token. Keep it secret.").define("token", "");
        GUILD = b.comment("Your Discord server ID (right-click the server > Copy Server ID).").define("server_id", "");
        b.pop();

        b.comment("Channel IDs (right-click a channel > Copy Channel ID). Leave empty to not use one.").push("channels");
        PUBLIC_CHANNEL = b.comment("Joins, leaves and rank-ups.").define("public", "");
        STAFF_CHANNEL = b.comment("Server start/stop, crash alerts and moderation actions.").define("staff", "");
        CHAT_CHANNEL = b.comment("Linked to game chat both ways.").define("chat", "");
        STAFF_CHAT_CHANNEL = b.comment("Linked to staff chat both ways. Empty = use the staff channel.").define("staff_chat", "");
        STATUS_CHANNEL = b.comment("Its name shows the player count (see [status]). Empty = off.").define("status", "");
        b.pop();

        b.comment("Status channel: the channel name shows if the server is up and how many players are on.",
                "Use a voice channel that nobody can join: text channel names can't have spaces or capitals.",
                "The bot needs the \"Manage Channels\" permission on that channel.",
                "Discord only allows 2 name changes per channel every 10 minutes, so the count is not instant.").push("status");
        STATUS_ONLINE = b.comment("Name while the server is up. Available: {online} {max}").define("online", "🟢 Players: {online}/{max}");
        STATUS_OFFLINE = b.comment("Name while the server is down (set when it stops).").define("offline", "🔴 Server Offline");
        STATUS_MINUTES = b.comment("Minutes between updates (5-60). 10 keeps one name change spare,",
                        "so the offline name always gets through when the server stops.")
                .defineInRange("update_minutes", 10, 5, 60);
        b.pop();

        b.comment("Discord role for each rank. Format: \"rank=role ID\".").push("roles");
        RANK_ROLES = b.comment("Example: [\"vip=123456789012345678\"]")
                .defineListAllowEmpty("rank_roles", java.util.ArrayList::new, () -> "", o -> o instanceof String);
        b.pop();

        b.comment("How messages look. Available: {player} {rank} {title} {message} {user}").push("format");
        TO_DISCORD = b.comment("Game chat in Discord.").define("chat_to_discord", "**{rank}** {title} {player}: {message}");
        TO_GAME = b.comment("Discord chat in game. {user} = Discord name, or their Minecraft name once linked.")
                .define("chat_to_game", "&9[Discord] &f{user}&7: &f{message}");
        STAFF_TO_DISCORD = b.comment("Staff chat in Discord.").define("staff_to_discord", "**{player}**: {message}");
        STAFF_TO_GAME = b.comment("Discord staff messages in game.").define("staff_to_game", "&c[Staff] &9[Discord] &f{user}&7: &f{message}");
        START = b.comment("Server started.").define("start", ":green_circle: Server started.");
        STOP = b.comment("Server stopped.").define("stop", ":red_circle: Server stopped.");
        RESTART = b.comment("Restart warning from /restartwarn. Available: {time}").define("restart", ":warning: Server is restarting in {time}.");
        CRASH = b.comment("Server came back after it did not stop cleanly.").define("crash", ":warning: The server stopped without shutting down cleanly (crash or kill).");
        JOIN = b.comment("Player joined.").define("join", ":arrow_right: **{player}** joined.");
        LEAVE = b.comment("Player left.").define("leave", ":arrow_left: **{player}** left.");
        RANK_UP = b.comment("Rank-up.").define("rank_up", ":star: **{player}** ranked up to **{rank}**!");
        MODERATION = b.comment("Moderation. Also: {staff} {action} {target} {reason} {duration}")
                .define("moderation", ":shield: **{staff}** {action} **{target}**{reason}{duration}");
        b.pop();

        SPEC = b.build();
    }

    private DiscordConfig() {}
}
