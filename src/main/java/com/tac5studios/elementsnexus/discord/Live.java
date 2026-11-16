package com.tac5studios.elementsnexus.discord;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.config.DiscordConfig;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Go-live announcements: in game and in the public go-live Discord channel.
 * Two ways in: a stream alert bot's post in the hidden feed channel, or a creator running /live.
 * Only creators with a file in config/elements_nexus/creators/ are ever announced (one file per creator).
 */
public final class Live {

    /** One creator file. links are stored cleaned ("twitch.tv/name"). */
    public record Creator(String file, String player, List<String> links, boolean enabled) {}

    /** lower-case player name -> creator */
    private static final Map<String, Creator> CREATORS = new ConcurrentHashMap<>();
    /** lower-case player name -> last announcement time, so one stream isn't announced twice. */
    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

    private Live() {}

    public static boolean on() {
        return Features.on("discord", "live_alerts");
    }

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(ElementsNexus.MOD_ID).resolve("creators");
    }

    // ---------- creator files ----------

    /** Read every creator file. Called at server start and by /nexus reload. Returns how many were read. */
    public static int load() {
        CREATORS.clear();
        if (!on()) return 0;
        Path dir = folder();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ElementsNexus.LOGGER.error("[Nexus] Could not make {}", dir, e);
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".toml")).toList()) {
                try (CommentedFileConfig cfg = CommentedFileConfig.builder(p).build()) {
                    cfg.load();
                    Object name = cfg.get("player");
                    if (!(name instanceof String player) || player.isBlank()) {
                        ElementsNexus.LOGGER.warn("[Nexus] Creator file {} has no player name. Skipped.", p.getFileName());
                        continue;
                    }
                    List<String> links = new ArrayList<>();
                    if (cfg.get("links") instanceof List<?> l) {
                        for (Object o : l) if (o instanceof String s && !s.isBlank()) links.add(cleanLink(s));
                    }
                    boolean enabled = !(cfg.get("enabled") instanceof Boolean b) || b;
                    CREATORS.put(player.trim().toLowerCase(Locale.ROOT),
                            new Creator(p.getFileName().toString(), player.trim(), List.copyOf(links), enabled));
                } catch (Exception e) {
                    ElementsNexus.LOGGER.error("[Nexus] Could not read creator file {}", p.getFileName(), e);
                }
            }
        } catch (IOException e) {
            ElementsNexus.LOGGER.error("[Nexus] Could not list {}", dir, e);
        }
        return CREATORS.size();
    }

    public static List<Creator> creators() {
        return new ArrayList<>(CREATORS.values());
    }

    public static Creator creator(String player) {
        return CREATORS.get(player.toLowerCase(Locale.ROOT));
    }

    /** Write (or rewrite) a creator's file and keep it in memory. */
    public static void save(String player, List<String> links, boolean enabled) throws IOException {
        Creator old = creator(player);
        String file = old != null ? old.file() : safeFileName(player) + ".toml";
        List<String> clean = new ArrayList<>();
        for (String l : links) {
            String c = cleanLink(l);
            if (!c.isEmpty() && !clean.contains(c)) clean.add(c);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Go-live creator. Edit this file, then run /nexus reload.\n");
        sb.append("# player: their Minecraft name.\n");
        sb.append("# links: their stream channels, e.g. \"twitch.tv/name\", \"youtube.com/@name\", \"kick.com/name\".\n");
        sb.append("# enabled: false stops all announcements for this creator without deleting the file.\n\n");
        sb.append("player = \"").append(escape(player)).append("\"\n");
        sb.append("links = [");
        for (int i = 0; i < clean.size(); i++) sb.append(i == 0 ? "" : ", ").append('"').append(escape(clean.get(i))).append('"');
        sb.append("]\n");
        sb.append("enabled = ").append(enabled).append('\n');
        Files.createDirectories(folder());
        Files.writeString(folder().resolve(file), sb.toString(), StandardCharsets.UTF_8);
        CREATORS.put(player.toLowerCase(Locale.ROOT), new Creator(file, player, List.copyOf(clean), enabled));
    }

    /** Delete a creator's file. Returns the name from the file, or null when there was none. */
    public static String delete(String player) throws IOException {
        Creator c = CREATORS.remove(player.toLowerCase(Locale.ROOT));
        if (c == null) return null;
        Files.deleteIfExists(folder().resolve(c.file()));
        LAST.remove(player.toLowerCase(Locale.ROOT));
        return c.player();
    }

    /** Drops {message} and the space next to it when there is no message. */
    private static String noMessage(String format, String message) {
        if (!message.isBlank()) return format;
        return format.replace("{message} ", "").replace(" {message}", "").replace("{message}", "");
    }

    /** {@code "https://www.twitch.tv/name/"} becomes {@code "twitch.tv/name"} (how links are stored and matched). */
    public static String cleanLink(String link) {
        String s = link.trim().replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "");
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static boolean validLink(String link) {
        String s = cleanLink(link);
        return s.contains(".") && s.contains("/") && !s.contains(" ") && !s.contains("\"") && s.length() <= 100;
    }

    private static String safeFileName(String player) {
        return player.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ---------- announcing ----------

    /** Server thread: a stream alert from the feed channel. allLower = everything in the post, lower case. */
    static void fromFeed(MinecraftServer server, String allLower, String title, String postLink) {
        for (Creator c : CREATORS.values()) {
            if (!c.enabled()) continue;
            for (String link : c.links()) {
                if (!mentions(allLower, link)) continue;
                if (DiscordConfig.LIVE_REQUIRE_ONLINE.get() && server.getPlayerList().getPlayerByName(c.player()) == null) return;
                // only reuse the post's link when it is this creator's stream (a title can hold any link)
                announce(server, c, title, mentions(postLink.toLowerCase(Locale.ROOT), link) ? postLink : "https://" + link);
                return;
            }
        }
    }

    /** True when text holds the link as a whole channel ("twitch.tv/bob" doesn't match "twitch.tv/bobby"). */
    private static boolean mentions(String textLower, String link) {
        String l = link.toLowerCase(Locale.ROOT);
        int i = textLower.indexOf(l);
        while (i >= 0) {
            int end = i + l.length();
            boolean startOk = i == 0 || !Character.isLetterOrDigit(textLower.charAt(i - 1)) && textLower.charAt(i - 1) != '.'
                    || textLower.startsWith("www.", i - 4) || textLower.startsWith("m.", i - 2);
            boolean endOk = end == textLower.length() || !(Character.isLetterOrDigit(textLower.charAt(end)) || textLower.charAt(end) == '_' || textLower.charAt(end) == '-');
            if (startOk && endOk) return true;
            i = textLower.indexOf(l, i + 1);
        }
        return false;
    }

    /** /live: the creator announces their own stream with their first link. False when it's still on cooldown. */
    public static boolean fromCommand(MinecraftServer server, Creator c) {
        return announce(server, c, "", "https://" + c.links().getFirst());
    }

    /** Minutes left before this creator can be announced again (0 = now). */
    public static long cooldownLeft(String player) {
        Long last = LAST.get(player.toLowerCase(Locale.ROOT));
        if (last == null) return 0;
        long left = last + DiscordConfig.LIVE_COOLDOWN.get() * 60_000L - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 59_999) / 60_000;
    }

    private static boolean announce(MinecraftServer server, Creator c, String message, String link) {
        if (cooldownLeft(c.player()) > 0) return false;
        LAST.put(c.player().toLowerCase(Locale.ROOT), System.currentTimeMillis());
        String shown = cleanLink(link);
        String game = noMessage(DiscordConfig.LIVE_TO_GAME.get(), message)
                .replace("{player}", Discord.safe(c.player()))
                .replace("{message}", Discord.safe(message))
                .replace("{link}", "[" + Discord.safe(shown) + "](" + link.replace(")", "%29") + ")")
                .replaceAll("\\s{2,}", " ").trim();
        Component line = Text.color(game);
        server.sendSystemMessage(line);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(line);
        String discord = noMessage(DiscordConfig.LIVE_TO_DISCORD.get(), message)
                .replace("{player}", Discord.markdown(c.player()))
                .replace("{message}", Discord.markdown(message))
                .replace("{link}", link)
                .replace("\\n", "\n")
                .replaceAll(" {2,}", " ").trim();
        Discord.postLive(discord);
        return true;
    }
}
