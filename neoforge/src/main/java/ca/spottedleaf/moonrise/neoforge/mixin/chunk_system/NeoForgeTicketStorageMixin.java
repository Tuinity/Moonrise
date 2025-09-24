package ca.spottedleaf.moonrise.neoforge.mixin.chunk_system;

import ca.spottedleaf.concurrentutil.map.ConcurrentLong2LongChainedHashTable;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketStorage;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketType;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TicketStorage.class)
abstract class NeoForgeTicketStorageMixin implements ChunkSystemTicketStorage {

    @Shadow
    private LongSet chunksWithForceNaturalSpawning;

    /**
     * @reason Destroy old chunk system state
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>(Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;)V",
        at = @At(
            value = "RETURN"
        )
    )
    private void destroyFields(Long2ObjectOpenHashMap p_393873_, Long2ObjectOpenHashMap p_394615_, CallbackInfo ci) {
        this.chunksWithForceNaturalSpawning = null;
    }

    /**
     * @reason The forced natural spawning chunks would be empty, as tickets should always be empty.
     *         We need to do this to avoid throwing immediately.
     * @author Spottedleaf
     */
    @Redirect(
        method = "<init>(Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/TicketStorage;updateForcedNaturalSpawning()V"
        )
    )
    private void avoidUpdatingForcedNaturalChunks(final TicketStorage instance) {}

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean shouldForceNaturalSpawning(final ChunkPos pos) {
        final ConcurrentLong2LongChainedHashTable counters = ((ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getChunkTaskScheduler()
            .chunkHolderManager.getTicketCounters(ChunkSystemTicketType.COUNTER_TYPER_NATURAL_SPAWNING_FORCED);

        if (counters == null || counters.isEmpty()) {
            return false;
        }

        return counters.containsKey(CoordinateUtils.getChunkKey(pos));
    }
}
