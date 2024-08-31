package ca.spottedleaf.moonrise.neoforge;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.common.util.ConfigHolder;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import java.util.List;
import java.util.function.Predicate;

public final class NeoForgeHooks implements PlatformHooks {

    @Override
    public String getBrand() {
        return "Moonrise";
    }

    @Override
    public int getLightEmission(final BlockState blockState, final BlockGetter world, final BlockPos pos) {
        return blockState.getLightEmission(world, pos);
    }

    @Override
    public Predicate<BlockState> maybeHasLightEmission() {
        return (final BlockState state) -> {
            return state.hasDynamicLightEmission() || state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) != 0;
        };
    }

    @Override
    public void onExplosion(final Level world, final Explosion explosion, final List<Entity> possiblyAffecting, final double diameter) {
        EventHooks.onExplosionDetonate(world, explosion, possiblyAffecting, diameter);
    }

    @Override
    public Vec3 modifyExplosionKnockback(final Level world, final Explosion explosion, final Entity entity, final Vec3 original) {
        return EventHooks.getExplosionKnockback(world, explosion, entity, original);
    }

    @Override
    public boolean hasCurrentlyLoadingChunk() {
        return true;
    }

    @Override
    public LevelChunk getCurrentlyLoadingChunk(final GenerationChunkHolder holder) {
        return holder.currentlyLoading;
    }

    @Override
    public void setCurrentlyLoading(final GenerationChunkHolder holder, final LevelChunk levelChunk) {
        holder.currentlyLoading = levelChunk;
    }

    @Override
    public void chunkFullStatusComplete(final LevelChunk newChunk, final ProtoChunk original) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(newChunk, !(original instanceof ImposterProtoChunk)));
    }

    @Override
    public boolean allowAsyncTicketUpdates() {
        return false;
    }

    @Override
    public void onChunkHolderTicketChange(final ServerLevel world, final NewChunkHolder holder, final int oldLevel, final int newLevel) {
        EventHooks.fireChunkTicketLevelUpdated(
            world, CoordinateUtils.getChunkKey(holder.chunkX, holder.chunkZ),
            oldLevel, newLevel, holder.vanillaChunkHolder
        );
    }

    @Override
    public void chunkUnloadFromWorld(final LevelChunk chunk) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
    }

    @Override
    public void chunkSyncSave(final ServerLevel world, final ChunkAccess chunk, final CompoundTag data) {
        NeoForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, world, data));
    }

    @Override
    public void onChunkWatch(final ServerLevel world, final LevelChunk chunk, final ServerPlayer player) {
        EventHooks.fireChunkWatch(player, chunk, world);
    }

    @Override
    public void onChunkUnWatch(final ServerLevel world, final ChunkPos chunk, final ServerPlayer player) {
        EventHooks.fireChunkUnWatch(player, chunk, world);
    }

    @Override
    public void addToGetEntities(final Level world, final Entity entity, final AABB boundingBox, final Predicate<? super Entity> predicate,
                                 final List<Entity> into) {
        for (final PartEntity<?> part : world.getPartEntities()) {
            if (part != entity && part.getBoundingBox().intersects(boundingBox) && (predicate == null || predicate.test(part))) {
                into.add(part);
            }
        }
    }

    @Override
    public <T extends Entity> void addToGetEntities(final Level world, final EntityTypeTest<Entity, T> entityTypeTest, final AABB boundingBox,
                                                    final Predicate<? super T> predicate, final List<? super T> into, final int maxCount) {
        if (into.size() >= maxCount) {
            // fix neoforge issue: do not add if list is already full
            return;
        }

        for (final PartEntity<?> part : world.getPartEntities()) {
            final T casted = (T)entityTypeTest.tryCast(part);
            if (casted != null && casted.getBoundingBox().intersects(boundingBox) && (predicate == null || predicate.test(casted))) {
                into.add(casted);
                if (into.size() >= maxCount) {
                    break;
                }
            }
        }
    }

    @Override
    public void entityMove(final Entity entity, final long oldSection, final long newSection) {
        CommonHooks.onEntityEnterSection(entity, oldSection, newSection);
    }

    @Override
    public boolean screenEntity(final ServerLevel world, final Entity entity, final boolean fromDisk, final boolean event) {
        if (event && NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(entity, entity.level(), fromDisk)).isCanceled()) {
            return false;
        }
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
    public long configAutoSaveInterval() {
        return ConfigHolder.getConfig().chunkSaving.autoSaveInterval.getTimeTicks();
    }

    @Override
    public int configMaxAutoSavePerTick() {
        return ConfigHolder.getConfig().chunkSaving.maxAutoSaveChunksPerTick;
    }

    @Override
    public boolean configFixMC159283() {
        return ConfigHolder.getConfig().bugFixes.fixMC159283;
    }
}
