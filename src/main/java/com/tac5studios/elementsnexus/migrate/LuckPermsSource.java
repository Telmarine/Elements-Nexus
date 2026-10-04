package com.tac5studios.elementsnexus.migrate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tac5studios.elementsnexus.ranks.Rank;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * LuckPerms. Reads the newest /lp export file (config/luckperms/*.json.gz),
 * or else the json flat-file storage (config/luckperms/json-storage/).
 * Only nexus.* permissions are copied.
 */
public class LuckPermsSource implements Migrations.Source {

    @Override public String id() { return "luckperms"; }
    @Override public String label() { return "LuckPerms"; }

    private static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("luckperms"); }

    @Override
    public boolean found(MinecraftServer server) {
        return newestExport() != null || Files.isDirectory(dir().resolve("json-storage"));
    }

    private static Path newestExport() {
        if (!Files.isDirectory(dir())) return null;
        try (Stream<Path> s = Files.list(dir())) {
            return s.filter(p -> p.getFileName().toString().endsWith(".json.gz"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void run(Importer im) throws Exception {
        skippedTotal = 0;
        try {
            read(im);
        } finally {
            if (skippedTotal > 0) im.note(skippedTotal + " LuckPerms entries (other mods' permissions, or ones with a context or expiry) were left out.");
        }
    }

    private void read(Importer im) throws Exception {
        Path export = newestExport();
        if (export != null) {
            im.note("Reading the LuckPerms export " + export.getFileName() + ".");
            JsonObject root;
            try (var in = new InputStreamReader(new GZIPInputStream(Files.newInputStream(export)), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(in).getAsJsonObject();
            }
            JsonObject groups = root.getAsJsonObject("groups");
            if (groups != null) {
                for (Map.Entry<String, JsonElement> g : groups.entrySet()) {
                    im.rank(g.getKey(), rank(im, nodes(g.getValue().getAsJsonObject(), "nodes", "key")));
                }
            }
            im.defaultRank("default");
            JsonObject users = root.getAsJsonObject("users");
            if (users != null) {
                for (Map.Entry<String, JsonElement> u : users.entrySet()) {
                    JsonObject o = u.getValue().getAsJsonObject();
                    String primary = o.has("primaryGroup") ? o.get("primaryGroup").getAsString() : "default";
                    im.userRank(Importer.uuid(u.getKey()), o.has("username") ? o.get("username").getAsString() : null, primary);
                }
            }
            return;
        }

        Path storage = dir().resolve("json-storage");
        im.note("No /lp export file found. Reading LuckPerms json storage.");
        for (Path f : list(storage.resolve("groups"))) {
            JsonObject o = im.obj(f);
            if (o == null) continue;
            String name = f.getFileName().toString().replace(".json", "");
            List<Node> ns = nodes(o, "permissions", "permission");
            for (JsonElement p : NeoEssentialsSource.arr(o, "parents")) {
                ns.add(new Node("group." + p.getAsJsonObject().get("group").getAsString(), true, false));
            }
            for (JsonElement p : NeoEssentialsSource.arr(o, "prefixes")) {
                JsonObject x = p.getAsJsonObject();
                ns.add(new Node("prefix." + x.get("priority").getAsInt() + "." + x.get("prefix").getAsString(), true, false));
            }
            im.rank(name, rank(im, ns));
        }
        im.defaultRank("default");
        for (Path f : list(storage.resolve("users"))) {
            JsonObject o = im.obj(f);
            if (o == null) continue;
            String primary = o.has("primaryGroup") ? o.get("primaryGroup").getAsString() : "default";
            im.userRank(Importer.uuid(NeoEssentialsSource.str(o, "uuid")), NeoEssentialsSource.str(o, "name"), primary);
        }
    }

    private record Node(String key, boolean value, boolean limited) {}

    /** Read node entries. limited = has a context or an expiry (those are skipped). */
    private static List<Node> nodes(JsonObject o, String listKey, String keyField) {
        List<Node> out = new ArrayList<>();
        for (JsonElement e : NeoEssentialsSource.arr(o, listKey)) {
            if (!e.isJsonObject()) continue;
            JsonObject n = e.getAsJsonObject();
            boolean value = !n.has("value") || n.get("value").getAsBoolean();
            boolean limited = n.has("expiry") || (n.has("context") && !n.get("context").getAsJsonObject().isEmpty());
            out.add(new Node(NeoEssentialsSource.str(n, keyField), value, limited));
        }
        return out;
    }

    private int skippedTotal;

    private Rank rank(Importer im, List<Node> nodes) {
        Rank r = new Rank();
        int bestPrefix = Integer.MIN_VALUE;
        int skipped = 0;
        for (Node n : nodes) {
            if (n.limited()) {
                skipped++;
                continue;
            }
            String k = n.key();
            if (k.startsWith("weight.")) {
                try { r.priority = Integer.parseInt(k.substring(7)); } catch (NumberFormatException ignored) { }
            } else if (k.startsWith("prefix.")) {
                String[] parts = k.split("\\.", 3);
                if (parts.length == 3) {
                    int pri;
                    try { pri = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { continue; }
                    if (pri > bestPrefix) {
                        bestPrefix = pri;
                        r.prefix = parts[2];
                        r.color = Importer.colorOf(parts[2]);
                    }
                }
            } else if (k.startsWith("group.") && n.value()) {
                String g = Importer.rankKey(k.substring(6));
                if (g != null && !r.inherits.contains(g)) r.inherits.add(g);
            } else if (k.startsWith("nexus.")) {
                r.permissions.add(n.value() ? k : "-" + k);
            } else {
                skipped++;
            }
        }
        skippedTotal += skipped;
        return r;
    }

    private static List<Path> list(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
        }
    }

}
