package com.tac5studios.elementsnexus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.dimension.end.DragonRespawnAnimation;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Lets the dragon respawn start the vanilla respawn with the crystals Nexus placed,
 * and correct the saved exit portal location. Both are private in the dragon fight.
 */
@Mixin(EndDragonFight.class)
public interface EndDragonFightAccessor {

    @Accessor("origin")
    BlockPos nexus$origin();

    @Accessor("portalLocation")
    BlockPos nexus$portalLocation();

    @Accessor("portalLocation")
    void nexus$setPortalLocation(BlockPos pos);

    /** Not null while the vanilla respawn is running (the saved data always reports false for this). */
    @Accessor("respawnStage")
    DragonRespawnAnimation nexus$respawnStage();

    @Invoker("respawnDragon")
    void nexus$respawnDragon(List<EndCrystal> crystals);
}
