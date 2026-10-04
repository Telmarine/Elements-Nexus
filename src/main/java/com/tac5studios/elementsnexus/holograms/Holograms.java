package com.tac5studios.elementsnexus.holograms;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.mixin.TextDisplayAccessor;
import com.tac5studios.elementsnexus.storage.Storage;
import com.tac5studios.elementsnexus.util.Fancy;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holograms: floating text made of vanilla text displays, one per line.
 * The text lives in the Nexus store. The display entities are made fresh each time the
 * area loads (old saved copies are thrown away), so they can never pile up or go missing.
 */
public final class Holograms {

    public static final String COLLECTION = "holograms";
    private static final String TAG = "nexus_hologram";

    /** id -> the display entities showing it right now (one per line). */
    private static final Map<String, List<Display.TextDisplay>> LIVE = new HashMap<>();
    /** id -> hologram data for the ones showing (so animation doesn't read the store every tick). */
    private static final Map<String, Hologram> SHOWN = new HashMap<>();
    private static long tick;

    private Holograms() {}

    // ---------- data ----------

    public static Hologram get(String id) {
        return Storage.get().get(COLLECTION, id, Hologram.class);
    }

    public static List<String> ids() {
        List<String> ids = Storage.get().keys(COLLECTION);
        ids.sort(String::compareTo);
        return ids;
    }

    /** Save and redraw. */
    public static void save(MinecraftServer server, String id, Hologram h) {
        Storage.get().put(COLLECTION, id, h);
        despawn(id);
    }

    public static void delete(String id) {
        Storage.get().remove(COLLECTION, id);
        despawn(id);
    }

    // ---------- entities ----------

    /** Throw away saved copies of our displays when a chunk loads; we make our own. */
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.loadedFromDisk() && e.getEntity().getTags().contains(TAG)) e.setCanceled(true);
    }

    public static void tick(MinecraftServer server) {
        if (!Features.on("holograms")) {
            if (!LIVE.isEmpty()) despawnAll();
            return;
        }
        tick++;
        if (tick % 20 == 0) check(server);
        animate();
    }

    /** Spawn holograms whose area is loaded but which aren't showing. */
    private static void check(MinecraftServer server) {
        for (String id : ids()) {
            Hologram h = get(id);
            if (h == null) continue;
            ServerLevel level = level(server, h.world);
            if (level == null) continue;
            int cx = net.minecraft.core.SectionPos.blockToSectionCoord(h.x);
            int cz = net.minecraft.core.SectionPos.blockToSectionCoord(h.z);
            boolean loaded = level.getChunkSource().hasChunk(cx, cz)
                    && level.isPositionEntityTicking(net.minecraft.core.BlockPos.containing(h.x, h.y, h.z));
            List<Display.TextDisplay> live = LIVE.get(id);
            boolean ok = live != null && !live.isEmpty() && live.stream().noneMatch(Entity::isRemoved);
            if (!loaded) {
                if (live != null && live.stream().allMatch(Entity::isRemoved)) {
                    LIVE.remove(id);
                    SHOWN.remove(id);
                }
                continue;
            }
            if (!ok) {
                despawn(id);
                spawn(level, id, h);
            }
        }
        for (String id : List.copyOf(LIVE.keySet())) if (!Storage.get().has(COLLECTION, id)) despawn(id);
    }

    private static void spawn(ServerLevel level, String id, Hologram h) {
        List<Display.TextDisplay> made = new ArrayList<>();
        int n = h.lines.size();
        for (int i = 0; i < n; i++) {
            Hologram.Line line = h.lines.get(i);
            double y = h.y + (n - 1 - i) * h.spacing * h.scale;
            CompoundTag tag = nbt(h, level);
            Entity e = EntityType.loadEntityRecursive(tag, level, ent -> {
                ent.moveTo(h.x, y, h.z, h.yaw, 0);
                return ent;
            });
            if (!(e instanceof Display.TextDisplay td)) continue;
            td.addTag(TAG);
            ((TextDisplayAccessor) td).nexus$setText(text(line.text));
            level.addFreshEntity(td);
            made.add(td);
        }
        LIVE.put(id, made);
        SHOWN.put(id, h);
    }

    private static CompoundTag nbt(Hologram h, Level level) {
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:text_display");
        t.putString("billboard", switch (h.facing.toLowerCase(java.util.Locale.ROOT)) {
            case "fixed", "vertical", "horizontal" -> h.facing.toLowerCase(java.util.Locale.ROOT);
            default -> "center";
        });
        t.putInt("line_width", Math.max(10, h.width));
        t.putInt("background", h.background);
        t.putBoolean("shadow", h.shadow);
        t.putBoolean("see_through", h.seeThrough);
        t.putString("alignment", "center");
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("translation", floats(0, 0, 0));
        tr.put("scale", floats(h.scale, h.scale, h.scale));
        t.put("transformation", tr);
        return t;
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }

    private static void animate() {
        for (Map.Entry<String, List<Display.TextDisplay>> e : LIVE.entrySet()) {
            Hologram h = SHOWN.get(e.getKey());
            if (h == null) continue;
            List<Display.TextDisplay> shown = e.getValue();
            for (int i = 0; i < shown.size(); i++) {
                if (i >= h.lines.size()) break;
                Hologram.Line line = h.lines.get(i);
                if (line.frames == null || line.frames.isEmpty() || line.interval <= 0) continue;
                if (tick % line.interval != 0) continue;
                int frame = (int) ((tick / line.interval) % line.frames.size());
                Display.TextDisplay td = shown.get(i);
                if (!td.isRemoved()) ((TextDisplayAccessor) td).nexus$setText(text(line.frames.get(frame)));
            }
        }
    }

    private static Component text(String raw) {
        return Text.color(Fancy.apply(raw == null || raw.isEmpty() ? " " : raw, 0));
    }

    private static void despawn(String id) {
        SHOWN.remove(id);
        List<Display.TextDisplay> live = LIVE.remove(id);
        if (live != null) for (Display.TextDisplay td : live) td.discard();
    }

    public static void despawnAll() {
        for (String id : List.copyOf(LIVE.keySet())) despawn(id);
    }

    private static ServerLevel level(MinecraftServer server, String world) {
        ResourceLocation rl = ResourceLocation.tryParse(world);
        return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }
}
