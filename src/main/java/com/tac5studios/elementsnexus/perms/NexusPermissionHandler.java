package com.tac5studios.elementsnexus.perms;

import com.tac5studios.elementsnexus.config.Features;
import com.tac5studios.elementsnexus.ranks.Ranks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.server.permission.handler.IPermissionHandler;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Lets other mods see Elements: Nexus ranks.
 * Turn it on in config/neoforge-server.toml:  permissionHandler = "elements_nexus:permissions"
 * Yes/no nodes come from ranks. Number and text nodes come from rank entries written as "node=value",
 * e.g. "xaero.pac_max_claims=200". Anything a rank doesn't mention uses the node's own default.
 */
public class NexusPermissionHandler implements IPermissionHandler {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("elements_nexus", "permissions");

    private final Set<PermissionNode<?>> nodes;

    public NexusPermissionHandler(Collection<PermissionNode<?>> nodes) {
        this.nodes = Collections.unmodifiableSet(new HashSet<>(nodes));
    }

    @Override
    public ResourceLocation getIdentifier() {
        return ID;
    }

    @Override
    public Set<PermissionNode<?>> getRegisteredNodes() {
        return nodes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        if (node.getType() == PermissionTypes.BOOLEAN && Features.on("ranks")) {
            if (Features.on("ranks", "ops_bypass") && Perm.isOp(player)) return (T) Boolean.TRUE;
            Ranks.Result r = Ranks.check(player.getUUID(), node.getNodeName());
            if (r != null) return (T) Boolean.valueOf(r.allowed());
        }
        T v = value(player.getUUID(), node);
        if (v != null) return v;
        return node.getDefaultResolver().resolve(player, player.getUUID(), context);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        if (node.getType() == PermissionTypes.BOOLEAN && Features.on("ranks")) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null && Features.on("ranks", "ops_bypass")) {
                var profile = server.getProfileCache() == null ? null : server.getProfileCache().get(player).orElse(null);
                if (profile != null && server.getPlayerList().isOp(profile)) return (T) Boolean.TRUE;
            }
            Ranks.Result r = Ranks.check(player, node.getNodeName());
            if (r != null) return (T) Boolean.valueOf(r.allowed());
        }
        T v = value(player, node);
        if (v != null) return v;
        return node.getDefaultResolver().resolve(null, player, context);
    }

    /** Number and text nodes from "node=value" rank entries. null = not set, or not a number for a number node. */
    @SuppressWarnings("unchecked")
    private static <T> T value(UUID player, PermissionNode<T> node) {
        if (!Features.on("ranks")) return null;
        boolean number = node.getType() == PermissionTypes.INTEGER;
        if (!number && node.getType() != PermissionTypes.STRING) return null;
        Ranks.Value v = Ranks.value(player, node.getNodeName());
        if (v == null) return null;
        if (!number) return (T) v.value();
        try {
            return (T) Integer.valueOf(v.value().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
