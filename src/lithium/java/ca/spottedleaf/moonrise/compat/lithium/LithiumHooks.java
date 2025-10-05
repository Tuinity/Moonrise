package ca.spottedleaf.moonrise.compat.lithium;

import net.caffeinemc.mods.lithium.common.world.chunk.ChunkStatusTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class LithiumHooks {
    private LithiumHooks() {}

    public static void onChunkInaccessible(final ServerLevel serverLevel, final ChunkPos pos) {
        ChunkStatusTracker.onChunkInaccessible(serverLevel, pos);
    }

    public static void onChunkAccessible(final ServerLevel serverLevel, final LevelChunk chunk) {
        ChunkStatusTracker.onChunkAccessible(serverLevel, chunk);
    }
}
