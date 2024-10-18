package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.ticks.ChunkSystemLevelChunkTicks;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class LevelChunkMixin extends ChunkAccess implements ChunkSystemLevelChunk {

    @Shadow
    @Final
    private LevelChunkTicks<Block> blockTicks;

    @Shadow
    @Final
    private LevelChunkTicks<Fluid> fluidTicks;

    @Shadow
    @Final
    Level level;

    public LevelChunkMixin(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, Registry<Biome> registry, long l, @Nullable LevelChunkSection[] levelChunkSections, @Nullable BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, registry, l, levelChunkSections, blendingData);
    }

    @Unique
    private boolean postProcessingDone;

    @Unique
    private ServerChunkCache.ChunkAndHolder chunkAndHolder;

    @Override
    public final boolean moonrise$isPostProcessingDone() {
        return this.postProcessingDone;
    }

    @Override
    public final ServerChunkCache.ChunkAndHolder moonrise$getChunkAndHolder() {
        return this.chunkAndHolder;
    }

    @Override
    public final void moonrise$setChunkAndHolder(final ServerChunkCache.ChunkAndHolder holder) {
        this.chunkAndHolder = holder;
    }

    /**
     * @reason Hook to set {@link #postProcessingDone} to {@code true} when post-processing completes to avoid invoking
     *         this function many times by the player chunk loader.
     * @author Spottedlef
     */
    @Inject(
            method = "postProcessGeneration",
            at = @At(
                    value = "RETURN"
            )
    )
    private void finishPostProcessing(final CallbackInfo ci) {
        this.postProcessingDone = true;
    }

    // add support for dirty scheduled chunk ticks
    @Override
    public boolean isUnsaved() {
        final long gameTime = this.level.getGameTime();
        if (((ChunkSystemLevelChunkTicks)this.blockTicks).moonrise$isDirty(gameTime)
                || ((ChunkSystemLevelChunkTicks)this.fluidTicks).moonrise$isDirty(gameTime)) {
            return true;
        }

        return super.isUnsaved();
    }

    @Override
    public boolean tryMarkSaved() {
        if (!this.isUnsaved()) {
            return false;
        }
        ((ChunkSystemLevelChunkTicks)this.blockTicks).moonrise$clearDirty();
        ((ChunkSystemLevelChunkTicks)this.fluidTicks).moonrise$clearDirty();

        super.tryMarkSaved();

        return true;
    }
}
