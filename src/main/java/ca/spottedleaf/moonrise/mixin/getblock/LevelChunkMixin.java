package ca.spottedleaf.moonrise.mixin.getblock;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.getblock.GetBlockChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class LevelChunkMixin extends ChunkAccess implements GetBlockChunk {

    @Shadow
    @Final
    Level level;


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

    @Unique
    private boolean debug;

    @Unique
    private BlockState defaultBlockState;

    public LevelChunkMixin(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor,
                           Registry<Biome> registry, long l, LevelChunkSection[] levelChunkSections, BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, registry, l, levelChunkSections, blendingData);
    }

    /**
     * Initialises the min/max section
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
            at = @At("TAIL")
    )
    public void onConstruct(Level level, ChunkPos chunkPos, UpgradeData upgradeData, LevelChunkTicks levelChunkTicks, LevelChunkTicks levelChunkTicks2, long l, LevelChunkSection[] levelChunkSections, LevelChunk.PostLoadProcessor postLoadProcessor, BlendingData blendingData, CallbackInfo ci) {
        this.minSection = WorldUtil.getMinSection(level);
        this.maxSection = WorldUtil.getMaxSection(level);

        final boolean empty = ((Object)this instanceof EmptyLevelChunk);
        this.debug = !empty && this.level.isDebug();
        this.defaultBlockState = empty ? VOID_AIR_BLOCKSTATE : AIR_BLOCKSTATE;
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

    @Unique
    private BlockState getBlockDebug(final int x, final int y, final int z) {
        if (y == 60) {
            return Blocks.BARRIER.defaultBlockState();
        }

        if (y == 70) {
            final BlockState ret = DebugLevelSource.getBlockStateFor(x, z);
            return ret == null ? AIR_BLOCKSTATE : ret;
        }

        return AIR_BLOCKSTATE;
    }

    @Override
    public final BlockState moonrise$getBlock(final int x, final int y, final int z) {
        if (this.debug) {
            return this.getBlockDebug(x, y, z);
        }

        final int sectionY = (y >> 4) - this.minSection;

        final LevelChunkSection[] sections = this.sections;
        if (sectionY < 0 || sectionY >= sections.length) {
            return this.defaultBlockState;
        }

        final LevelChunkSection section = sections[sectionY];

        if (!section.hasOnlyAir()) {
            final int index = (x & 15) | ((z & 15) << 4) | ((y & 15) << (4+4));
            return section.states.get(index);
        }

        return this.defaultBlockState;
    }

    /**
     * @reason Replace with more optimised version
     * @author Spottedleaf
     */
    @Overwrite
    public FluidState getFluidState(final int x, final int y, final int z) {
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
}
