package ca.spottedleaf.moonrise.patches.collisions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ExplosionBlockCache {

    public final long key;
    public final BlockPos immutablePos;
    public final BlockState blockState;
    public final FluidState fluidState;
    public final float resistance;
    public final boolean outOfWorld;
    public Boolean shouldExplode; // null -> not called yet
    public VoxelShape cachedCollisionShape;

    public ExplosionBlockCache(final long key, final BlockPos immutablePos, final BlockState blockState,
                               final FluidState fluidState, final float resistance, final boolean outOfWorld) {
        this.key = key;
        this.immutablePos = immutablePos;
        this.blockState = blockState;
        this.fluidState = fluidState;
        this.resistance = resistance;
        this.outOfWorld = outOfWorld;
    }
}
