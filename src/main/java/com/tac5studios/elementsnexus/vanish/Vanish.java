package com.tac5studios.elementsnexus.vanish;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.perms.Perm;
import com.tac5studios.elementsnexus.storage.Storage;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vanished staff are hidden from other players: in the world, the Tab list and the online count.
 * Staff with nexus.mod.vanish.see still see them (marked [V]). Vanish stays on across relogs.
 */
public final class Vanish {

    private static final String COLLECTION = "vanish";
    private static final Set<UUID> ON = new HashSet<>();

    private Vanish() {}

    public static boolean isVanished(ServerPlayer p) {
        return ON.contains(p.getUUID());
    }

    /** True if 'viewer' should not see 'target'. */
    public static boolean hiddenFrom(ServerPlayer target, ServerPlayer viewer) {
        return target != viewer && ON.contains(target.getUUID()) && Features.on("vanish")
                && !Perm.has(viewer, Perm.VANISH_SEE);
    }

    /** True if the Tab list and online count should also hide them. */
    public static boolean hideEverywhere() {
        return Features.on("vanish", "hide_everywhere");
    }

    /** Number of players a viewer can see as online. */
    public static int visibleCount(MinecraftServer server, ServerPlayer viewer) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!hideEverywhere() || !hiddenFrom(p, viewer)) n++;
        }
        return n;
    }

    public static void set(ServerPlayer p, boolean on) {
        if (on) {
            ON.add(p.getUUID());
            Storage.get().put(COLLECTION, p.getUUID().toString(), true);
        } else {
            ON.remove(p.getUUID());
            Storage.get().remove(COLLECTION, p.getUUID().toString());
        }
        apply(p);
    }

    /** Push the current vanish state of 'target' to everyone online. */
    public static void apply(ServerPlayer target) {
        MinecraftServer server = target.server;
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer == target) continue;
            boolean hide = hiddenFrom(target, viewer);
            if (hideEverywhere()) {
                if (hide) viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
                else viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
            }
            recheck(target, viewer);
        }
        target.refreshTabListName(); // adds or removes the [V] marker
    }

    /** Make Minecraft re-check whether 'viewer' should see 'target' in the world. */
    private static void recheck(ServerPlayer target, ServerPlayer viewer) {
        if (!(target.level() instanceof ServerLevel level) || viewer.level() != level) return;
        Object tracked = ((ChunkMapAccess) level.getChunkSource().chunkMap).nexus$entityMap().get(target.getId());
        if (tracked instanceof NexusTracked t) t.nexus$update(viewer);
    }

    /** A player joined: restore their vanish, and hide vanished staff from them. */
    public static void onJoin(ServerPlayer joined) {
        if (!Features.on("vanish")) return;
        if (Storage.get().has(COLLECTION, joined.getUUID().toString())) {
            ON.add(joined.getUUID());
            apply(joined);
            joined.sendSystemMessage(com.tac5studios.elementsnexus.util.Text.color("&7You are still vanished."));
        }
        if (!hideEverywhere()) return;
        for (ServerPlayer other : joined.server.getPlayerList().getPlayers()) {
            if (other != joined && hiddenFrom(other, joined)) {
                joined.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(other.getUUID())));
            }
        }
    }

    /** Vanished on their last visit (checked before the join event, while they are still logging in). */
    public static boolean savedVanished(UUID id) {
        return Features.on("vanish") && Storage.get().has(COLLECTION, id.toString());
    }

    /** Send a join/leave line only to staff who can see vanished players, marked [V]. */
    public static void tellStaffOnly(MinecraftServer server, ServerPlayer who, net.minecraft.network.chat.Component line) {
        net.minecraft.network.chat.Component marked = net.minecraft.network.chat.Component.empty()
                .append(com.tac5studios.elementsnexus.util.Text.color("&7[V] ")).append(line);
        server.sendSystemMessage(marked);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != who && Perm.has(p, Perm.VANISH_SEE)) p.sendSystemMessage(marked);
        }
    }

    // ---------- quiet messages (death, advancements) ----------

    private static final ThreadLocal<ServerPlayer> QUIET = new ThreadLocal<>();

    /** Messages broadcast until endQuiet() go to staff only, if this player is vanished. */
    public static void beginQuiet(ServerPlayer p) {
        if (Features.on("vanish") && ON.contains(p.getUUID())) QUIET.set(p);
    }

    public static void endQuiet() {
        QUIET.remove();
    }

    /** The vanished player whose message is being sent right now, or null. */
    public static ServerPlayer quietSource() {
        return QUIET.get();
    }

    // ---------- lookups ----------

    public static boolean isVanished(UUID id) {
        return ON.contains(id) && Features.on("vanish");
    }

    public static int vanishedOnline(MinecraftServer server) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (isVanished(p.getUUID())) n++;
        return n;
    }

    /** Names of online players this viewer shouldn't know about. */
    public static List<String> namesHiddenFrom(ServerPlayer viewer) {
        List<String> out = new java.util.ArrayList<>();
        for (ServerPlayer p : viewer.server.getPlayerList().getPlayers()) {
            if (hiddenFrom(p, viewer)) out.add(p.getGameProfile().getName());
        }
        return out;
    }

    /** Online player by name, or null if offline or hidden from this command source. */
    public static ServerPlayer findVisible(net.minecraft.commands.CommandSourceStack source, String name) {
        ServerPlayer p = source.getServer().getPlayerList().getPlayerByName(name);
        if (p == null) return null;
        ServerPlayer viewer = source.getPlayer();
        return viewer != null && hiddenFrom(p, viewer) ? null : p;
    }

    public static void forget(UUID id) {
        ON.remove(id);
    }
}
