package com.tac5studios.elementsnexus.world;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.chat.StaffChat;
import com.tac5studios.elementsnexus.config.DragonConfig;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.StorageConfig;
import com.tac5studios.elementsnexus.discord.Discord;
import com.tac5studios.elementsnexus.mixin.EndDragonFightAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Respawns the Ender Dragon a set time after it dies (dragon.toml).
 * Finds the exit portal (auto, vanilla or fixed), puts End Crystals on the configured spots and starts
 * the vanilla respawn with those crystals, so the pillars rebuild, a new gateway opens and the fight runs as normal.
 * Starting it with our own crystals means it no longer depends on where the dragon fight thinks the portal is.
 * Switch: features.toml [dragon] auto_respawn.
 */
public final class DragonRespawn {

    /** Epoch millis when the respawn is due. 0 = no respawn pending. -1 = not loaded yet. */
    private static long dueAt = -1;
    private static int tick;
    /** When crystals were placed and the respawn was started (epoch millis), 0 = none. */
    private static long startedAt;
    /** Tries since the timer ran out. */
    private static int tries;
    /** Spots shown with particles by /nexus dragon spots, until this time. */
    private static long showSpotsUntil;
    private static List<BlockPos> shownSpots = List.of();

    private DragonRespawn() {}

    /** Where the portal is and where the crystals go. */
    public record Target(BlockPos center, List<BlockPos> spots, String how) {}

    /** /nexus reload: read the timer file again. */
    public static void reload() {
        dueAt = -1;
    }

    public static void tick(MinecraftServer server) {
        if (++tick % 10 == 0) showSpots(server);
        if (tick % 20 != 0) return; // once a second
        if (!Features.on("dragon", "auto_respawn")) return;

        ServerLevel end = server.getLevel(Level.END);
        if (end == null) return;
        EndDragonFight fight = end.getDragonFight();
        if (fight == null) return;

        if (dueAt < 0) dueAt = load();
        EndDragonFight.Data data = fight.saveData();
        long now = System.currentTimeMillis();

        if (!data.dragonKilled()) {
            // Dragon is alive - nothing pending.
            if (dueAt != 0) save(0);
            startedAt = 0;
            tries = 0;
            return;
        }
        if (respawning(fight)) {
            // The vanilla respawn is running (pillars, crystals, new dragon).
            startedAt = 0;
            tries = 0;
            return;
        }

        if (startedAt > 0) {
            if (now - startedAt < DragonConfig.RETRY_SECONDS.get() * 1000L) return; // give it time to start
            startedAt = 0;
            if (tries >= DragonConfig.MAX_RETRIES.get()) {
                failed(server, end, fight);
                return;
            }
            ElementsNexus.LOGGER.warn("[Nexus] The Ender Dragon respawn did not start. Trying again ({}/{}).",
                    tries + 1, DragonConfig.MAX_RETRIES.get());
        }

        if (dueAt == 0) {
            // Dragon just died (or died before this feature was on): start the timer.
            save(now + DragonConfig.RESPAWN_MINUTES.get() * 60_000L);
            ElementsNexus.LOGGER.info("[Nexus] Ender Dragon is dead. It respawns in {} minute(s).", DragonConfig.RESPAWN_MINUTES.get());
            return;
        }
        if (now < dueAt) return;

        // Time is up. Wait for a player near the portal so the arena is loaded and the respawn can tick.
        if (end.players().isEmpty()) return;
        Target t = target(end, fight);
        if (t == null) return;
        double r = DragonConfig.PLAYER_RADIUS.get();
        if (end.players().stream().noneMatch(p -> p.blockPosition().closerThan(t.center(), r))) return;
        if (!end.isLoaded(t.center())) return;

        start(end, fight, t);
        tries++;
        startedAt = now;
        if (tries == 1) {
            String msg = DragonConfig.RESPAWN_MESSAGE.get();
            if (!msg.isBlank()) {
                server.getPlayerList().broadcastSystemMessage(Component.literal(msg.replace('&', '§')), false);
            }
        }
    }

    /** Place the crystals and start the vanilla respawn. */
    private static void start(ServerLevel end, EndDragonFight fight, Target t) {
        List<EndCrystal> crystals = placeCrystals(end, t);
        EndDragonFightAccessor acc = (EndDragonFightAccessor) fight;
        if (DragonConfig.FIX_PORTAL_LOCATION.get() && !"vanilla".equals(DragonConfig.mode())
                && !t.center().equals(acc.nexus$portalLocation())) {
            acc.nexus$setPortalLocation(t.center());
            ElementsNexus.LOGGER.info("[Nexus] Saved the exit portal location as {} {} {}.",
                    t.center().getX(), t.center().getY(), t.center().getZ());
        }
        acc.nexus$respawnDragon(crystals);
        ElementsNexus.LOGGER.info("[Nexus] Ender Dragon respawn started ({} crystals, portal {} {} {}, {}).",
                crystals.size(), t.center().getX(), t.center().getY(), t.center().getZ(), t.how());
    }

    /** Out of tries: tell staff and start the timer again. */
    private static void failed(MinecraftServer server, ServerLevel end, EndDragonFight fight) {
        Target t = target(end, fight);
        BlockPos c = t == null ? BlockPos.ZERO : t.center();
        ElementsNexus.LOGGER.warn("[Nexus] The Ender Dragon respawn failed {} times (portal {} {} {}). Starting the timer again.",
                tries, c.getX(), c.getY(), c.getZ());
        String msg = DragonConfig.FAILED_MESSAGE.get();
        if (!msg.isBlank()) {
            String filled = msg.replace("{x}", String.valueOf(c.getX())).replace("{y}", String.valueOf(c.getY()))
                    .replace("{z}", String.valueOf(c.getZ())).replace("{tries}", String.valueOf(tries));
            StaffChat.alert(server, filled);
            Discord.staffNotice(filled.replaceAll("&#[0-9a-fA-F]{6}", "").replaceAll("&[0-9a-fk-orA-FK-OR]", ""));
        }
        tries = 0;
        save(System.currentTimeMillis() + DragonConfig.RESPAWN_MINUTES.get() * 60_000L);
    }

    /** True while the vanilla respawn animation is running. */
    private static boolean respawning(EndDragonFight fight) {
        return ((EndDragonFightAccessor) fight).nexus$respawnStage() != null;
    }

    // ---------- commands (/nexus dragon ...) ----------

    /** /nexus dragon respawn: place crystals and start now. Null = started, otherwise why not. */
    public static String respawnNow(MinecraftServer server) {
        ServerLevel end = server.getLevel(Level.END);
        EndDragonFight fight = end == null ? null : end.getDragonFight();
        if (fight == null) return "There is no dragon fight in The End.";
        EndDragonFight.Data data = fight.saveData();
        if (!data.dragonKilled()) return "The Ender Dragon is alive.";
        if (respawning(fight)) return "The Ender Dragon is already respawning.";
        Target t = target(end, fight);
        if (t == null) return "The exit portal or the crystal spots weren't found. Check dragon.toml [portal] and [crystals], or /nexus dragon status.";
        if (!end.isLoaded(t.center())) return "The exit portal isn't loaded. A player has to be near it.";
        start(end, fight, t);
        tries = 1;
        startedAt = System.currentTimeMillis();
        return null;
    }

    /** /nexus dragon status lines. */
    public static List<String> status(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        ServerLevel end = server.getLevel(Level.END);
        EndDragonFight fight = end == null ? null : end.getDragonFight();
        if (fight == null) {
            out.add("&7There is no dragon fight in The End.");
            return out;
        }
        if (dueAt < 0) dueAt = load();
        EndDragonFight.Data data = fight.saveData();
        String state;
        if (!data.dragonKilled()) state = "&aalive";
        else if (respawning(fight)) state = "&erespawning now";
        else if (dueAt == 0) state = "&cdead &7(timer starts within a second)";
        else {
            long left = dueAt - System.currentTimeMillis();
            state = left > 0 ? "&cdead &7- respawns in " + (left / 60_000) + "m " + (left / 1000 % 60) + "s"
                    : "&cdead &7- due now, waiting for a player within " + DragonConfig.PLAYER_RADIUS.get() + " blocks of the portal";
        }
        out.add("&6Ender Dragon: " + state + (tries > 0 ? " &7(try " + tries + "/" + DragonConfig.MAX_RETRIES.get() + ")" : ""));
        out.add("&7Switch: auto_respawn " + (Features.on("dragon", "auto_respawn") ? "&aon" : "&coff"));
        out.add("&7Portal mode: &f" + DragonConfig.mode() + " &7| crystals: &f"
                + ("vanilla".equals(DragonConfig.mode()) ? "vanilla" : DragonConfig.placement()));
        BlockPos saved = ((EndDragonFightAccessor) fight).nexus$portalLocation();
        out.add("&7Saved portal location: &f" + (saved == null ? "none" : saved.getX() + " " + saved.getY() + " " + saved.getZ()));
        Target t = target(end, fight);
        if (t == null) {
            out.add("&cThe exit portal or the crystal spots weren't found (is the portal area loaded?).");
            return out;
        }
        out.add("&7Portal used: &f" + t.center().getX() + " " + t.center().getY() + " " + t.center().getZ() + " &7(" + t.how() + ")");
        for (BlockPos s : t.spots()) {
            boolean has = !end.getEntitiesOfClass(EndCrystal.class, new AABB(s)).isEmpty();
            out.add("&7 Spot &f" + s.getX() + " " + s.getY() + " " + s.getZ() + (has ? " &acrystal" : " &8empty"));
        }
        return out;
    }

    /** /nexus dragon spots: particles on the spots for 10 seconds. Null = shown, otherwise why not. */
    public static String startShowingSpots(MinecraftServer server) {
        ServerLevel end = server.getLevel(Level.END);
        EndDragonFight fight = end == null ? null : end.getDragonFight();
        if (fight == null) return "There is no dragon fight in The End.";
        Target t = target(end, fight);
        if (t == null) return "The exit portal or the crystal spots weren't found.";
        shownSpots = t.spots();
        showSpotsUntil = System.currentTimeMillis() + 10_000L;
        return null;
    }

    private static void showSpots(MinecraftServer server) {
        if (showSpotsUntil == 0) return;
        if (System.currentTimeMillis() > showSpotsUntil) {
            showSpotsUntil = 0;
            return;
        }
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) return;
        for (BlockPos s : shownSpots) {
            end.sendParticles(ParticleTypes.HAPPY_VILLAGER, s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, 6, 0.25, 0.5, 0.25, 0);
        }
    }

    // ---------- finding the portal and the spots ----------

    /** The portal centre and crystal spots from dragon.toml, or null if they can't be found. */
    public static Target target(ServerLevel end, EndDragonFight fight) {
        String mode = DragonConfig.mode();
        if ("vanilla".equals(mode)) {
            Optional<BlockPos> portal = fight.saveData().exitPortalLocation();
            if (portal.isEmpty()) return null;
            BlockPos base = portal.get().above(1);
            List<BlockPos> spots = new ArrayList<>();
            for (Direction dir : Direction.Plane.HORIZONTAL) spots.add(base.relative(dir, 2));
            return new Target(portal.get(), spots, "vanilla saved location");
        }

        BlockPos center;
        String how;
        if ("fixed".equals(mode)) {
            center = new BlockPos(DragonConfig.PORTAL_X.get(), DragonConfig.PORTAL_Y.get(), DragonConfig.PORTAL_Z.get());
            how = "fixed in dragon.toml";
        } else {
            center = findPortal(end, ((EndDragonFightAccessor) fight).nexus$origin());
            if (center == null) return null;
            how = "found";
        }

        List<BlockPos> spots = switch (DragonConfig.placement()) {
            case "offset" -> {
                List<BlockPos> l = new ArrayList<>();
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    l.add(center.above(DragonConfig.HEIGHT.get()).relative(dir, DragonConfig.DISTANCE.get()));
                }
                yield l;
            }
            case "list" -> listSpots();
            default -> bedrockSpots(end, center);
        };
        if (spots == null || spots.isEmpty()) return null;
        return new Target(center, spots, how);
    }

    /**
     * Auto mode: the centre bedrock pillar nearest the dragon fight's centre (a stack of 3+ bedrock).
     * Returns the portal centre at the height of the outer bedrock ring (one above the bottom of the stack,
     * which is the bedrock floor under the portal).
     */
    private static BlockPos findPortal(ServerLevel end, BlockPos origin) {
        int minY = Math.max(end.getMinBuildHeight(), DragonConfig.SEARCH_MIN_Y.get());
        int maxY = Math.min(end.getMaxBuildHeight() - 1, DragonConfig.SEARCH_MAX_Y.get());
        int radius = DragonConfig.SEARCH_RADIUS.get();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // only the ring at this distance
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    if (!end.isLoaded(new BlockPos(x, minY, z))) continue;
                    int run = 0;
                    for (int y = maxY; y >= minY; y--) {
                        if (end.getBlockState(new BlockPos(x, y, z)).is(Blocks.BEDROCK)) {
                            run++;
                        } else {
                            if (run >= 3) return new BlockPos(x, y + 2, z); // y + 1 = floor, y + 2 = ring height
                            run = 0;
                        }
                    }
                    if (run >= 3) return new BlockPos(x, minY + 1, z);
                }
            }
        }
        return null;
    }

    /** On top of the outermost open bedrock block on each side, at the ring height (one above or below is also accepted). */
    private static List<BlockPos> bedrockSpots(ServerLevel end, BlockPos center) {
        List<BlockPos> spots = new ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos found = null;
            for (int d = 6; d >= 2 && found == null; d--) {
                for (int dy = 1; dy >= -1 && found == null; dy--) {
                    BlockPos b = center.relative(dir, d).above(dy);
                    // Above must be open: air, or the fire an End Crystal leaves under itself.
                    var above = end.getBlockState(b.above());
                    if (end.getBlockState(b).is(Blocks.BEDROCK) && (above.isAir() || above.is(BlockTags.FIRE))) found = b.above();
                }
            }
            if (found == null) {
                ElementsNexus.LOGGER.warn("[Nexus] No bedrock found on the {} side of the exit portal at {} {} {}.",
                        dir.getName(), center.getX(), center.getY(), center.getZ());
                return null;
            }
            spots.add(found);
        }
        return spots;
    }

    private static List<BlockPos> listSpots() {
        List<BlockPos> spots = new ArrayList<>();
        for (String s : DragonConfig.POSITIONS.get()) {
            String[] p = s.trim().split("[\\s,]+");
            if (p.length != 3) {
                ElementsNexus.LOGGER.warn("[Nexus] dragon.toml positions: '{}' is not \"x y z\". Skipped.", s);
                continue;
            }
            try {
                spots.add(new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
            } catch (NumberFormatException e) {
                ElementsNexus.LOGGER.warn("[Nexus] dragon.toml positions: '{}' is not \"x y z\". Skipped.", s);
            }
        }
        return spots;
    }

    /** Remove stray crystals, then make sure every spot has one. Returns the crystals on the spots. */
    private static List<EndCrystal> placeCrystals(ServerLevel end, Target t) {
        if (DragonConfig.REMOVE_MISPLACED.get()) {
            int r = DragonConfig.CLEANUP_RADIUS.get();
            BlockPos c = t.center();
            AABB area = new AABB(c.getX() - r, c.getY() - 4, c.getZ() - r, c.getX() + r + 1, c.getY() + 8, c.getZ() + r + 1);
            for (EndCrystal crystal : end.getEntitiesOfClass(EndCrystal.class, area)) {
                if (!t.spots().contains(crystal.blockPosition())) crystal.discard();
            }
        }
        List<EndCrystal> out = new ArrayList<>();
        for (BlockPos spot : t.spots()) {
            List<EndCrystal> there = end.getEntitiesOfClass(EndCrystal.class, new AABB(spot));
            if (!there.isEmpty() && DragonConfig.PLACE_MISSING_ONLY.get()) {
                out.add(there.getFirst());
                continue;
            }
            for (EndCrystal old : there) old.discard();
            EndCrystal crystal = new EndCrystal(end, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            crystal.setShowBottom(DragonConfig.SHOW_BOTTOM.get());
            end.addFreshEntity(crystal);
            out.add(crystal);
        }
        return out;
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
