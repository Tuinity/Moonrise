package ca.spottedleaf.moonrise.compat.lithium;

import net.caffeinemc.mods.lithium.common.block.entity.SleepingBlockEntity;
import net.caffeinemc.mods.lithium.common.world.chunk.ChunkStatusTracker;
import net.caffeinemc.mods.lithium.mixin.world.block_entity_ticking.sleeping.WrappedBlockEntityTickInvokerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public final class LithiumHooks {
    private LithiumHooks() {}

    public static void onChunkInaccessible(final ServerLevel serverLevel, final ChunkPos pos) {
        ChunkStatusTracker.onChunkInaccessible(serverLevel, pos);
    }

    public static void onChunkAccessible(final ServerLevel serverLevel, final LevelChunk chunk) {
        ChunkStatusTracker.onChunkAccessible(serverLevel, chunk);
    }

    public static boolean isSleeping(final TickingBlockEntity ticker) {
        return ticker instanceof WrappedBlockEntityTickInvokerAccessor wrapper
            && wrapper.getWrapped() == SleepingBlockEntity.SLEEPING_BLOCK_ENTITY_TICKER;
    }
}
