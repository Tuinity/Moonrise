package ca.spottedleaf.moonrise.common;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Predicate;

public interface PlatformHooks {
    public static PlatformHooks get() {
        return Holder.INSTANCE;
    }

    public String getBrand();

    public int getLightEmission(final BlockState blockState, final BlockGetter world, final BlockPos pos);

    public Predicate<BlockState> maybeHasLightEmission();

    public void onExplosion(final Level world, final Explosion explosion, final List<Entity> possiblyAffecting, final double diameter);

    public Vec3 modifyExplosionKnockback(final Level world, final Explosion explosion, final Entity entity, final Vec3 original);

    public boolean hasCurrentlyLoadingChunk();

    public LevelChunk getCurrentlyLoadingChunk(final GenerationChunkHolder holder);

    public void setCurrentlyLoading(final GenerationChunkHolder holder, final LevelChunk levelChunk);

    public void chunkFullStatusComplete(final LevelChunk newChunk, final ProtoChunk original);

    public boolean allowAsyncTicketUpdates();

    public void onChunkHolderTicketChange(final ServerLevel world, final NewChunkHolder holder, final int oldLevel, final int newLevel);

    public void chunkUnloadFromWorld(final LevelChunk chunk);

    public void chunkSyncSave(final ServerLevel world, final ChunkAccess chunk, final CompoundTag data);

    public void onChunkWatch(final ServerLevel world, final LevelChunk chunk, final ServerPlayer player);

    public void onChunkUnWatch(final ServerLevel world, final ChunkPos chunk, final ServerPlayer player);

    public void addToGetEntities(final Level world, final Entity entity, final AABB boundingBox, final Predicate<? super Entity> predicate,
                                 final List<Entity> into);

    public <T extends Entity> void addToGetEntities(final Level world, final EntityTypeTest<Entity, T> entityTypeTest,
                                                    final AABB boundingBox, final Predicate<? super T> predicate,
                                                    final List<? super T> into, final int maxCount);

    public void entityMove(final Entity entity, final long oldSection, final long newSection);

    public boolean screenEntity(final ServerLevel world, final Entity entity, final boolean fromDisk, final boolean event);

    public boolean configFixMC224294();

    public boolean configAutoConfigSendDistance();

    public double configPlayerMaxLoadRate();

    public double configPlayerMaxGenRate();

    public double configPlayerMaxSendRate();

    public int configPlayerMaxConcurrentLoads();

    public int configPlayerMaxConcurrentGens();

    public long configAutoSaveInterval();

    public int configMaxAutoSavePerTick();

    public static final class Holder {
        private Holder() {
        }

        private static final PlatformHooks INSTANCE;

        static {
            INSTANCE = ServiceLoader.load(PlatformHooks.class, PlatformHooks.class.getClassLoader()).findFirst()
                    .orElseThrow(() -> new RuntimeException("Failed to locate PlatformHooks"));
        }
    }
}
