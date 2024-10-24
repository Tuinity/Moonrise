package ca.spottedleaf.moonrise.fabric;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.common.util.ConfigHolder;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.function.Predicate;

public final class FabricHooks implements PlatformHooks {

    private static final boolean HAS_FABRIC_LIFECYCLE_EVENTS = FabricLoader.getInstance().isModLoaded("fabric-lifecycle-events-v1");

    @Override
    public String getBrand() {
        return "Moonrise";
    }

    @Override
    public int getLightEmission(final BlockState blockState, final BlockGetter world, final BlockPos pos) {
        return blockState.getLightEmission();
    }

    @Override
    public Predicate<BlockState> maybeHasLightEmission() {
        return (final BlockState state) -> {
            return state.getLightEmission() != 0;
        };
    }

    @Override
    public boolean hasCurrentlyLoadingChunk() {
        return false;
    }

    @Override
    public LevelChunk getCurrentlyLoadingChunk(final GenerationChunkHolder holder) {
        return null;
    }

    @Override
    public void setCurrentlyLoading(final GenerationChunkHolder holder, final LevelChunk levelChunk) {

    }

    @Override
    public void chunkFullStatusComplete(final LevelChunk newChunk, final ProtoChunk original) {
        if (HAS_FABRIC_LIFECYCLE_EVENTS) {
            ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad((ServerLevel)newChunk.getLevel(), newChunk);
        }
    }

    @Override
    public boolean allowAsyncTicketUpdates() {
        return false;
    }

    @Override
    public void onChunkHolderTicketChange(final ServerLevel world, final ChunkHolder holder, final int oldLevel, final int newLevel) {

    }

    @Override
    public void chunkUnloadFromWorld(final LevelChunk chunk) {
        if (HAS_FABRIC_LIFECYCLE_EVENTS) {
            ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload((ServerLevel)chunk.getLevel(), chunk);
        }
    }

    @Override
    public void chunkSyncSave(final ServerLevel world, final ChunkAccess chunk, final SerializableChunkData data) {

    }

    @Override
    public void onChunkWatch(final ServerLevel world, final LevelChunk chunk, final ServerPlayer player) {

    }

    @Override
    public void onChunkUnWatch(final ServerLevel world, final ChunkPos chunk, final ServerPlayer player) {

    }

    @Override
    public void addToGetEntities(final Level world, final Entity entity, final AABB boundingBox, final Predicate<? super Entity> predicate,
                                 final List<Entity> into) {

    }

    @Override
    public <T extends Entity> void addToGetEntities(final Level world, final EntityTypeTest<Entity, T> entityTypeTest, final AABB boundingBox,
                                                    final Predicate<? super T> predicate, final List<? super T> into, final int maxCount) {

    }

    @Override
    public void entityMove(final Entity entity, final long oldSection, final long newSection) {

    }

    @Override
    public boolean screenEntity(final ServerLevel world, final Entity entity, final boolean fromDisk, final boolean event) {
        return true;
    }

    @Override
    public boolean configFixMC224294() {
        return ConfigHolder.getConfig().bugFixes.fixMC224294;
    }

    @Override
    public boolean configAutoConfigSendDistance() {
        return ConfigHolder.getConfig().chunkLoading.advanced.autoConfigSendDistance;
    }

    @Override
    public double configPlayerMaxLoadRate() {
        return ConfigHolder.getConfig().chunkLoading.basic.playerMaxLoadRate;
    }

    @Override
    public double configPlayerMaxGenRate() {
        return ConfigHolder.getConfig().chunkLoading.basic.playerMaxGenRate;
    }

    @Override
    public double configPlayerMaxSendRate() {
        return ConfigHolder.getConfig().chunkLoading.basic.playerMaxSendRate;
    }

    @Override
    public int configPlayerMaxConcurrentLoads() {
        return ConfigHolder.getConfig().chunkLoading.advanced.playerMaxConcurrentChunkLoads;
    }

    @Override
    public int configPlayerMaxConcurrentGens() {
        return ConfigHolder.getConfig().chunkLoading.advanced.playerMaxConcurrentChunkGenerates;
    }

    @Override
    public long configAutoSaveInterval(final ServerLevel world) {
        return ConfigHolder.getConfig().chunkSaving.autoSaveInterval.getTimeTicks();
    }

    @Override
    public int configMaxAutoSavePerTick(final ServerLevel world) {
        return ConfigHolder.getConfig().chunkSaving.maxAutoSaveChunksPerTick;
    }

    @Override
    public boolean configFixMC159283() {
        return ConfigHolder.getConfig().bugFixes.fixMC159283;
    }

    @Override
    public boolean forceNoSave(final ChunkAccess chunk) {
        return false;
    }

    @Override
    public CompoundTag convertNBT(final DSL.TypeReference type, final DataFixer dataFixer, final CompoundTag nbt,
                                  final int fromVersion, final int toVersion) {
        return (CompoundTag)dataFixer.update(
            type, new Dynamic<>(NbtOps.INSTANCE, nbt), fromVersion, toVersion
        ).getValue();
    }

    @Override
    public boolean hasMainChunkLoadHook() {
        return false;
    }

    @Override
    public void mainChunkLoad(final ChunkAccess chunk, final SerializableChunkData chunkData) {

    }

    @Override
    public List<Entity> modifySavedEntities(final ServerLevel world, final int chunkX, final int chunkZ, final List<Entity> entities) {
        return entities;
    }

    @Override
    public void unloadEntity(final Entity entity) {
        entity.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
    }

    @Override
    public void postLoadProtoChunk(final ServerLevel world, final ProtoChunk chunk) {
        ChunkStatusTasks.postLoadProtoChunk(world, chunk.getEntities());
    }

    @Override
    public int modifyEntityTrackingRange(final Entity entity, final int currentRange) {
        return currentRange;
    }
}
