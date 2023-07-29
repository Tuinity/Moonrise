package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.world.BlockCounter;
import ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin implements CollisionLevelChunkSection {

    @Shadow
    @Final
    public PalettedContainer<BlockState> states;

    @Shadow
    private short nonEmptyBlockCount;

    @Shadow
    private short tickingBlockCount;

    @Shadow
    private short tickingFluidCount;


    @Unique
    private int specialCollidingBlocks;

    /**
     * @reason Callback used to update the known collision data on block update.
     * @author Spottedleaf
     */
    @Inject(
            method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN")
    )
    private void updateBlockCallback(final int x, final int y, final int z, final BlockState state, final boolean lock,
                                     final CallbackInfoReturnable<BlockState> cir) {
        if (CollisionUtil.isSpecialCollidingBlock(state)) {
            ++this.specialCollidingBlocks;
        }
        if (CollisionUtil.isSpecialCollidingBlock(cir.getReturnValue())) {
            --this.specialCollidingBlocks;
        }
    }

    /**
     * @reason Insert known collision data counting
     * @author Spottedleaf
     */
    @Overwrite
    public void recalcBlockCounts() {
        final BlockCounter counter = new BlockCounter();

        this.states.count(counter);

        this.nonEmptyBlockCount = (short)counter.nonEmptyBlockCount;
        this.tickingBlockCount = (short)counter.tickingBlockCount;
        this.tickingFluidCount = (short)counter.tickingFluidCount;
        this.specialCollidingBlocks = (short)counter.specialCollidingBlocks;
    }

    @Override
    public final int getSpecialCollidingBlocks() {
        return this.specialCollidingBlocks;
    }
}
