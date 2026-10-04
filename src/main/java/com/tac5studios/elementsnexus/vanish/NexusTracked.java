package com.tac5studios.elementsnexus.vanish;

import net.minecraft.server.level.ServerPlayer;

/** Added to Minecraft's entity tracker by a mixin, so vanish can make it re-check who sees a player. */
public interface NexusTracked {
    void nexus$update(ServerPlayer viewer);
}
