package ca.spottedleaf.moonrise.mixin.getblock;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.getblock.GetBlockChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProtoChunk.class)
abstract class ProtoChunkMixin extends ChunkAccess implements GetBlockChunk {

    public ProtoChunkMixin(final ChunkPos chunkPos, final UpgradeData upgradeData, final LevelHeightAccessor levelHeightAccessor, final PalettedContainerFactory containerFactory, final long inhabitedTime, final LevelChunkSection @Nullable [] sections, @Nullable final BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, containerFactory, inhabitedTime, sections, blendingData);
    }

    @Unique
    private static final BlockState AIR_BLOCKSTATE = Blocks.AIR.defaultBlockState();
    @Unique
    private static final FluidState AIR_FLUIDSTATE = Fluids.EMPTY.defaultFluidState();
    @Unique
    private static final BlockState VOID_AIR_BLOCKSTATE = Blocks.VOID_AIR.defaultBlockState();

    @Unique
    private int minSection;

    @Unique
    private int maxSection;

    /**
     * Initialises the min/max section
     */
    @Inject(
        method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/ticks/ProtoChunkTicks;Lnet/minecraft/world/ticks/ProtoChunkTicks;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/chunk/PalettedContainerFactory;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
        at = @At("TAIL")
    )
    public void onConstruct(ChunkPos chunkPos, UpgradeData upgradeData, LevelChunkSection[] sections, ProtoChunkTicks blockTicks, ProtoChunkTicks fluidTicks, LevelHeightAccessor levelHeightAccessor, PalettedContainerFactory containerFactory, BlendingData blendingData, CallbackInfo ci) {
        this.minSection = WorldUtil.getMinSection(levelHeightAccessor);
        this.maxSection = WorldUtil.getMaxSection(levelHeightAccessor);
    }

    @Override
    public BlockState moonrise$getBlock(final int x, final int y, final int z) {
        final int sectionY = (y >> 4) - this.minSection;

        final LevelChunkSection[] sections = this.sections;
        if (sectionY < 0 || sectionY >= sections.length) {
            return VOID_AIR_BLOCKSTATE;
        }

        final LevelChunkSection section = sections[sectionY];

        if (!section.hasOnlyAir()) {
            final int index = (x & 15) | ((z & 15) << 4) | ((y & 15) << (4+4));
            return section.states.get(index);
        }

        return AIR_BLOCKSTATE;
    }

    /**
     * @reason Route to optimized getBlock
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public BlockState getBlockState(final BlockPos pos) {
        return this.moonrise$getBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState moonrise$getFluid(final int x, final int y, final int z) {
        final int sectionY = (y >> 4) - this.minSection;

        final LevelChunkSection[] sections = this.sections;
        if (sectionY < 0 || sectionY >= sections.length) {
            return AIR_FLUIDSTATE;
        }

        final LevelChunkSection section = sections[sectionY];

        if (!section.hasOnlyAir()) {
            final int index = (x & 15) | ((z & 15) << 4) | ((y & 15) << (4+4));
            return section.states.get(index).getFluidState();
        }

        return AIR_FLUIDSTATE;
    }

    /**
     * @reason Replace with more optimised version
     * @author Spottedleaf
     */
    @Overwrite
    public FluidState getFluidState(final BlockPos pos) {
        return this.moonrise$getFluid(pos.getX(), pos.getY(), pos.getZ());
    }
}
