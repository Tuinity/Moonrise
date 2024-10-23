package ca.spottedleaf.moonrise.mixin.starlight.lightengine;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Mixin(ThreadedLevelLightEngine.class)
abstract class ThreadedLevelLightEngineMixin extends LevelLightEngine implements StarLightLightingProvider {

    @Shadow
    private ProcessorMailbox<Runnable> taskMailbox;

    @Shadow
    private ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> sorterMailbox;

    public ThreadedLevelLightEngineMixin(final LightChunkGetter chunkProvider, final boolean hasBlockLight, final boolean hasSkyLight) {
        super(chunkProvider, hasBlockLight, hasSkyLight);
    }

    @Unique
    private final AtomicLong chunkWorkCounter = new AtomicLong();

    @Unique
    private void queueTaskForSection(final int chunkX, final int chunkY, final int chunkZ,
                                     final Supplier<StarLightInterface.LightQueue.ChunkTasks> supplier) {
        final ServerLevel world = (ServerLevel)this.getLightEngine().getWorld();

        final ChunkAccess center = this.getLightEngine().getAnyChunkNow(chunkX, chunkZ);
        if (center == null || !center.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT)) {
            // do not accept updates in unlit chunks, unless we might be generating a chunk
            return;
        }

        final StarLightInterface.ServerLightQueue.ServerChunkTasks scheduledTask = (StarLightInterface.ServerLightQueue.ServerChunkTasks)supplier.get();

        if (scheduledTask == null) {
            // not scheduled
            return;
        }

        if (!scheduledTask.markTicketAdded()) {
            // ticket already added
            return;
        }

        final Long ticketId = Long.valueOf(this.chunkWorkCounter.getAndIncrement());
        final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        world.getChunkSource().addRegionTicket(StarLightInterface.CHUNK_WORK_TICKET, pos, StarLightInterface.REGION_LIGHT_TICKET_LEVEL, ticketId);

        scheduledTask.queueOrRunTask(() -> {
            world.getChunkSource().removeRegionTicket(StarLightInterface.CHUNK_WORK_TICKET, pos, StarLightInterface.REGION_LIGHT_TICKET_LEVEL, ticketId);
        });
    }

    @Override
    public final int serverRelightChunks(final Collection<ChunkPos> chunks0,
                                         final Consumer<ChunkPos> chunkLightCallback,
                                         final IntConsumer onComplete) {
        final Set<ChunkPos> chunks = new LinkedHashSet<>(chunks0);
        final Map<ChunkPos, Long> ticketIds = new HashMap<>();
        final ServerLevel world = (ServerLevel)this.getLightEngine().getWorld();

        for (final Iterator<ChunkPos> iterator = chunks.iterator(); iterator.hasNext();) {
            final ChunkPos pos = iterator.next();

            final Long id = ChunkTaskScheduler.getNextChunkRelightId();
            world.getChunkSource().addRegionTicket(ChunkTaskScheduler.CHUNK_RELIGHT, pos, StarLightInterface.REGION_LIGHT_TICKET_LEVEL, id);
            ticketIds.put(pos, id);

            final ChunkAccess chunk = (ChunkAccess)world.getChunkSource().getChunkForLighting(pos.x, pos.z);
            if (chunk == null || !chunk.isLightCorrect() || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT)) {
                // cannot relight this chunk
                iterator.remove();
                ticketIds.remove(pos);
                world.getChunkSource().removeRegionTicket(ChunkTaskScheduler.CHUNK_RELIGHT, pos, StarLightInterface.REGION_LIGHT_TICKET_LEVEL, id);
                continue;
            }
        }

        ((ChunkSystemServerLevel)world).moonrise$getChunkTaskScheduler().radiusAwareScheduler.queueInfiniteRadiusTask(() -> {
            ThreadedLevelLightEngineMixin.this.getLightEngine().relightChunks(
                    chunks,
                    (final ChunkPos pos) -> {
                        if (chunkLightCallback != null) {
                            chunkLightCallback.accept(pos);
                        }

                        ((ChunkSystemServerLevel)world).moonrise$getChunkTaskScheduler().scheduleChunkTask(pos.x, pos.z, () -> {
                            final NewChunkHolder chunkHolder = ((ChunkSystemServerLevel)world).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(
                                    pos.x, pos.z
                            );

                            if (chunkHolder == null) {
                                return;
                            }

                            final List<ServerPlayer> players = ((ChunkSystemChunkHolder)chunkHolder.vanillaChunkHolder).moonrise$getPlayers(false);

                            if (players.isEmpty()) {
                                return;
                            }

                            final Packet<?> relightPacket = new ClientboundLightUpdatePacket(
                                    pos, (ThreadedLevelLightEngine)(Object)ThreadedLevelLightEngineMixin.this,
                                    null, null
                            );

                            for (final ServerPlayer player : players) {
                                final ServerGamePacketListenerImpl conn = player.connection;
                                if (conn != null) {
                                    conn.send(relightPacket);
                                }
                            }
                        });
                    },
                    (final int relight) -> {
                        if (onComplete != null) {
                            onComplete.accept(relight);
                        }

                        for (final Map.Entry<ChunkPos, Long> entry : ticketIds.entrySet()) {
                            world.getChunkSource().removeRegionTicket(
                                    ChunkTaskScheduler.CHUNK_RELIGHT, entry.getKey(),
                                    StarLightInterface.REGION_LIGHT_TICKET_LEVEL, entry.getValue()
                            );
                        }
                    }
            );
        });

        return chunks.size();
    }

    /**
     * @reason Destroy old chunk system hook
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initHook(final CallbackInfo ci) {
        this.taskMailbox = null;
        this.sorterMailbox = null;
    }

    /**
     * @reason Destroy old chunk system hook
     * @author Spottedleaf
     */
    @Overwrite
    public void addTask(final int x, final int z, final ThreadedLevelLightEngine.TaskType type,
                         final Runnable task) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hook
     * @author Spottedleaf
     */
    @Overwrite
    public void addTask(final int x, final int z, final IntSupplier ticketLevelSupplier,
                        final ThreadedLevelLightEngine.TaskType type, final Runnable task) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system schedules light tasks immediately
     * @author Spottedleaf
     */
    @Overwrite
    public void tryScheduleUpdate() {}

    /**
     * @reason Destroy old chunk system hook
     * @author Spottedleaf
     */
    @Overwrite
    public void runUpdate() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Redirect scheduling call away from the vanilla light engine, as well as enforce
     * that chunk neighbours are loaded before the processing can occur
     * @author Spottedleaf
     */
    @Overwrite
    public void checkBlock(final BlockPos pos) {
        final BlockPos posCopy = pos.immutable();
        this.queueTaskForSection(posCopy.getX() >> 4, posCopy.getY() >> 4, posCopy.getZ() >> 4, () -> {
            return ThreadedLevelLightEngineMixin.this.getLightEngine().blockChange(posCopy);
        });
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void updateChunkStatus(final ChunkPos pos) {}

    /**
     * @reason Redirect to schedule for our own logic, as well as ensure 1 radius neighbours
     * are loaded
     * Note: Our scheduling logic will discard this call if the chunk is not lit, unloaded, or not at LIGHT stage yet.
     * @author Spottedleaf
     */
    @Overwrite
    public void updateSectionStatus(final SectionPos pos, final boolean notReady) {
        this.queueTaskForSection(pos.getX(), pos.getY(), pos.getZ(), () -> {
            return ThreadedLevelLightEngineMixin.this.getLightEngine().sectionChange(pos, notReady);
        });
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void propagateLightSources(final ChunkPos pos) {
        // handled by light()
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void setLightEnabled(final ChunkPos pos, final boolean lightEnabled) {
        // light impl does not need to do this
    }

    /**
     * @reason Light data is now attached to chunks, and this means we need to hook into chunk loading logic
     * to load the data rather than rely on this call. This call also would mess with the vanilla light engine state.
     * @author Spottedleaf
     */
    @Overwrite
    public void queueSectionData(final LightLayer lightType, final SectionPos pos, final @Nullable DataLayer nibbles) {
        // load hooks inside ChunkSerializer
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void retainData(final ChunkPos pos, final boolean retainData) {
        // light impl does not need to do this
    }

    /**
     * @reason Starlight does not have to do this
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> initializeLight(final ChunkAccess chunk, final boolean lit) {
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * @reason Chunk system patch replaces the vanilla scheduling entirely
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> lightChunk(final ChunkAccess chunk, final boolean lit) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<?> waitForPendingTasks(final int chunkX, final int chunkZ) {
        throw new UnsupportedOperationException();
    }
}
