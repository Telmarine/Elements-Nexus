package com.tac5studios.elementsnexus.migrate;

import com.tac5studios.elementsnexus.ranks.Rank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** FTB Ranks: world/serverconfig/ftbranks/ranks.snbt and players.snbt. */
public class FtbRanksSource implements Migrations.Source {

    private static final Set<String> RESERVED = Set.of("name", "power", "condition");

    @Override public String id() { return "ftb_ranks"; }
    @Override public String label() { return "FTB Ranks"; }

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("ftbranks");
    }

    @Override
    public boolean found(MinecraftServer server) {
        return Files.isRegularFile(dir(server).resolve("ranks.snbt"));
    }

    @Override
    public void run(Importer im) {
        CompoundTag ranks = Snbt.read(im, dir(im.server).resolve("ranks.snbt"));
        if (ranks == null) return;
        Map<String, Integer> power = new HashMap<>();
        String lowestAlways = null;
        int lowestPower = Integer.MAX_VALUE;
        int skipped = 0;

        for (String id : ranks.getAllKeys()) {
            if (!(ranks.get(id) instanceof CompoundTag t)) continue;
            Rank r = new Rank();
            r.priority = t.getInt("power");
            power.put(id, r.priority);
            String fmt = t.getString("ftbranks.name_format");
            String prefix = fmt.replace("{name}", "").stripTrailing();
            r.prefix = prefix.isEmpty() ? "" : prefix + " ";
            r.color = Importer.colorOf(fmt);
            for (String key : t.getAllKeys()) {
                if (RESERVED.contains(key) || key.equals("ftbranks.name_format")) continue;
                if (!key.startsWith("nexus.")) {
                    skipped++;
                    continue;
                }
                Tag v = t.get(key);
                boolean on = !(v instanceof NumericTag n) || n.getAsByte() != 0;
                r.permissions.add(on ? key : "-" + key);
            }
            if ("always_active".equals(t.getString("condition")) && r.priority < lowestPower) {
                lowestPower = r.priority;
                lowestAlways = id;
            }
            im.rank(id, r);
        }
        if (skipped > 0) im.note(skipped + " FTB Ranks permissions are not Nexus permissions and were left out.");
        if (lowestAlways != null) im.defaultRank(lowestAlways);

        CompoundTag players = Snbt.read(im, dir(im.server).resolve("players.snbt"));
        if (players == null) return;
        for (String key : players.getAllKeys()) {
            if (!(players.get(key) instanceof CompoundTag p)) continue;
            UUID id = Importer.uuid(key);
            String best = null;
            int bestPower = Integer.MIN_VALUE;
            CompoundTag has = p.getCompound("ranks");
            for (String rank : has.getAllKeys()) {
                int pw = power.getOrDefault(rank, 0);
                if (pw > bestPower) {
                    bestPower = pw;
                    best = rank;
                }
            }
            if (best != null) im.userRank(id, p.getString("name"), best);
        }
    }
}
