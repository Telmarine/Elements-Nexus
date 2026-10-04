package com.tac5studios.elementsnexus.moderation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.config.Features;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * world/elements_nexus/staff_log.jsonl - one line per staff action.
 * Only ever added to; the mod never edits lines. When it gets too big it is packed (gzip)
 * into world/elements_nexus/staff_log_archive/ and a new file starts.
 */
public final class StaffLog {

    private StaffLog() {}

    public static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("elements_nexus").resolve("staff_log.jsonl");
    }

    /** Add a line. target/reason/duration may be null. */
    public static void add(CommandSourceStack staff, String action, UUID targetId, String target, String reason, String duration) {
        com.tac5studios.elementsnexus.discord.Discord.staffAction(
                staff.getPlayer() == null ? com.tac5studios.elementsnexus.util.ConsoleName.plain() : staff.getPlayer().getGameProfile().getName(),
                action, target, reason, duration);
        if (!Features.on("staff_log")) return;
        JsonObject o = new JsonObject();
        o.addProperty("time", Instant.now().toString());
        o.addProperty("staff", staff.getPlayer() == null ? "Console" : staff.getPlayer().getGameProfile().getName());
        if (staff.getPlayer() != null) o.addProperty("staff_uuid", staff.getPlayer().getUUID().toString());
        o.addProperty("action", action);
        if (target != null) o.addProperty("target", target);
        if (targetId != null) o.addProperty("target_uuid", targetId.toString());
        if (reason != null && !reason.isEmpty()) o.addProperty("reason", reason);
        if (duration != null) o.addProperty("duration", duration);
        Path f = file(staff.getServer());
        try {
            Files.createDirectories(f.getParent());
            rollOver(f);
            Files.writeString(f, o + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ElementsNexus.LOGGER.error("[Nexus] Could not write the staff log.", e);
        }
    }

    private static Path archiveDir(Path log) {
        return log.getParent().resolve("staff_log_archive");
    }

    /** Pack the log into the archive folder once it is too big. */
    private static void rollOver(Path f) {
        try {
            if (!Files.exists(f)) return;
            long max = com.tac5studios.elementsnexus.config.ModerationConfig.LOG_MAX_MB.get() * 1024L * 1024L;
            if (Files.size(f) < max) return;
            Path dir = archiveDir(f);
            Files.createDirectories(dir);
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path gz = dir.resolve("staff_log_" + stamp + ".jsonl.gz");
            try (var in = Files.newInputStream(f);
                 var out = new java.util.zip.GZIPOutputStream(Files.newOutputStream(gz))) {
                in.transferTo(out);
            }
            Files.delete(f);
            ElementsNexus.LOGGER.info("[Nexus] Staff log packed into {}", gz.getFileName());

            int keep = com.tac5studios.elementsnexus.config.ModerationConfig.LOG_KEEP.get();
            if (keep > 0) {
                List<Path> old = archives(f);
                for (int i = keep; i < old.size(); i++) Files.deleteIfExists(old.get(i));
            }
        } catch (IOException e) {
            ElementsNexus.LOGGER.error("[Nexus] Could not pack the staff log.", e);
        }
    }

    /** Packed logs, newest first. */
    private static List<Path> archives(Path log) throws IOException {
        Path dir = archiveDir(log);
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (var s = Files.list(dir)) {
            return new ArrayList<>(s.filter(p -> p.getFileName().toString().endsWith(".jsonl.gz"))
                    .sorted(java.util.Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList());
        }
    }

    /** The newest lines about one player (newest first). Looks in packed logs too. */
    public static List<JsonObject> about(MinecraftServer server, UUID target, int max) {
        List<JsonObject> out = new ArrayList<>();
        Path f = file(server);
        String id = target.toString();
        try {
            if (Files.exists(f)) collect(Files.readAllLines(f, StandardCharsets.UTF_8), id, max, out);
            for (Path gz : archives(f)) {
                if (out.size() >= max) break;
                try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(gz))) {
                    collect(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList(), id, max, out);
                }
            }
        } catch (IOException e) {
            ElementsNexus.LOGGER.error("[Nexus] Could not read the staff log.", e);
        }
        return out;
    }

    private static void collect(List<String> lines, String id, int max, List<JsonObject> out) {
        for (int i = lines.size() - 1; i >= 0 && out.size() < max; i--) {
            String l = lines.get(i);
            if (!l.contains(id)) continue;
            try {
                JsonObject o = JsonParser.parseString(l).getAsJsonObject();
                if (o.has("target_uuid") && id.equals(o.get("target_uuid").getAsString())) out.add(o);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
