package ca.spottedleaf.moonrise.mixin.chunk_tick_iteration;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.common.util.SimpleRandom;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk;
import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickServerLevel;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.Util;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin extends ChunkSource {

    @Shadow
    @Final
    public ServerLevel level;

    @Unique
    private ServerChunkCache.ChunkAndHolder[] iterationCopy;

    @Unique
    private int iterationCopyLen;

    @Unique
    private final SimpleRandom shuffleRandom = new SimpleRandom(0L);

    /**
     * @reason Avoid creating the list, which is sized at the chunkholder count. The actual number of ticking
     *         chunks is always lower. The mixin below will initialise the list to non-null.
     * @author Spottedleaf
     */
    @Redirect(
            method = "tickChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayListWithCapacity(I)Ljava/util/ArrayList;"
            )
    )
    private <T> ArrayList<T> avoidListCreation(final int initialArraySize) {
        return null;
    }

    /**
     * @reason Initialise the list to contain only the ticking chunks.
     * @author Spottedleaf
     */
    @ModifyVariable(
            method = "tickChunks",
            at = @At(
                    value = "STORE",
                    opcode = Opcodes.ASTORE,
                    ordinal = 0
            )
    )
    private List<ServerChunkCache.ChunkAndHolder> initTickChunks(final List<ServerChunkCache.ChunkAndHolder> shouldBeNull) {
        final ReferenceList<ServerChunkCache.ChunkAndHolder> tickingChunks =
                ((ChunkTickServerLevel)this.level).moonrise$getPlayerTickingChunks();

        final ServerChunkCache.ChunkAndHolder[] raw = tickingChunks.getRawDataUnchecked();
        final int size = tickingChunks.size();

        if (this.iterationCopy == null || this.iterationCopy.length < size) {
            this.iterationCopy = new ServerChunkCache.ChunkAndHolder[raw.length];
        }
        this.iterationCopyLen = size;
        System.arraycopy(raw, 0, this.iterationCopy, 0, size);

        return ObjectArrayList.wrap(
                this.iterationCopy, size
        );
    }

    /**
     * @reason Use random implementation which does not use CAS and has a faster nextInt(int)
     *         function
     * @author Spottedleaf
     */
    @Redirect(
        method = "tickChunks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/Util;shuffle(Ljava/util/List;Lnet/minecraft/util/RandomSource;)V"
        )
    )
    private <T> void useBetterRandom(final List<T> list, final RandomSource randomSource) {
        this.shuffleRandom.setSeed(randomSource.nextLong());
        Util.shuffle(list, this.shuffleRandom);
    }

    /**
     * @reason Do not initialise ticking chunk list, as we did that above.
     * @author Spottedleaf
     */
    @Redirect(
            method = "tickChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;hasNext()Z",
                    ordinal = 0
            )
    )
    private <E> boolean skipTickAdd(final Iterator<E> instance) {
        return false;
    }

    /**
     * @reason Use the nearby players cache
     * @author Spottedleaf
     */
    @Redirect(
        method = "tickChunks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z"
        )
    )
    private boolean useNearbyCache(final ChunkMap instance, final ChunkPos chunkPos,
                                   @Local(ordinal = 0, argsOnly = false) final LevelChunk levelChunk) {
        final ChunkData chunkData =
            ((ChunkSystemChunkHolder)((ChunkSystemLevelChunk)levelChunk).moonrise$getChunkAndHolder().holder())
                .moonrise$getRealChunkHolder().holderData;
        final NearbyPlayers.TrackedChunk nearbyPlayers = chunkData.nearbyPlayers;
        if (nearbyPlayers == null) {
            return false;
        }

        final ReferenceList<ServerPlayer> players = nearbyPlayers.getPlayers(NearbyPlayers.NearbyMapType.SPAWN_RANGE);

        if (players == null) {
            return false;
        }

        final ServerPlayer[] raw = players.getRawDataUnchecked();
        final int len = players.size();

        Objects.checkFromIndexSize(0, len, raw.length);
        for (int i = 0; i < len; ++i) {
            if (instance.playerIsCloseEnoughForSpawning(raw[i], chunkPos)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @reason Clear the iteration array after the list is done being used.
     * @author Spottedleaf
     */
    @Inject(
            method = "tickChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void broadcastChanges(final CallbackInfo ci) {
        Arrays.fill(this.iterationCopy, 0, this.iterationCopyLen, null);
    }
}
