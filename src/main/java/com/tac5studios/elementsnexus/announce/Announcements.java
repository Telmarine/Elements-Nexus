package com.tac5studios.elementsnexus.announce;

import com.tac5studios.elementsnexus.broadcast.Broadcast;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.TomlFile;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import com.tac5studios.elementsnexus.util.Time;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Timed messages and the restart countdown. */
public final class Announcements {

    private static final TomlFile FILE = new TomlFile("announcements.toml");

    // timed
    private static long nextTimedAt = 0;
    private static int nextIndex = 0;

    // restart countdown
    private static long restartAt = 0;          // millis, 0 = no countdown
    private static final Set<Long> FIRED = new HashSet<>();
    private static final List<Long> CHAT_AT = new ArrayList<>();
    private static final List<Long> SCREEN_AT = new ArrayList<>();

    private Announcements() {}

    public static void load() {
        FILE.load();
        CHAT_AT.clear();
        SCREEN_AT.clear();
        for (String s : FILE.strings("restart.chat_at")) { Long v = Time.parse(s); if (v != null) CHAT_AT.add(v); }
        for (String s : FILE.strings("restart.screen_at")) { Long v = Time.parse(s); if (v != null) SCREEN_AT.add(v); }
        nextTimedAt = System.currentTimeMillis() + intervalMillis();
    }

    private static long intervalMillis() {
        return Math.max(1, FILE.num("timed.interval_minutes", 10)) * 60_000L;
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (Features.on("announcements", "timed") && now >= nextTimedAt) {
            nextTimedAt = now + intervalMillis();
            sendTimed(server);
        }
        if (restartAt > 0) countdown(server, now);
    }

    // ---------- timed ----------

    private static void sendTimed(MinecraftServer server) {
        List<String> msgs = FILE.strings("timed.messages");
        if (msgs.isEmpty()) return;
        if (server.getPlayerList().getPlayerCount() < FILE.num("timed.min_players", 1)) return;
        String msg;
        if (FILE.bool("timed.random_order", false)) {
            msg = msgs.get(ThreadLocalRandom.current().nextInt(msgs.size()));
        } else {
            if (nextIndex >= msgs.size()) nextIndex = 0;
            msg = msgs.get(nextIndex++);
        }
        Component line = Text.color(Fancy.apply(FILE.str("timed.prefix", "&b&l[Info] &r") + msg, 0));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(line);
    }

    // ---------- restart countdown ----------

    public static boolean restartRunning() {
        return restartAt > 0;
    }

    public static long secondsLeft() {
        return restartAt == 0 ? 0 : Math.max(0, (restartAt - System.currentTimeMillis() + 999) / 1000);
    }

    public static void startRestart(MinecraftServer server, long seconds) {
        restartAt = System.currentTimeMillis() + seconds * 1000;
        FIRED.clear();
        // Skip marks longer than this countdown, and warn right away.
        for (Long m : CHAT_AT) if (m >= seconds) FIRED.add(m);
        for (Long m : SCREEN_AT) if (m >= seconds) FIRED.add(-m);
        if (Features.on("announcements", "restart_warnings")) warn(server, seconds, true, SCREEN_AT.contains(seconds));
    }

    public static void cancelRestart(MinecraftServer server) {
        restartAt = 0;
        FIRED.clear();
        String msg = FILE.str("restart.cancel_message", "&aThe server restart was cancelled.");
        if (!msg.isEmpty()) sendAll(server, Text.color(Fancy.apply(msg, 0)));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
        }
    }

    private static void countdown(MinecraftServer server, long now) {
        long left = Math.max(0, (restartAt - now + 999) / 1000);
        if (left == 0) {
            restartAt = 0;
            String msg = FILE.str("restart.now_message", "");
            if (!msg.isEmpty()) sendAll(server, Text.color(Fancy.apply(msg, 0)));
            return;
        }
        if (!Features.on("announcements", "restart_warnings")) return;
        // A mark fires once the time left reaches it (also if a laggy tick skipped past it).
        boolean chat = false, screen = false;
        for (Long m : CHAT_AT) if (left <= m && FIRED.add(m)) chat = true;
        for (Long m : SCREEN_AT) if (left <= m && FIRED.add(-m)) screen = true;
        if (chat || screen) warn(server, left, chat, screen);
    }

    private static void warn(MinecraftServer server, long left, boolean chat, boolean screen) {
        String time = words(left);
        if (chat) {
            sendAll(server, Text.color(Fancy.apply(
                    FILE.str("restart.chat_message", "&c&l⚠ &eServer restarting in &f{time}&e.").replace("{time}", time), 0)));
        }
        if (screen) {
            Component t = Text.color(Fancy.apply(FILE.str("restart.screen_title", "&c&lServer Restart").replace("{time}", time), 0));
            Component s = Text.color(Fancy.apply(FILE.str("restart.screen_subtitle", "&ein {time}").replace("{time}", time), 0));
            boolean shortOne = left <= 10;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(shortOne ? 0 : 10, shortOne ? 25 : 60, shortOne ? 5 : 20));
                p.connection.send(new ClientboundSetSubtitleTextPacket(s));
                p.connection.send(new ClientboundSetTitleTextPacket(t));
            }
        }
        if (FILE.bool("restart.sound", true)) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) Broadcast.sound(p);
        }
    }

    private static void sendAll(MinecraftServer server, Component line) {
        server.sendSystemMessage(line);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(line);
    }

    /** 1800 -> "30 minutes", 90 -> "1 minute 30 seconds", 1 -> "1 second". */
    public static String words(long seconds) {
        long h = seconds / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        List<String> parts = new ArrayList<>();
        if (h > 0) parts.add(h + (h == 1 ? " hour" : " hours"));
        if (m > 0) parts.add(m + (m == 1 ? " minute" : " minutes"));
        if (s > 0 || parts.isEmpty()) parts.add(s + (s == 1 ? " second" : " seconds"));
        return String.join(" ", parts);
    }
}
