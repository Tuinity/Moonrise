package ca.spottedleaf.moonrise.mixin.block_counting;

import ca.spottedleaf.moonrise.common.list.IntList;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;

@Mixin(LevelChunkSection.class)
abstract class LevelChunkSectionMixin implements BlockCountingChunkSection {

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
    private static final IntArrayList FULL_LIST = new IntArrayList(16*16*16);
    static {
        for (int i = 0; i < (16*16*16); ++i) {
            FULL_LIST.add(i);
        }
    }

    // Client-side: this will only be 0 or 1 (0 meaning no special colliders, 1 meaning any non-0 amount), see checkForSpecialCollidingBlocksClient and uses of moonrise$getSpecialCollidingBlocks
    @Unique
    private int specialCollidingBlocks;

    @Unique
    private final IntList tickingBlocks = new IntList();

    @Override
    public final int moonrise$getSpecialCollidingBlocks() {
        return this.specialCollidingBlocks;
    }

    @Override
    public final IntList moonrise$getTickingBlockList() {
        return this.tickingBlocks;
    }

    /**
     * @reason Callback used to update block counts on block change.
     * @author Spottedleaf
     */
    @Inject(
            method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At(
                    "RETURN"
            )
    )
    private void updateBlockCallback(final int x, final int y, final int z, final BlockState newState, final boolean lock,
                                     final CallbackInfoReturnable<BlockState> cir, @Local(ordinal = 1) final BlockState oldState) {
        if (oldState == newState) {
            return;
        }
        if (CollisionUtil.isSpecialCollidingBlock(oldState)) {
            --this.specialCollidingBlocks;
        }
        if (CollisionUtil.isSpecialCollidingBlock(newState)) {
            ++this.specialCollidingBlocks;
        }

        final int position = x | (z << 4) | (y << (4+4));

        if (oldState.isRandomlyTicking()) {
            this.tickingBlocks.remove(position);
        }
        if (newState.isRandomlyTicking()) {
            this.tickingBlocks.add(position);
        }
    }

    /**
     * @reason We should only adjust the fluid ticking count based on whether the fluid is TICKING, not whether it is EMPTY.
     * @author Spottedleaf
     */
    @Redirect(
        method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;isEmpty()Z"
        )
    )
    private boolean fixTickingFluidCount(final FluidState instance) {
        return !instance.isRandomlyTicking();
    }

    /**
     * @reason Calculate block counts after deserialization.
     * @author Spottedleaf
     */
    @Overwrite
    public void recalcBlockCounts() {
        // reset, then recalculate
        this.nonEmptyBlockCount = (short)0;
        this.tickingBlockCount = (short)0;
        this.tickingFluidCount = (short)0;
        this.specialCollidingBlocks = (short)0;
        this.tickingBlocks.clear();

        if (this.maybeHas((final BlockState state) -> !state.isAir())) {
            final PalettedContainer.Data<BlockState> data = this.states.data;
            final Palette<BlockState> palette = data.palette;
            final int paletteSize = palette.getSize();
            final BitStorage storage = data.storage;

            final Int2ObjectOpenHashMap<IntArrayList> counts;
            if (paletteSize == 1) {
                counts = new Int2ObjectOpenHashMap<>(1);
                counts.put(0, FULL_LIST);
            } else {
                counts = ((BlockCountingBitStorage)storage).moonrise$countEntries();
            }

            for (final Iterator<Int2ObjectMap.Entry<IntArrayList>> iterator = counts.int2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                final Int2ObjectMap.Entry<IntArrayList> entry = iterator.next();
                final int paletteIdx = entry.getIntKey();
                final IntArrayList coordinates = entry.getValue();
                final int paletteCount = coordinates.size();

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
                    final int[] raw = coordinates.elements();

                    Objects.checkFromToIndex(0, paletteCount, raw.length);
                    for (int i = 0; i < paletteCount; ++i) {
                        this.tickingBlocks.add(raw[i]);
                    }
                }

                final FluidState fluid = state.getFluidState();

                if (!fluid.isEmpty()) {
                    //this.nonEmptyBlockCount += count; // fix vanilla bug: make non-empty block count correct
                    if (fluid.isRandomlyTicking()) {
                        this.tickingFluidCount += paletteCount;
                    }
                }
            }
        }
    }

    /**
     * @reason We need to know if there are any special colliding blocks in the section
     * @author Spottedleaf
     */
    @Inject(
            method = "read",
            at = @At(
                    value = "RETURN"
            )
    )
    private void checkForSpecialCollidingBlocksClient(final CallbackInfo ci) {
        this.specialCollidingBlocks = this.maybeHas(CollisionUtil::isSpecialCollidingBlock) ? 1 : 0;
    }
}
