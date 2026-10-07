package ca.spottedleaf.moonrise.mixin.chunk_gen.chunk_access;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionMixin implements WorldGenLevel {

    @Shadow
    @Final
    private StaticCache2D<@Nullable ChunkAccess> cache;

    @Shadow
    public abstract @Nullable ChunkAccess getChunk(final int chunkX, final int chunkZ, final ChunkStatus targetStatus, final boolean loadOrGenerate);

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
        final ChunkAccess ret = this.cache.get(chunkX, chunkZ);
        if (ret != null) {
            return ret;
        }
        // fall back to getChunk for error handling
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, true);
    }
}
