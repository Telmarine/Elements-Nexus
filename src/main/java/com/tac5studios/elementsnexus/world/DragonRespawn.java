package com.tac5studios.elementsnexus.world;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.config.DragonConfig;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.StorageConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Respawns the Ender Dragon a set time after it dies (dragon.toml [respawn] minutes).
 * Uses the vanilla respawn: places the 4 End Crystals on the exit portal and starts the fight's own
 * respawn, so the pillars rebuild, a new gateway opens and the fight runs as normal.
 * Switch: features.toml [dragon] auto_respawn.
 */
public final class DragonRespawn {

    /** Epoch millis when the respawn is due. 0 = no respawn pending. */
    private static long dueAt = -1; // -1 = not loaded yet
    private static int tick;
    /** When our respawn started (epoch millis), 0 = none. The fight still reports a dead dragon until the new one spawns. */
    private static long respawnStartedAt;
    /** How long to wait for the new dragon before treating the respawn as failed and starting the timer again. */
    private static final long RESPAWN_GRACE = 5 * 60_000L;

    private DragonRespawn() {}

    public static void tick(MinecraftServer server) {
        if (++tick % 20 != 0) return; // once a second
        if (!Features.on("dragon", "auto_respawn")) return;

        ServerLevel end = server.getLevel(Level.END);
        if (end == null) return;
        EndDragonFight fight = end.getDragonFight();
        if (fight == null) return;

        if (dueAt < 0) dueAt = load();
        EndDragonFight.Data data = fight.saveData();

        if (!data.dragonKilled()) {
            // Dragon is alive or already respawning - nothing pending.
            if (dueAt != 0) save(0);
            respawnStartedAt = 0;
            return;
        }

        long now = System.currentTimeMillis();
        if (respawnStartedAt > 0) {
            if (now - respawnStartedAt < RESPAWN_GRACE) return; // new dragon still on its way
            respawnStartedAt = 0;
            ElementsNexus.LOGGER.warn("[Nexus] The Ender Dragon respawn did not finish. Starting the timer again.");
        }
        if (dueAt == 0) {
            // Dragon just died (or died before this feature was on): start the timer.
            save(now + DragonConfig.RESPAWN_MINUTES.get() * 60_000L);
            ElementsNexus.LOGGER.info("[Nexus] Ender Dragon is dead. It respawns in {} minute(s).", DragonConfig.RESPAWN_MINUTES.get());
            return;
        }
        if (now < dueAt) return;

        // Time is up. Wait for a player in The End so the arena is loaded and the respawn can tick.
        if (end.players().isEmpty()) return;
        Optional<BlockPos> portal = data.exitPortalLocation();
        if (portal.isEmpty()) return;
        BlockPos base = portal.get().above(1);
        if (!end.hasChunkAt(base)) return;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos spot = base.relative(dir, 2);
            if (end.getEntitiesOfClass(EndCrystal.class, new AABB(spot)).isEmpty()) {
                EndCrystal crystal = new EndCrystal(end, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                crystal.setShowBottom(false);
                end.addFreshEntity(crystal);
            }
        }
        fight.tryRespawn();

        save(0);
        respawnStartedAt = now;
        ElementsNexus.LOGGER.info("[Nexus] Ender Dragon respawn started.");
        String msg = DragonConfig.RESPAWN_MESSAGE.get();
        if (msg != null && !msg.isBlank()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(msg.replace('&', '§')), false);
        }
    }

    // ---- timer file: <server>/<storage folder>/dragon_respawn.txt ----

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve(StorageConfig.FOLDER.get()).resolve("dragon_respawn.txt").normalize();
    }

    private static long load() {
        try {
            Path f = file();
            if (Files.exists(f)) return Long.parseLong(Files.readString(f).trim());
        } catch (IOException | NumberFormatException ex) {
            ElementsNexus.LOGGER.warn("[Nexus] Could not read the dragon respawn timer, starting fresh.", ex);
        }
        return 0;
    }

    private static void save(long value) {
        dueAt = value;
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, Long.toString(value));
        } catch (IOException ex) {
            ElementsNexus.LOGGER.warn("[Nexus] Could not save the dragon respawn timer.", ex);
        }
    }
}
