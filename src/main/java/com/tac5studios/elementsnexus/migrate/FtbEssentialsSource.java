package com.tac5studios.elementsnexus.migrate;

import com.tac5studios.elementsnexus.teleport.Teleports.Spot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

/** FTB Essentials: world/ftbessentials/playerdata/<uuid>.snbt and data.snbt. */
public class FtbEssentialsSource implements Migrations.Source {

    @Override public String id() { return "ftb_essentials"; }
    @Override public String label() { return "FTB Essentials"; }

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("ftbessentials");
    }

    @Override
    public boolean found(MinecraftServer server) {
        return Files.isDirectory(dir(server));
    }

    @Override
    public void run(Importer im) throws Exception {
        Path players = dir(im.server).resolve("playerdata");
        if (Files.isDirectory(players)) {
            try (Stream<Path> files = Files.list(players)) {
                for (Path f : (Iterable<Path>) files::iterator) {
                    String name = f.getFileName().toString();
                    if (!name.endsWith(".snbt")) continue;
                    UUID id = Importer.uuid(name.substring(0, name.length() - 5));
                    CompoundTag t = Snbt.read(im, f);
                    if (id == null || t == null) continue;
                    CompoundTag homes = t.getCompound("homes");
                    for (String h : homes.getAllKeys()) im.home(id, h, spot(homes.getCompound(h)));
                    if (t.contains("nick")) im.nick(id, t.getString("nick"));
                    if (t.getBoolean("muted")) im.mute(id, 0, "Imported from FTB Essentials", "");
                }
            }
        }
        CompoundTag data = Snbt.read(im, dir(im.server).resolve("data.snbt"));
        if (data != null) {
            CompoundTag warps = data.getCompound("warps");
            for (String w : warps.getAllKeys()) im.warp(w, spot(warps.getCompound(w)));
            if (!data.getCompound("kits").isEmpty()) im.note("FTB Essentials kits were not imported. Make them again with /kit create.");
        }
    }

    /** FTB saves block positions, so stand in the middle of the block. */
    private static Spot spot(CompoundTag t) {
        if (!t.contains("x")) return null;
        String dim = t.getString("dim");
        if (dim.isEmpty()) dim = "minecraft:overworld";
        return new Spot(dim, t.getInt("x") + 0.5, t.getInt("y"), t.getInt("z") + 0.5, t.getFloat("yRot"), t.getFloat("xRot"));
    }
}
