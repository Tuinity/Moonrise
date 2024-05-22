package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevelChunkSection;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Iterator;
import java.util.function.Predicate;

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

    @Shadow
    public abstract boolean maybeHas(Predicate<BlockState> predicate);


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
        // reset, then recalculate
        this.nonEmptyBlockCount = (short)0;
        this.tickingBlockCount = (short)0;
        this.tickingFluidCount = (short)0;
        this.specialCollidingBlocks = (short)0;

        if (this.maybeHas((final BlockState state) -> !state.isAir())) {
            final PalettedContainer.Data<BlockState> data = this.states.data;
            final Palette<BlockState> palette = data.palette;
            final int paletteSize = palette.getSize();
            final BitStorage storage = data.storage;

            final Int2IntOpenHashMap counts = new Int2IntOpenHashMap(paletteSize);
            if (paletteSize == 1) {
                counts.addTo(0, storage.getSize());
            } else {
                storage.getAll((final int paletteIdx) -> {
                    counts.addTo(paletteIdx, 1);
                });
            }

            for (final Iterator<Int2IntMap.Entry> iterator = counts.int2IntEntrySet().fastIterator(); iterator.hasNext();) {
                final Int2IntMap.Entry entry = iterator.next();
                final int paletteIdx = entry.getIntKey();
                final int paletteCount = entry.getIntValue();

                final BlockState state = palette.valueFor(paletteIdx);

                if (state.isAir()) {
                    continue;
                }

                if (CollisionUtil.isSpecialCollidingBlock(state)) {
                    this.specialCollidingBlocks += paletteCount;
                }
                this.nonEmptyBlockCount += paletteCount;
                if (state.isRandomlyTicking()) {
                    this.tickingBlockCount += paletteCount;
                }

                final FluidState fluid = state.getFluidState();

                if (!fluid.isEmpty()) {
                    //this.nonEmptyBlockCount += count; // fix vanilla bug: make non empty block count correct
                    if (fluid.isRandomlyTicking()) {
                        this.tickingFluidCount += paletteCount;
                    }
                }
            }
        }
    }

    /**
     * @reason recalcBlockCounts is not called on the client, so we need to insert the call somewhere for our collision
     * state caches
     * @author Spottedleaf
     */
    @Inject(
            method = "read",
            at = @At(
                    value = "RETURN"
            )
    )
    private void callRecalcBlocksClient(final CallbackInfo ci) {
        this.recalcBlockCounts();
    }

    @Override
    public final int moonrise$getSpecialCollidingBlocks() {
        return this.specialCollidingBlocks;
    }
}
