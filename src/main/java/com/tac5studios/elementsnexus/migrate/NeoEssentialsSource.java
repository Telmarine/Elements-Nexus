package com.tac5studios.elementsnexus.migrate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tac5studios.elementsnexus.kits.Kit;
import com.tac5studios.elementsnexus.ranks.Rank;
import com.tac5studios.elementsnexus.rules.Rules;
import com.tac5studios.elementsnexus.teleport.Teleports.Spot;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NeoEssentials (json storage): neoessentials/store/*.json and config/neoessentials/.
 * Imports ranks, player ranks, homes, warps, spawn, back, kits, kit claims, nicknames,
 * active mutes and bans, the jail spot and the rules.
 */
public class NeoEssentialsSource implements Migrations.Source {

    /** NeoEssentials permission -> Nexus permission. Anything not listed is skipped. */
    private static final Map<String, String> NODES = Map.ofEntries(
            Map.entry("neoessentials.*", "nexus.*"),
            Map.entry("neoessentials.afk", "nexus.afk.use"),
            Map.entry("neoessentials.afk.exempt", "nexus.afk.exempt"),
            Map.entry("neoessentials.chat.msg", "nexus.msg.use"),
            Map.entry("neoessentials.chat.socialspy", "nexus.msg.spy"),
            Map.entry("neoessentials.chat.staff", "nexus.staffchat.use"),
            Map.entry("neoessentials.chat.mute", "nexus.mod.mute"),
            Map.entry("neoessentials.invsee", "nexus.mod.inv"),
            Map.entry("neoessentials.kits.use", "nexus.kit.use"),
            Map.entry("neoessentials.moderation.freeze", "nexus.mod.freeze"),
            Map.entry("neoessentials.moderation.jail", "nexus.mod.jail"),
            Map.entry("neoessentials.moderation.kick", "nexus.mod.kick"),
            Map.entry("neoessentials.moderation.warn", "nexus.mod.warn"),
            Map.entry("neoessentials.moderation.warnings", "nexus.mod.history"),
            Map.entry("neoessentials.moderation.tempban", "nexus.mod.ban"),
            Map.entry("neoessentials.moderation.ban", "nexus.mod.ban"),
            Map.entry("neoessentials.moderation.tempbanip", "nexus.mod.banip"),
            Map.entry("neoessentials.moderation.banip", "nexus.mod.banip"),
            Map.entry("neoessentials.moderation.vanish", "nexus.mod.vanish"),
            Map.entry("neoessentials.moderation.seevanished", "nexus.mod.vanish.see"),
            Map.entry("neoessentials.teleport.admin.tp", "nexus.tp.use"),
            Map.entry("neoessentials.teleport.admin.tphere", "nexus.tp.use"),
            Map.entry("neoessentials.teleport.admin.tppos", "nexus.tp.use"),
            Map.entry("neoessentials.teleport.home", "nexus.home.use"),
            Map.entry("neoessentials.teleport.home.others", "nexus.home.others"),
            Map.entry("neoessentials.teleport.spawn", "nexus.spawn.use"),
            Map.entry("neoessentials.teleport.spawn.set", "nexus.spawn.set"),
            Map.entry("neoessentials.teleport.warp.create", "nexus.warp.admin"),
            Map.entry("neoessentials.teleport.warp.delete", "nexus.warp.admin"));

    @Override public String id() { return "neoessentials"; }
    @Override public String label() { return "NeoEssentials"; }

    private static Path store() { return FMLPaths.GAMEDIR.get().resolve("neoessentials").resolve("store"); }
    private static Path config() { return FMLPaths.CONFIGDIR.get().resolve("neoessentials"); }

    @Override
    public boolean found(MinecraftServer server) {
        return Files.isDirectory(store());
    }

    @Override
    public void run(Importer im) {
        if (Files.isRegularFile(store().resolve("data.db"))) {
            im.note("NeoEssentials uses SQLite storage here. Only json storage can be imported.");
        }
        ranks(im);
        places(im);
        kits(im);
        nicknames(im);
        holograms(im);
        punishments(im);
        rules(im);
    }

    // ---------- ranks ----------

    private void ranks(Importer im) {
        JsonObject groups = im.obj(store().resolve("permission_groups.json"));
        int skippedNodes = 0;
        if (groups != null) {
            for (Map.Entry<String, JsonElement> e : groups.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject g = e.getValue().getAsJsonObject();
                Rank r = new Rank();
                String prefix = str(g, "prefix");
                r.prefix = prefix.isEmpty() ? "" : prefix + " ";
                r.color = Importer.colorOf(prefix);
                r.priority = g.has("priority") ? g.get("priority").getAsInt() : 0;
                for (JsonElement in : arr(g, "inherits")) {
                    String k = Importer.rankKey(in.getAsString());
                    if (k != null) r.inherits.add(k);
                }
                for (JsonElement p : arr(g, "permissions")) {
                    String node = p.getAsString();
                    boolean deny = node.startsWith("-");
                    String bare = deny ? node.substring(1) : node;
                    String mapped = mapNode(bare);
                    if (mapped == null) {
                        skippedNodes++;
                        continue;
                    }
                    String full = (deny ? "-" : "") + mapped;
                    if (!r.permissions.contains(full)) r.permissions.add(full);
                }
                im.rank(e.getKey(), r);
            }
        }
        if (skippedNodes > 0) im.note(skippedNodes + " NeoEssentials permissions have no Nexus match and were left out.");

        JsonObject main = im.obj(config().resolve("main.json"));
        String def = main != null ? findString(main, "defaultGroup") : null;
        if (def != null) im.defaultRank(def);

        JsonObject users = im.obj(store().resolve("permission_users.json"));
        if (users != null) {
            for (Map.Entry<String, JsonElement> e : users.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                im.userRank(Importer.uuid(e.getKey()), null, str(e.getValue().getAsJsonObject(), "group"));
            }
        }
    }

    /** neoessentials.home.N -> nexus.home.limit.N, plus the table above. */
    private static String mapNode(String node) {
        if (node.startsWith("neoessentials.home.")) {
            String n = node.substring("neoessentials.home.".length());
            if (n.matches("\\d+")) return "nexus.home.limit." + n;
            if (n.equals("unlimited") || n.equals("*")) return "nexus.home.limit.unlimited";
        }
        if (node.startsWith("nexus.")) return node;
        return NODES.get(node);
    }

    // ---------- homes, warps, spawn, back, jail ----------

    private void places(Importer im) {
        JsonObject homes = im.obj(store().resolve("playerdata_homes.json"));
        if (homes != null) {
            for (Map.Entry<String, JsonElement> e : homes.entrySet()) {
                UUID id = Importer.uuid(e.getKey());
                if (id == null || !e.getValue().isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> h : e.getValue().getAsJsonObject().entrySet()) {
                    im.home(id, h.getKey(), spot(h.getValue()));
                }
            }
        }

        JsonObject warps = im.obj(store().resolve("warps.json"));
        if (warps != null) {
            for (Map.Entry<String, JsonElement> e : warps.entrySet()) {
                if (e.getKey().startsWith("!!")) continue; // settings entry
                im.warp(e.getKey(), spot(e.getValue()));
            }
        }

        JsonObject spawn = im.obj(store().resolve("spawn.json"));
        if (spawn != null && spawn.has("global") && spawn.getAsJsonObject("global").has("spawn")) {
            im.spawn(spot(spawn.getAsJsonObject("global").get("spawn")));
        }

        JsonObject back = im.obj(store().resolve("playerdata_back_locations.json"));
        if (back != null) {
            for (Map.Entry<String, JsonElement> e : back.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                im.back(Importer.uuid(e.getKey()), spot(e.getValue().getAsJsonObject().get("backLocation")));
            }
        }

        JsonObject jails = im.obj(store().resolve("jail_locations.json"));
        if (jails != null && !jails.isEmpty()) {
            JsonElement first = jails.entrySet().iterator().next().getValue();
            if (first.isJsonObject() && first.getAsJsonObject().has("position")) {
                JsonObject j = first.getAsJsonObject();
                JsonObject pos = j.getAsJsonObject("position");
                im.jailSpot(new Spot(str(j, "dimension"), pos.get("x").getAsDouble() + 0.5, pos.get("y").getAsDouble(),
                        pos.get("z").getAsDouble() + 0.5, 0, 0));
                if (jails.size() > 1) im.note("Nexus has one jail. Only the first NeoEssentials jail was imported.");
            }
        }
        JsonObject jailed = im.obj(store().resolve("jails.json"));
        if (jailed != null && !jailed.isEmpty()) {
            im.note(jailed.size() + " jailed player(s) were not imported. Jail them again with /jail if needed.");
        }
    }

    private static Spot spot(JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        if (!o.has("x") || !o.has("y") || !o.has("z")) return null;
        String world = str(o, "world");
        if (world.isEmpty()) world = "minecraft:overworld";
        return new Spot(world, o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
                o.has("yaw") ? o.get("yaw").getAsFloat() : 0, o.has("pitch") ? o.get("pitch").getAsFloat() : 0);
    }

    // ---------- kits ----------

    private void kits(Importer im) {
        JsonObject kits = im.obj(store().resolve("kits.json"));
        if (kits != null) {
            int lostData = 0;
            for (Map.Entry<String, JsonElement> e : kits.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject k = e.getValue().getAsJsonObject();
                Kit kit = new Kit();
                kit.cooldown = k.has("cooldown") ? k.get("cooldown").getAsLong() : 0;
                for (JsonElement it : arr(k, "items")) {
                    if (!it.isJsonObject()) continue;
                    JsonObject i = it.getAsJsonObject();
                    JsonObject stack = new JsonObject();
                    stack.addProperty("id", str(i, "item"));
                    stack.addProperty("count", i.has("count") ? i.get("count").getAsInt() : 1);
                    kit.items.add(stack);
                    String nbt = str(i, "nbt");
                    if (!nbt.isEmpty() && !nbt.equals("{}")) lostData++;
                }
                if (!arr(k, "commands").isEmpty()) im.note("Kit '" + e.getKey() + "' runs commands. Nexus kits only give items.");
                im.kit(e.getKey(), kit);
            }
            if (lostData > 0) im.note(lostData + " kit item(s) had extra data (names, enchantments). Only the item and count were kept.");
        }

        JsonObject usages = im.obj(store().resolve("kit_usages.json"));
        if (usages != null) {
            for (Map.Entry<String, JsonElement> e : usages.entrySet()) {
                UUID id = Importer.uuid(e.getKey());
                if (id == null || !e.getValue().isJsonObject()) continue;
                JsonObject u = e.getValue().getAsJsonObject().getAsJsonObject("usages");
                if (u == null) continue;
                for (Map.Entry<String, JsonElement> k : u.entrySet()) {
                    if (k.getValue().getAsInt() > 0) im.kitClaimed(id, k.getKey());
                }
            }
        }
    }

    // ---------- holograms ----------

    private void holograms(Importer im) {
        JsonObject all = im.obj(store().resolve("holograms.json"));
        if (all == null) return;
        boolean extras = false;
        for (Map.Entry<String, JsonElement> e : all.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject o = e.getValue().getAsJsonObject();
            var h = new com.tac5studios.elementsnexus.holograms.Hologram();
            h.world = str(o, "world").isEmpty() ? "minecraft:overworld" : str(o, "world");
            h.x = o.has("x") ? o.get("x").getAsDouble() : 0;
            h.y = o.has("y") ? o.get("y").getAsDouble() : 0;
            h.z = o.has("z") ? o.get("z").getAsDouble() : 0;
            if (o.has("scale")) h.scale = o.get("scale").getAsFloat();
            if (o.has("lineSpacing")) h.spacing = o.get("lineSpacing").getAsFloat();
            if (o.has("textShadow")) h.shadow = o.get("textShadow").getAsBoolean();
            if (o.has("backgroundColorArgb")) h.background = o.get("backgroundColorArgb").getAsInt();
            if (o.has("lineWidth")) h.width = o.get("lineWidth").getAsInt();
            if (o.has("seeThrough")) h.seeThrough = o.get("seeThrough").getAsBoolean();
            int mode = o.has("billboardMode") ? o.get("billboardMode").getAsInt() : 3;
            h.facing = switch (mode) { case 0 -> "fixed"; case 1 -> "vertical"; case 2 -> "horizontal"; default -> "center"; };
            if (bool(o, "spinEnabled") || bool(o, "hoverEnabled")) extras = true;
            for (JsonElement le : arr(o, "lines")) {
                if (!le.isJsonObject()) continue;
                JsonObject l = le.getAsJsonObject();
                var line = new com.tac5studios.elementsnexus.holograms.Hologram.Line();
                line.text = str(l, "text");
                for (JsonElement f : arr(l, "frames")) line.frames.add(f.getAsString());
                int every = l.has("animFrameIntervalTicks") ? l.get("animFrameIntervalTicks").getAsInt() : 0;
                if (every > 0) line.interval = every;
                else line.frames.clear();
                h.lines.add(line);
            }
            im.hologram(e.getKey(), h);
        }
        if (extras) im.note("Some holograms spin or bob. Nexus holograms stay still.");
    }

    // ---------- nicknames ----------

    private void nicknames(Importer im) {
        JsonObject nicks = im.obj(config().resolve("nickname_data.json"));
        if (nicks == null) return;
        for (Map.Entry<String, JsonElement> e : nicks.entrySet()) {
            if (e.getValue().isJsonPrimitive()) im.nick(Importer.uuid(e.getKey()), e.getValue().getAsString());
        }
    }

    // ---------- mutes and bans ----------

    private void punishments(Importer im) {
        JsonObject mutes = im.obj(store().resolve("mutes.json"));
        if (mutes != null) {
            for (JsonElement v : mutes.asMap().values()) {
                if (!v.isJsonObject()) continue;
                JsonObject m = v.getAsJsonObject();
                if (!bool(m, "active")) continue;
                UUID id = im.uuidByName(str(m, "target"));
                if (id == null) {
                    im.note("Mute for '" + str(m, "target") + "' skipped: player not known.");
                    continue;
                }
                im.mute(id, num(m, "expireTime"), str(m, "reason"), str(m, "mutedBy"));
            }
        }
        JsonObject bans = im.obj(store().resolve("player_bans.json"));
        if (bans != null) {
            for (JsonElement v : bans.asMap().values()) {
                if (!v.isJsonObject()) continue;
                JsonObject b = v.getAsJsonObject();
                if (!bool(b, "active")) continue;
                im.ban(Importer.uuid(str(b, "playerId")), str(b, "playerName"), num(b, "banTime"), num(b, "expireTime"),
                        str(b, "reason"), str(b, "bannedBy"));
            }
        }
        JsonObject ipBans = im.obj(store().resolve("ip_bans.json"));
        if (ipBans != null) {
            for (JsonElement v : ipBans.asMap().values()) {
                if (!v.isJsonObject()) continue;
                JsonObject b = v.getAsJsonObject();
                if (!bool(b, "active")) continue;
                im.ipBan(str(b, "ipAddress"), num(b, "banTime"), num(b, "expireTime"), str(b, "reason"), str(b, "bannedBy"));
            }
        }
    }

    // ---------- rules ----------

    private void rules(Importer im) {
        JsonObject rules = im.obj(config().resolve("rules_data.json"));
        if (rules == null) return;
        List<String> list = new ArrayList<>();
        for (JsonElement r : arr(rules, "rules")) list.add(r.getAsString());
        if (list.isEmpty()) return;
        im.note("Rules: " + list.size() + " NeoEssentials rules replace the current /rules list.");
        if (im.apply) {
            Rules.list().clear();
            Rules.list().addAll(list);
            Rules.save();
        }
        for (int i = 0; i < list.size(); i++) im.added("Rules");
    }

    // ---------- json helpers ----------

    static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    static long num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0;
    }

    static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    static JsonArray arr(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    /** Find a string value by key anywhere in a JSON tree. */
    static String findString(JsonElement e, String key) {
        if (e == null) return null;
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet()) {
                if (m.getKey().equals(key) && m.getValue().isJsonPrimitive()) return m.getValue().getAsString();
                String f = findString(m.getValue(), key);
                if (f != null) return f;
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                String f = findString(x, key);
                if (f != null) return f;
            }
        }
        return null;
    }
}
