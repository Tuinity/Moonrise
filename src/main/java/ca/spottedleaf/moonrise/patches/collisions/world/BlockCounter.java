package ca.spottedleaf.moonrise.patches.collisions.world;

import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;

public final class BlockCounter implements PalettedContainer.CountConsumer<BlockState> {
    public int nonEmptyBlockCount;
    public int tickingBlockCount;
    public int tickingFluidCount;
    public int specialCollidingBlocks;

    @Override
    public void accept(final BlockState state, final int count) {
        // our logic
        if (CollisionUtil.isSpecialCollidingBlock(state)) {
            this.specialCollidingBlocks += count;
        }

        // Vanilla logic
        if (!state.isAir()) {
            this.nonEmptyBlockCount += count;
            if (state.isRandomlyTicking()) {
                this.tickingBlockCount += count;
            }
        }

        final FluidState fluid = state.getFluidState();

        if (!fluid.isEmpty()) {
            //this.nonEmptyBlockCount += i; // fix vanilla bug: make non empty block count correct
            if (fluid.isRandomlyTicking()) {
                this.tickingFluidCount += count;
            }
        }
    }
}