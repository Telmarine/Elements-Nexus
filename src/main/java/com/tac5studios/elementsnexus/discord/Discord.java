package com.tac5studios.elementsnexus.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.chat.ChatFilter;
import com.tac5studios.elementsnexus.chat.StaffChat;
import com.tac5studios.elementsnexus.config.DiscordConfig;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.moderation.Punish;
import com.tac5studios.elementsnexus.nick.Nick;
import com.tac5studios.elementsnexus.ranks.Rank;
import com.tac5studios.elementsnexus.ranks.RankUps;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.util.Placeholders;
import com.tac5studios.elementsnexus.util.Text;
import com.tac5studios.elementsnexus.vanish.Vanish;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Discord link: event feed, chat bridge, staff chat bridge, account links and rank roles.
 * Every method is safe to call when Discord is off - it just does nothing.
 */
public final class Discord {

    private static final String LINKS = "discord_links"; // uuid -> Discord user ID
    private static final long CODE_LIFE = 10 * 60 * 1000L;

    private record Pending(UUID player, String name, long expires) {}

    private static final Map<String, Pending> CODES = new ConcurrentHashMap<>();

    private static volatile MinecraftServer server;
    private static volatile DiscordRest rest;
    private static volatile DiscordGateway gateway;

    private Discord() {}

    public static boolean running() {
        return rest != null;
    }

    // ---------- start / stop ----------

    public static void start(MinecraftServer srv) {
        server = srv;
        boolean wasClean = !Storage.get().has("meta", "running");
        Storage.get().put("meta", "running", true);
        if (!Features.on("discord")) return;
        String token = DiscordConfig.TOKEN.get().trim();
        if (token.isEmpty()) {
            ElementsNexus.LOGGER.warn("[Nexus] Discord is on but has no bot token. Set it in discord.toml.");
            return;
        }
        rest = new DiscordRest(token);
        boolean incoming = Features.on("discord", "link") || Features.on("discord.chat_bridge", "to_game")
                || Features.on("discord.chat_bridge", "staff_chat") || Features.on("discord", "role_to_rank");
        if (incoming) {
            int intents = 1 | (1 << 9) | (1 << 12) | (1 << 15); // guilds, guild messages, DMs, message content
            if (Features.on("discord", "role_to_rank")) intents |= (1 << 1); // server members
            gateway = new DiscordGateway(rest.http(), token, intents, Discord::onGatewayEvent);
            gateway.start();
        }
        if (!wasClean && Features.on("discord.events", "crash")) rest.send(DiscordConfig.STAFF_CHANNEL.get(), DiscordConfig.CRASH.get());
        if (Features.on("discord.events", "start_stop")) rest.send(DiscordConfig.STAFF_CHANNEL.get(), DiscordConfig.START.get());
    }

    /** Called while the server stops. Waits a few seconds so the last message gets out. */
    public static void stop() {
        try {
            if (Storage.get() != null) Storage.get().remove("meta", "running");
        } catch (IllegalStateException ignored) {
        }
        DiscordRest r = rest;
        if (r == null) return;
        if (Features.on("discord.events", "start_stop")) r.send(DiscordConfig.STAFF_CHANNEL.get(), DiscordConfig.STOP.get());
        if (gateway != null) gateway.stop();
        r.drain(5);
        rest = null;
        gateway = null;
    }

    // ---------- events from the game ----------

    public static void join(ServerPlayer p) {
        if (rest == null || Vanish.isVanished(p) || !Features.on("discord.events", "join_leave")) return;
        rest.send(DiscordConfig.PUBLIC_CHANNEL.get(), fill(DiscordConfig.JOIN.get(), p, ""));
    }

    public static void leave(ServerPlayer p) {
        if (rest == null || Vanish.isVanished(p) || !Features.on("discord.events", "join_leave")) return;
        rest.send(DiscordConfig.PUBLIC_CHANNEL.get(), fill(DiscordConfig.LEAVE.get(), p, ""));
    }

    public static void rankUp(String name, String rank, String rankShown) {
        if (rest == null || !Features.on("discord.events", "rank_up")) return;
        rest.send(DiscordConfig.PUBLIC_CHANNEL.get(),
                DiscordConfig.RANK_UP.get().replace("{player}", name).replace("{rank}", rankShown));
    }

    /** Called for every staff log entry; only moderation actions are posted. */
    public static void staffAction(String staff, String action, String target, String reason, String duration) {
        if (rest == null || !Features.on("discord.events", "moderation")) return;
        if (!isModeration(action)) return;
        rest.send(DiscordConfig.STAFF_CHANNEL.get(), DiscordConfig.MODERATION.get()
                .replace("{staff}", staff)
                .replace("{action}", past(action))
                .replace("{target}", target == null ? "" : target.matches("[0-9.:a-fA-F]+") && target.contains(".") || target.matches("[0-9a-fA-F:]+:[0-9a-fA-F:]+") ? "an IP address" : target)
                .replace("{reason}", reason == null || reason.isBlank() ? "" : " - " + reason)
                .replace("{duration}", duration == null || duration.isBlank() ? "" : " (" + duration + ")"));
    }

    /** Restart countdown started (/restartwarn). Posted with the server events. */
    public static void restartWarn(String time) {
        if (rest == null || !Features.on("discord.events", "start_stop")) return;
        String msg = DiscordConfig.RESTART.get();
        if (!msg.isBlank()) rest.send(DiscordConfig.STAFF_CHANNEL.get(), msg.replace("{time}", time));
    }

    public static void gameChat(ServerPlayer p, String message) {
        if (rest == null || !Features.on("discord.chat_bridge", "to_discord")) return;
        rest.send(DiscordConfig.CHAT_CHANNEL.get(), fill(DiscordConfig.TO_DISCORD.get(), p, message));
    }

    public static void staffChat(String name, String message) {
        if (rest == null || !Features.on("discord.chat_bridge", "staff_chat")) return;
        rest.send(staffChatChannel(), DiscordConfig.STAFF_TO_DISCORD.get()
                .replace("{player}", plain(name)).replace("{message}", markdown(plain(message))));
    }

    // ---------- account links ----------

    /** A new code for /link. */
    public static String newCode(ServerPlayer p) {
        CODES.values().removeIf(c -> c.player().equals(p.getUUID()) || c.expires() < System.currentTimeMillis());
        String code;
        do {
            code = String.valueOf(100000 + ThreadLocalRandom.current().nextInt(900000));
        } while (CODES.containsKey(code));
        CODES.put(code, new Pending(p.getUUID(), p.getGameProfile().getName(), System.currentTimeMillis() + CODE_LIFE));
        return code;
    }

    public static String linkedUser(UUID player) {
        return Storage.get().get(LINKS, player.toString(), String.class);
    }

    public static void unlink(UUID player) {
        String user = linkedUser(player);
        if (user == null) return;
        Storage.get().remove(LINKS, player.toString());
        if (rest != null && Features.on("discord", "role_sync")) {
            for (String role : roleMap().values()) rest.removeRole(guild(), user, role);
        }
    }

    private static UUID playerFor(String discordUser) {
        for (String key : Storage.get().keys(LINKS)) {
            if (discordUser.equals(Storage.get().get(LINKS, key, String.class))) {
                try {
                    return UUID.fromString(key);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    // ---------- rank <-> role ----------

    /** rank name -> role ID, from discord.toml. */
    private static Map<String, String> roleMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String s : DiscordConfig.RANK_ROLES.get()) {
            int eq = s.indexOf('=');
            if (eq > 0) m.put(s.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT), s.substring(eq + 1).trim());
        }
        return m;
    }

    /** Give a linked player the role for their rank and take away the other rank roles. */
    public static void syncRoles(UUID player) {
        if (rest == null || !Features.on("discord", "role_sync")) return;
        String user = linkedUser(player);
        if (user == null || guild().isEmpty()) return;
        String rank = Ranks.rankOf(player);
        Map<String, String> map = roleMap();
        String want = map.get(rank);
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getValue().equals(want)) continue;
            rest.removeRole(guild(), user, e.getValue());
        }
        if (want != null) rest.addRole(guild(), user, want);
    }

    /** Discord role -> rank: the highest-priority rank whose role the member has. */
    private static void rankFromRoles(String user, JsonArray roles) {
        if (!Features.on("discord", "role_to_rank") || roles == null) return;
        UUID player = playerFor(user);
        if (player == null) return;
        Map<String, String> map = roleMap();
        String best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (JsonElement r : roles) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (!e.getValue().equals(r.getAsString())) continue;
                Rank rank = Ranks.rank(e.getKey());
                if (rank != null && rank.priority > bestPriority) {
                    bestPriority = rank.priority;
                    best = e.getKey();
                }
            }
        }
        if (best == null) return;
        String target = best;
        server.execute(() -> {
            String old = Ranks.user(player).rank;
            if (target.equals(old)) return;
            String name = Ranks.user(player).name;
            Ranks.setRank(player, name, target);
            RankUps.changed(server, player, name, old == null || old.isEmpty() ? Ranks.defaultRankName() : old, target);
        });
    }

    // ---------- events from Discord (gateway thread) ----------

    private static void onGatewayEvent(String type, JsonObject d) {
        MinecraftServer srv = server;
        if (srv == null) return;
        if ("GUILD_MEMBER_UPDATE".equals(type)) {
            JsonObject user = d.getAsJsonObject("user");
            if (user != null) rankFromRoles(user.get("id").getAsString(), d.getAsJsonArray("roles"));
            return;
        }
        if (!"MESSAGE_CREATE".equals(type)) return;
        JsonObject author = d.getAsJsonObject("author");
        if (author == null || (author.has("bot") && author.get("bot").getAsBoolean())) return;
        if (d.has("webhook_id")) return;
        String userId = author.get("id").getAsString();
        String channel = d.get("channel_id").getAsString();
        String content = d.has("content") ? d.get("content").getAsString().trim() : "";
        if (content.isEmpty()) return;

        if (content.toLowerCase(java.util.Locale.ROOT).startsWith("!link ") && Features.on("discord", "link")) {
            String code = content.substring(6).trim();
            srv.execute(() -> finishLink(userId, code, channel));
            return;
        }

        String shownName = discordName(d, author);
        if (channel.equals(DiscordConfig.CHAT_CHANNEL.get().trim()) && Features.on("discord.chat_bridge", "to_game")) {
            srv.execute(() -> toGame(userId, shownName, content));
        } else if (channel.equals(staffChatChannel()) && Features.on("discord.chat_bridge", "staff_chat")) {
            srv.execute(() -> toStaff(userId, shownName, content));
        }
    }

    private static void finishLink(String userId, String code, String channel) {
        Pending p = CODES.remove(code);
        if (p == null || p.expires() < System.currentTimeMillis()) {
            rest.send(channel, "That code is wrong or has run out. Use /link in game to get a new one.");
            return;
        }
        UUID old = playerFor(userId);
        if (old != null) Storage.get().remove(LINKS, old.toString());
        Storage.get().put(LINKS, p.player().toString(), userId);
        rest.send(channel, "Linked to **" + p.name() + "**.");
        ServerPlayer online = server.getPlayerList().getPlayer(p.player());
        if (online != null) online.sendSystemMessage(Text.color("&aYour Discord account is now linked."));
        syncRoles(p.player());
        if (Features.on("discord", "role_to_rank")) {
            DiscordRest r = rest;
            new Thread(() -> rankFromRoles(userId, r.memberRoles(guild(), userId)), "Nexus-Discord-Roles").start();
        }
    }

    private static void toGame(String userId, String shownName, String content) {
        UUID player = playerFor(userId);
        String name = shownName;
        if (player != null) {
            if (Punish.of(player).mute != null && Punish.of(player).mute.active()) return; // muted in game = muted here too
            String n = Ranks.user(player).name;
            if (n != null && !n.isEmpty()) name = n;
            String nick = Nick.get(player);
            if (nick != null) name = "~" + nick;
        }
        name = safe(name);
        String text = content.replace("§", ""); // § codes would bypass the filter and color the text
        if (Features.on("chat", "filter")) {
            ChatFilter.Result r = ChatFilter.check(text);
            if (r.kind() == ChatFilter.Kind.BLOCK) return;
            text = r.text();
        }
        text = safe(text); // no color codes from Discord
        Component line = Text.color(DiscordConfig.TO_GAME.get().replace("{user}", name).replace("{message}", text));
        server.sendSystemMessage(line);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(line);
    }

    private static void toStaff(String userId, String shownName, String content) {
        UUID player = playerFor(userId);
        String name = shownName;
        if (player != null) {
            String n = Ranks.user(player).name;
            if (n != null && !n.isEmpty()) name = n;
        }
        StaffChat.fromDiscord(server, Text.color(DiscordConfig.STAFF_TO_GAME.get().replace("{user}", safe(name))
                .replace("{message}", safe(content))));
    }

    // ---------- helpers ----------

    private static String guild() {
        return DiscordConfig.GUILD.get().trim();
    }

    private static String staffChatChannel() {
        String c = DiscordConfig.STAFF_CHAT_CHANNEL.get().trim();
        return c.isEmpty() ? DiscordConfig.STAFF_CHANNEL.get().trim() : c;
    }

    private static String discordName(JsonObject d, JsonObject author) {
        JsonObject member = d.getAsJsonObject("member");
        if (member != null && member.has("nick") && !member.get("nick").isJsonNull()) return member.get("nick").getAsString();
        if (author.has("global_name") && !author.get("global_name").isJsonNull()) return author.get("global_name").getAsString();
        return author.get("username").getAsString();
    }

    /** Fill {player} {rank} {title} {message} with plain text (no color codes). */
    private static String fill(String fmt, ServerPlayer p, String message) {
        String s = fmt.replace("{message}", "\u0000MSG\u0000");
        s = plain(Placeholders.apply(s.replace("{player}", "{name}"), p));
        s = s.replace("\u0000MSG\u0000", markdown(plain(message)));
        s = s.replaceAll("\\s{2,}", " ").trim();
        return s;
    }

    /** Text from Discord shown in game: no § codes, & shown as plain text. */
    private static String safe(String s) {
        return s == null ? "" : s.replace("§", "").replace("&", "&\u200B");
    }

    /** Player text shown in Discord: markdown (bold, links, headers...) shows as plain text. */
    private static String markdown(String s) {
        return s.replaceAll("([\\\\*_~`|>#\\[\\]()-])", "\\\\$1");
    }

    private static String plain(String s) {
        return s == null ? "" : Text.color(s).getString();
    }

    private static boolean isModeration(String action) {
        return PAST.containsKey(action.toLowerCase(java.util.Locale.ROOT));
    }

    private static final Map<String, String> PAST = new HashMap<>(Map.ofEntries(
            Map.entry("warn", "warned"), Map.entry("mute", "muted"), Map.entry("unmute", "unmuted"),
            Map.entry("kick", "kicked"), Map.entry("ban", "banned"), Map.entry("tempban", "temp-banned"),
            Map.entry("unban", "unbanned"), Map.entry("ban ip", "IP-banned"), Map.entry("unban ip", "IP-unbanned"),
            Map.entry("jail", "jailed"), Map.entry("unjail", "released"), Map.entry("freeze", "froze"),
            Map.entry("unfreeze", "unfroze")));

    private static String past(String action) {
        return PAST.getOrDefault(action.toLowerCase(java.util.Locale.ROOT), action);
    }
}
