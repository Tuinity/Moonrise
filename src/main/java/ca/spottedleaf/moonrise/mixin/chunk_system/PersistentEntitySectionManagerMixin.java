package ca.spottedleaf.moonrise.mixin.chunk_system;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.Writer;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Mixin(PersistentEntitySectionManager.class)
abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> {
    @Mutable @Shadow @Final EntitySectionStorage<T> sectionStorage;
    @Mutable @Shadow @Final private LevelEntityGetter<T> entityGetter;
    @Mutable @Shadow @Final LevelCallback<T> callbacks;
    @Mutable @Shadow @Final private EntityPersistentStorage<T> permanentStorage;
    @Mutable @Shadow @Final Set<UUID> knownUuids;
    @Mutable @Shadow @Final private Long2ObjectMap<Visibility> chunkVisibility;
    @Mutable @Shadow @Final private Long2ObjectMap<?> chunkLoadStatuses;
    @Mutable @Shadow @Final private LongSet chunksToUnload;
    @Mutable @Shadow @Final private Queue<ChunkEntities<T>> loadingInbox;
    @Mutable @Shadow @Final private EntityLookup<T> visibleEntityStorage;

    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void destroyFields(final CallbackInfo ci) {
        this.sectionStorage = null;
        this.entityGetter = null;
        this.callbacks = null;
        this.permanentStorage = null;
        this.knownUuids = null;
        this.chunkVisibility = null;
        this.chunkLoadStatuses = null;
        this.chunksToUnload = null;
        this.loadingInbox = null;
        this.visibleEntityStorage = null;
    }
    @Inject(
        method = "addNewEntity",
        at = @At("HEAD")
    )
    private void addNewEntity(T access, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "addEntity",
        at = @At("HEAD")
    )
    private void addEntity(T access, boolean worldGenSpawned, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "removeSectionIfEmpty",
        at = @At("HEAD")
    )
    private void removeSectionIfEmpty(long sectionKey, EntitySection<T> section, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "addEntityUuid",
        at = @At("HEAD")
    )
    private void addEntityUuid(T access, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "getEffectiveStatus",
        at = @At("HEAD")
    )
    private static <T extends EntityAccess> void getEffectiveStatus(T entity, Visibility visibility, CallbackInfoReturnable<Visibility> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "isTicking",
        at = @At("HEAD")
    )
    private void isTicking(ChunkPos chunkPos, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "addLegacyChunkEntities",
        at = @At("HEAD")
    )
    private void addLegacyChunkEntities(Stream<T> entities, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "addWorldGenChunkEntities",
        at = @At("HEAD")
    )
    private void addWorldGenChunkEntities(Stream<T> entities, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "startTicking",
        at = @At("HEAD")
    )
    private void startTicking(T entity, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "stopTicking",
        at = @At("HEAD")
    )
    private void stopTicking(T entity, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "startTracking",
        at = @At("HEAD")
    )
    private void startTracking(T entity, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "stopTracking",
        at = @At("HEAD")
    )
    private void stopTracking(T entity, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V",
        at = @At("HEAD")
    )
    private void updateChunkStatus(ChunkPos chunkPos, Visibility visibility, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/FullChunkStatus;)V",
        at = @At("HEAD")
    )
    private void updateChunkStatus(ChunkPos chunkPos, FullChunkStatus status, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "ensureChunkQueuedForLoad",
        at = @At("HEAD")
    )
    private void ensureChunkQueuedForLoad(long chunkPosValue, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "storeChunkSections",
        at = @At("HEAD")
    )
    private void storeChunkSections(long chunkPosValue, Consumer<T> entityAction, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "requestChunkLoad",
        at = @At("HEAD")
    )
    private void requestChunkLoad(long chunkPosValue, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "processChunkUnload",
        at = @At("HEAD")
    )
    private void processChunkUnload(long chunkPosValue, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "unloadEntity",
        at = @At("HEAD")
    )
    private void unloadEntity(T entity, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "processUnloads",
        at = @At("HEAD")
    )
    private void processUnloads(CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "processPendingLoads",
        at = @At("HEAD")
    )
    private void processPendingLoads(CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void tick(CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "getAllChunksToSave",
        at = @At("HEAD")
    )
    private void getAllChunksToSave(CallbackInfoReturnable<LongSet> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "autoSave",
        at = @At("HEAD")
    )
    private void autoSave(CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "saveAll",
        at = @At("HEAD")
    )
    private void saveAll(CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "close",
        at = @At("HEAD")
    )
    private void close(CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "isLoaded",
        at = @At("HEAD")
    )
    private void isLoaded(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "getEntityGetter",
        at = @At("HEAD")
    )
    public void getEntityGetter(CallbackInfoReturnable<LevelEntityGetter<T>> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "canPositionTick(Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD")
    )
    public void canPositionTick(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "canPositionTick(Lnet/minecraft/world/level/ChunkPos;)Z",
        at = @At("HEAD")
    )
    public void canPositionTick(ChunkPos chunkPos, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "areEntitiesLoaded(J)Z",
        at = @At("HEAD")
    )
    public void areEntitiesLoaded(long chunkPos, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "dumpSections",
        at = @At("HEAD")
    )
    public void dumpSections(Writer writer, CallbackInfo ci) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "gatherStats",
        at = @At("HEAD")
    )
    public void gatherStats(CallbackInfoReturnable<String> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "count",
        at = @At("HEAD")
    )
    public void count(CallbackInfoReturnable<Integer> cir) {
        throw new UnsupportedOperationException();
    }
}
