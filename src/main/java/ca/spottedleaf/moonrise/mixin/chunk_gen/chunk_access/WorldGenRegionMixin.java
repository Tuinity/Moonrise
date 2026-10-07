package ca.spottedleaf.moonrise.mixin.chunk_gen.chunk_access;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionMixin implements WorldGenLevel {

    @Shadow
    public abstract @Nullable ChunkAccess getChunk(final int chunkX, final int chunkZ, final ChunkStatus targetStatus, final boolean loadOrGenerate);

    @Unique
    private @Nullable ChunkAccess[] chunkCache;

    @Unique
    private int chunkCacheMinX;

    @Unique
    private int chunkCacheMinZ;

    @Unique
    private int chunkCacheWidth;

    /**
     * @reason Resolve the chunks at their maximum allowed status ahead of time, so that
     *         getChunk does not need to compute the dependency status for each call
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void initChunkCache(final ServerLevel level, final StaticCache2D<GenerationChunkHolder> cache,
                                final ChunkStep generatingStep, final ChunkAccess center, final CallbackInfo ci) {
        final ChunkDependencies dependencies = generatingStep.directDependencies();
        final int radius = dependencies.size() - 1;
        final int width = Math.max(0, 2 * radius + 1);
        final ChunkPos centerPos = center.getPos();
        final int minX = centerPos.x() - radius;
        final int minZ = centerPos.z() - radius;

        final ChunkAccess[] chunks = new ChunkAccess[width * width];

        for (int dz = -radius; dz <= radius; ++dz) {
            for (int dx = -radius; dx <= radius; ++dx) {
                final ChunkStatus maxAllowedStatus = dependencies.get(Math.max(Math.abs(dx), Math.abs(dz)));
                final GenerationChunkHolder holder = cache.get(centerPos.x() + dx, centerPos.z() + dz);

                chunks[(dx + radius) + (dz + radius) * width] = holder.getChunkIfPresentUnchecked(maxAllowedStatus);
            }
        }

        this.chunkCache = chunks;
        this.chunkCacheMinX = minX;
        this.chunkCacheMinZ = minZ;
        this.chunkCacheWidth = width;
    }

    @Override
    public ChunkAccess getChunk(final BlockPos pos) {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public ChunkAccess getChunk(final int chunkX, final int chunkZ, final ChunkStatus status) {
        if (status == ChunkStatus.EMPTY) {
            return this.getChunk(chunkX, chunkZ);
        } else {
            return this.getChunk(chunkX, chunkZ, status, true);
        }
    }

    /**
     * @reason We can skip the status checking logic, as it will never fail
     *         when status = EMPTY
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public ChunkAccess getChunk(final int chunkX, final int chunkZ) {
        final int relX = chunkX - this.chunkCacheMinX;
        final int relZ = chunkZ - this.chunkCacheMinZ;
        final int width = this.chunkCacheWidth;

        if (relX >= 0 && relZ >= 0 && relX < width && relZ < width) {
            final ChunkAccess ret = this.chunkCache[relX + relZ * width];
            if (ret != null) {
                return ret;
            }
        }
        // fall back to getChunk for error handling
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, true);
    }
}
