package com.tac5studios.elementsnexus.migrate;

import com.tac5studios.elementsnexus.ranks.Rank;
import com.tac5studios.elementsnexus.ranks.Ranks;
import com.tac5studios.elementsnexus.storage.Storage;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * TAB: config/tab/groups.yml. Gives Nexus ranks with the same name their TAB tab prefix,
 * but only when the Nexus rank has no prefix yet. Per-player settings (users.yml) are not supported.
 */
public class TabSource implements Migrations.Source {

    @Override public String id() { return "tab"; }
    @Override public String label() { return "TAB"; }

    private static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("tab"); }

    @Override
    public boolean found(MinecraftServer server) {
        return Files.isRegularFile(dir().resolve("groups.yml"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(Importer im) throws Exception {
        Object root = new Yaml().load(Files.readString(dir().resolve("groups.yml"), StandardCharsets.UTF_8));
        if (!(root instanceof Map<?, ?> groups)) return;
        for (Map.Entry<?, ?> e : groups.entrySet()) {
            String name = String.valueOf(e.getKey());
            if (name.startsWith("_") || name.equals("per-world") || name.equals("per-server")) continue;
            if (!(e.getValue() instanceof Map<?, ?> props)) continue;
            Object tp = props.get("tabprefix");
            if (tp == null) continue;
            String prefix = String.valueOf(tp);
            String k = Importer.rankKey(name);
            Rank r = k == null ? null : Ranks.rank(k);
            if (r == null) {
                im.note("TAB group '" + name + "' has no Nexus rank with that name. Import your ranks first.");
                continue;
            }
            if (r.prefix != null && !r.prefix.isBlank()) {
                im.kept("Rank prefixes");
                continue;
            }
            r.prefix = prefix;
            r.color = Importer.colorOf(prefix);
            if (im.apply) Storage.get().put(Ranks.RANKS, k, r);
            im.added("Rank prefixes");
        }
        Path users = dir().resolve("users.yml");
        if (Files.isRegularFile(users)) {
            Object u = new Yaml().load(Files.readString(users, StandardCharsets.UTF_8));
            if (u instanceof Map<?, ?> m && !m.isEmpty()) {
                im.note(m.size() + " per-player TAB settings (users.yml) were not imported. Nexus styles players by rank.");
            }
        }
    }
}
