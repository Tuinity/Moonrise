package ca.spottedleaf.moonrise.patches.getblock;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public interface GetBlockChunk {

    public BlockState moonrise$getBlock(final int x, final int y, final int z);

    public FluidState moonrise$getFluid(final int x, final int y, final int z);

}
