package com.tac5studios.elementsnexus.mixin;

import com.tac5studios.elementsnexus.vanish.ChunkMapAccess;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements ChunkMapAccess {

    @Shadow @Final private Int2ObjectMap<?> entityMap;

    @Override
    public Int2ObjectMap<?> nexus$entityMap() {
        return entityMap;
    }
}
