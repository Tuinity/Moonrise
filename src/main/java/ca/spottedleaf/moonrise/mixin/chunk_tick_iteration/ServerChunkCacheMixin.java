package ca.spottedleaf.moonrise.mixin.chunk_tick_iteration;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin extends ChunkSource {

    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    @Final
    public ChunkMap chunkMap;


    @Unique
    private final SimpleThreadUnsafeRandom shuffleRandom = new SimpleThreadUnsafeRandom(0L);

    /**
     * @reason Use random implementation which does not use CAS and has a faster nextInt(int)
     *         function
     * @author Spottedleaf
     */
    @Redirect(
        method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Util;shuffle(Ljava/util/List;Lnet/minecraft/util/RandomSource;)V"
        )
    )
    private <T> void useBetterRandom(final List<T> list, final RandomSource randomSource) {
        this.shuffleRandom.setSeed(randomSource.nextLong());
        Util.shuffle(list, this.shuffleRandom);
    }

    /**
     * @reason Do not iterate over entire chunk holder map; additionally perform mid-tick chunk task execution
     * @author Spottedleaf
     */
    @Redirect(
        method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;forEachBlockTickingChunk(Ljava/util/function/Consumer;)V"
        )
    )
    private void iterateTickingChunksFaster(final ChunkMap instance, final Consumer<LevelChunk> consumer) {
        final ServerLevel world = this.level;
        final int randomTickSpeed = world.getGameRules().get(GameRules.RANDOM_TICK_SPEED);

        // TODO check on update: impl of forEachBlockTickingChunk will only iterate ENTITY ticking chunks!
        // TODO check on update: consumer just runs tickChunk
        final ReferenceList<LevelChunk> entityTickingChunks = ((ChunkSystemServerLevel)world).moonrise$getEntityTickingChunks();

        // note: we can use the backing array here because:
        // 1. we do not care about new additions
        // 2. _removes_ are impossible at this stage in the tick
        final LevelChunk[] raw = entityTickingChunks.getRawDataUnchecked();
        final int size = entityTickingChunks.size();

        Objects.checkFromToIndex(0, size, raw.length);
        for (int i = 0; i < size; ++i) {
            world.tickChunk(raw[i], randomTickSpeed);

            // call mid-tick tasks for chunk system
            if ((i & 7) == 0) {
                ((ChunkSystemMinecraftServer)this.level.getServer()).moonrise$executeMidTickTasks();
                continue;
            }
        }
    }
}
