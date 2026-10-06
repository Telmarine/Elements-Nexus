package com.tac5studios.elementsnexus.hooks;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.config.WaystonesConfig;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.util.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Waystone rules per dimension (waystones.toml): placing, first activation, and teleporting to / from.
 * Placing works for any listed mod or block. Activation and teleports hook into the Waystones mod
 * through reflection and its own cancellable teleport event, so Nexus never needs Waystones to load.
 */
public final class WaystoneRules {

    private static final String WAYSTONES = "waystones";

    private static boolean triedApi;
    private static Method getWaystoneAt;   // WaystonesAPI.getWaystoneAt(ServerLevel, BlockPos) -> Optional<Waystone>
    private static Method isActivated;     // WaystonesAPI.isWaystoneActivated(Player, Waystone)

    private WaystoneRules() {}

    public static void setup() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, WaystoneRules::onPlace);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, WaystoneRules::onClick);
        if (ModList.get().isLoaded(WAYSTONES)) listenToTeleports();
    }

    // ---------- rules ----------

    /** True if this action is denied in this dimension. */
    private static boolean denied(ResourceKey<Level> dim, String what) {
        String id = dim.location().toString();
        for (String entry : WaystonesConfig.DENY.get()) {
            int eq = entry.indexOf('=');
            if (eq < 0 || !entry.substring(0, eq).trim().equalsIgnoreCase(id)) continue;
            for (String w : entry.substring(eq + 1).split(",")) {
                if (w.trim().toLowerCase(Locale.ROOT).equals(what)) return true;
            }
        }
        return false;
    }

    private static boolean bypass(ServerPlayer p) {
        return Perm.has(p, Perm.WAYSTONES_BYPASS);
    }

    private static boolean isWaystoneBlock(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Set<String> list = new HashSet<>();
        for (String s : WaystonesConfig.MODS.get()) list.add(s.trim().toLowerCase(Locale.ROOT));
        return list.contains(id.getNamespace()) || list.contains(id.toString());
    }

    private static String world(ResourceKey<Level> dim) {
        return dim.location().getPath();
    }

    // ---------- placing ----------

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!Features.on("waystones", "place")) return;
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof Level level)) return;
        if (!isWaystoneBlock(e.getPlacedBlock()) || !denied(level.dimension(), "place") || bypass(p)) return;
        e.setCanceled(true);
        p.containerMenu.sendAllDataToRemote(); // put the item back on the player's screen
        p.displayClientMessage(Text.color("&cWaystones can't be placed in " + world(level.dimension()) + "."), true);
    }

    // ---------- activating ----------

    private static void onClick(PlayerInteractEvent.RightClickBlock e) {
        if (!Features.on("waystones", "activate")) return;
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        if (!isWaystoneBlock(level.getBlockState(e.getPos())) || !denied(level.dimension(), "activate") || bypass(p)) return;
        if (!api()) return;
        try {
            Optional<?> ws = (Optional<?>) getWaystoneAt.invoke(null, level, e.getPos());
            if (ws.isEmpty()) ws = (Optional<?>) getWaystoneAt.invoke(null, level, e.getPos().below()); // top half
            if (ws.isEmpty() || (boolean) isActivated.invoke(null, p, ws.get())) return; // already known: normal use
            e.setCanceled(true);
            p.displayClientMessage(Text.color("&cWaystones can't be activated in " + world(level.dimension()) + "."), true);
        } catch (Exception ex) {
            ElementsNexus.LOGGER.warn("[Nexus] Could not check a waystone: {}", ex.toString());
        }
    }

    private static boolean api() {
        if (triedApi) return getWaystoneAt != null;
        triedApi = true;
        if (!ModList.get().isLoaded(WAYSTONES)) return false;
        try {
            Class<?> api = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
            Class<?> waystone = Class.forName("net.blay09.mods.waystones.api.Waystone");
            getWaystoneAt = api.getMethod("getWaystoneAt", ServerLevel.class, BlockPos.class);
            isActivated = api.getMethod("isWaystoneActivated", net.minecraft.world.entity.player.Player.class, waystone);
            return true;
        } catch (Exception ex) {
            getWaystoneAt = null;
            ElementsNexus.LOGGER.warn("[Nexus] Waystones is installed but its API could not be read, so activation rules are off: {}", ex.toString());
            return false;
        }
    }

    // ---------- teleporting ----------

    /** True when both ends of the teleport are a type listed in allow_types (e.g. warp plate to warp plate). */
    private static boolean allowedType(Method getType, Object target, Object from) throws Exception {
        if (target == null || from == null) return false;
        Set<String> list = new HashSet<>();
        for (String s : WaystonesConfig.ALLOW_TYPES.get()) list.add(s.trim().toLowerCase(Locale.ROOT));
        if (list.isEmpty()) return false;
        Object a = getType.invoke(target), b = getType.invoke(from);
        return a != null && b != null && list.contains(a.toString()) && list.contains(b.toString());
    }

    @SuppressWarnings("unchecked")
    private static void listenToTeleports() {
        try {
            Class<?> pre = Class.forName("net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Pre");
            Method getContext = pre.getMethod("getContext");
            Class<?> ctx = Class.forName("net.blay09.mods.waystones.api.WaystoneTeleportContext");
            Method getEntity = ctx.getMethod("getEntity");
            Method getTarget = ctx.getMethod("getTargetWaystone");
            Method getFrom = ctx.getMethod("getFromWaystone");
            Class<?> waystone = Class.forName("net.blay09.mods.waystones.api.Waystone");
            Method getDimension = waystone.getMethod("getDimension");
            Method getType = waystone.getMethod("getWaystoneType");
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (Class<Event>) pre, e -> {
                if (!Features.on("waystones", "teleport")) return;
                try {
                    Object context = getContext.invoke(e);
                    if (!(getEntity.invoke(context) instanceof Entity entity)) return;
                    ServerPlayer p = entity instanceof ServerPlayer sp ? sp : null;
                    if (p != null && bypass(p)) return;
                    Object target = getTarget.invoke(context);
                    if (allowedType(getType, target, ((Optional<?>) getFrom.invoke(context)).orElse(null))) return;
                    ResourceKey<Level> to = (ResourceKey<Level>) getDimension.invoke(target);
                    ResourceKey<Level> from = entity.level().dimension();
                    String why = null;
                    if (denied(from, "from")) why = "You can't use waystones to leave " + world(from) + ".";
                    else if (to != null && denied(to, "to")) why = "Waystones can't take you to " + world(to) + ".";
                    if (why == null) return;
                    ((ICancellableEvent) e).setCanceled(true);
                    if (p != null) p.sendSystemMessage(Text.color("&c" + why));
                } catch (Exception ex) {
                    ElementsNexus.LOGGER.warn("[Nexus] Could not check a waystone teleport: {}", ex.toString());
                }
            });
            ElementsNexus.LOGGER.info("[Nexus] Waystones found. Waystone teleport rules are ready.");
        } catch (Exception ex) {
            ElementsNexus.LOGGER.warn("[Nexus] Waystones is installed but its teleport event could not be found, so teleport rules are off: {}", ex.toString());
        }
    }
}
