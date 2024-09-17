package ca.spottedleaf.moonrise.mixin.block_counting;

import ca.spottedleaf.moonrise.common.list.ShortList;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
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
    private static final ShortArrayList FULL_LIST = new ShortArrayList(16*16*16);
    static {
        for (short i = 0; i < (16*16*16); ++i) {
            FULL_LIST.add(i);
        }
    }

    @Unique
    private boolean isClient;

    @Unique
    private static final short CLIENT_FORCED_SPECIAL_COLLIDING_BLOCKS = (short)9999;

    @Unique
    private short specialCollidingBlocks;

    @Unique
    private final ShortList tickingBlocks = new ShortList();

    @Override
    public final boolean moonrise$hasSpecialCollidingBlocks() {
        return this.specialCollidingBlocks != 0;
    }

    @Override
    public final ShortList moonrise$getTickingBlockList() {
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

        if (this.isClient) {
            if (CollisionUtil.isSpecialCollidingBlock(newState)) {
                this.specialCollidingBlocks = CLIENT_FORCED_SPECIAL_COLLIDING_BLOCKS;
            }
            return;
        }

        final boolean isSpecialOld = CollisionUtil.isSpecialCollidingBlock(oldState);
        final boolean isSpecialNew = CollisionUtil.isSpecialCollidingBlock(newState);
        if (isSpecialOld != isSpecialNew) {
            if (isSpecialOld) {
                --this.specialCollidingBlocks;
            } else {
                ++this.specialCollidingBlocks;
            }
        }

        final boolean oldTicking = oldState.isRandomlyTicking();
        final boolean newTicking = newState.isRandomlyTicking();
        if (oldTicking != newTicking) {
            final ShortList tickingBlocks = this.tickingBlocks;
            final short position = (short)(x | (z << 4) | (y << (4+4)));

            if (oldTicking) {
                tickingBlocks.remove(position);
            } else {
                tickingBlocks.add(position);
            }
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

            final Int2ObjectOpenHashMap<ShortArrayList> counts;
            if (paletteSize == 1) {
                counts = new Int2ObjectOpenHashMap<>(1);
                counts.put(0, FULL_LIST);
            } else {
                counts = ((BlockCountingBitStorage)storage).moonrise$countEntries();
            }

            for (final Iterator<Int2ObjectMap.Entry<ShortArrayList>> iterator = counts.int2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                final Int2ObjectMap.Entry<ShortArrayList> entry = iterator.next();
                final int paletteIdx = entry.getIntKey();
                final ShortArrayList coordinates = entry.getValue();
                final int paletteCount = coordinates.size();

                final BlockState state = palette.valueFor(paletteIdx);

                if (state.isAir()) {
                    continue;
                }

                if (CollisionUtil.isSpecialCollidingBlock(state)) {
                    this.specialCollidingBlocks += (short)paletteCount;
                }
                this.nonEmptyBlockCount += (short)paletteCount;
                if (state.isRandomlyTicking()) {
                    this.tickingBlockCount += (short)paletteCount;
                    final short[] raw = coordinates.elements();
                    final int rawLen = raw.length;

                    final ShortList tickingBlocks = this.tickingBlocks;

                    tickingBlocks.setMinCapacity(Math.min((rawLen + tickingBlocks.size()) * 3 / 2, 16*16*16));

                    Objects.checkFromToIndex(0, paletteCount, rawLen);
                    for (int i = 0; i < paletteCount; ++i) {
                        tickingBlocks.add(raw[i]);
                    }
                }

                final FluidState fluid = state.getFluidState();

                if (!fluid.isEmpty()) {
                    //this.nonEmptyBlockCount += count; // fix vanilla bug: make non-empty block count correct
                    if (fluid.isRandomlyTicking()) {
                        this.tickingFluidCount += (short)paletteCount;
                    }
                }
            }
        }
    }

    /**
     * @reason Set up special colliding blocks on the client, as it is too expensive to perform a full calculation
     * @author Spottedleaf
     */
    @Inject(
            method = "read",
            at = @At(
                    value = "RETURN"
            )
    )
    private void callRecalcBlocksClient(final CallbackInfo ci) {
        this.isClient = true;
        // force has special colliding blocks to be true
        this.specialCollidingBlocks = this.nonEmptyBlockCount != (short)0 && this.maybeHas(CollisionUtil::isSpecialCollidingBlock) ? CLIENT_FORCED_SPECIAL_COLLIDING_BLOCKS : (short)0;
    }
}
