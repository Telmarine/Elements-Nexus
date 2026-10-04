package com.tac5studios.elementsnexus.hooks;

import com.tac5studios.elementsnexus.ElementsNexus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Who owns the land a player stands on. Talks to Open Parties and Claims or FTB Chunks
 * if one is installed (looked up at runtime, so neither is needed to run Nexus).
 */
public final class Claims {

    /** What we found: no claim mod, wilderness, or a claim. */
    public record Spot(boolean claimed, boolean server, String name) {
        static final Spot WILD = new Spot(false, false, "");
    }

    private static final UUID SERVER_CLAIM = new UUID(0, 0);
    private static final UUID EXPIRED_CLAIM = new UUID(0, 1);

    private static Boolean opac;
    private static Boolean ftb;
    private static boolean broken;

    // OPAC
    private static Method opacGet, opacManager, opacClaimAt, opacOwner, opacCustomName, opacPlayerInfo, opacUsername;
    // FTB Chunks
    private static Method ftbApi, ftbLoaded, ftbManager, ftbGetChunk, ftbTeamData, ftbTeam, ftbTeamName, ftbIsServer;
    private static java.lang.reflect.Constructor<?> ftbPos;

    private Claims() {}

    /** True if a supported claim mod is installed. */
    public static boolean available() {
        if (opac == null) {
            ModList mods = ModList.get();
            opac = mods != null && mods.isLoaded("openpartiesandclaims");
            ftb = mods != null && mods.isLoaded("ftbchunks");
        }
        return !broken && (opac || ftb);
    }

    public static Spot at(ServerPlayer p) {
        if (!available()) return Spot.WILD;
        try {
            if (opac) return opac(p);
            return ftb(p);
        } catch (Throwable t) {
            broken = true;
            ElementsNexus.LOGGER.warn("[Nexus] Could not read land claims, so the location line is turned off: {}", t.toString());
            return Spot.WILD;
        }
    }

    // ---------- Open Parties and Claims ----------

    private static Spot opac(ServerPlayer p) throws Exception {
        if (opacGet == null) {
            Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Class<?> cm = Class.forName("xaero.pac.common.claims.api.IClaimsManagerAPI");
            Class<?> claim = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI");
            Class<?> info = Class.forName("xaero.pac.common.claims.player.api.IPlayerClaimInfoAPI");
            opacGet = api.getMethod("get", MinecraftServer.class);
            opacManager = api.getMethod("getServerClaimsManager");
            opacClaimAt = cm.getMethod("get", ResourceLocation.class, int.class, int.class);
            opacOwner = claim.getMethod("getPlayerId");
            opacCustomName = cm.getMethod("getCustomName", claim, ResourceLocation.class);
            opacPlayerInfo = cm.getMethod("getPlayerInfo", UUID.class);
            opacUsername = info.getMethod("getPlayerUsername");
        }
        Object api = opacGet.invoke(null, p.server);
        Object manager = opacManager.invoke(api);
        ResourceLocation dim = p.level().dimension().location();
        BlockPos pos = p.blockPosition();
        Object claim = opacClaimAt.invoke(manager, dim, pos.getX() >> 4, pos.getZ() >> 4);
        if (claim == null) return Spot.WILD;
        UUID owner = (UUID) opacOwner.invoke(claim);
        if (EXPIRED_CLAIM.equals(owner)) return Spot.WILD;
        String custom = (String) opacCustomName.invoke(manager, claim, dim);
        boolean server = SERVER_CLAIM.equals(owner);
        if (custom != null && !custom.isBlank()) return new Spot(true, server, custom);
        if (server) return new Spot(true, true, "");
        String name = (String) opacUsername.invoke(opacPlayerInfo.invoke(manager, owner));
        return new Spot(true, false, name == null || name.isBlank() ? "" : name);
    }

    // ---------- FTB Chunks ----------

    private static Spot ftb(ServerPlayer p) throws Exception {
        if (ftbApi == null) {
            Class<?> api = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
            Class<?> apiI = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI$API");
            Class<?> pos = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
            Class<?> mgr = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunkManager");
            Class<?> chunk = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunk");
            Class<?> data = Class.forName("dev.ftb.mods.ftbchunks.api.ChunkTeamData");
            Class<?> team = Class.forName("dev.ftb.mods.ftbteams.api.Team");
            ftbApi = api.getMethod("api");
            ftbLoaded = apiI.getMethod("isManagerLoaded");
            ftbManager = apiI.getMethod("getManager");
            ftbPos = pos.getConstructor(Level.class, BlockPos.class);
            ftbGetChunk = mgr.getMethod("getChunk", pos);
            ftbTeamData = chunk.getMethod("getTeamData");
            ftbTeam = data.getMethod("getTeam");
            ftbTeamName = team.getMethod("getName");
            ftbIsServer = team.getMethod("isServerTeam");
        }
        Object api = ftbApi.invoke(null);
        if (!(boolean) ftbLoaded.invoke(api)) return Spot.WILD;
        Object chunk = ftbGetChunk.invoke(ftbManager.invoke(api), ftbPos.newInstance(p.level(), p.blockPosition()));
        if (chunk == null) return Spot.WILD;
        Object team = ftbTeam.invoke(ftbTeamData.invoke(chunk));
        String name = ((Component) ftbTeamName.invoke(team)).getString();
        return new Spot(true, (boolean) ftbIsServer.invoke(team), name);
    }
}
