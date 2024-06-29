package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.dfl.DefaultEntityLookup;
import ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemEntityGetter;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(Level.class)
public abstract class LevelMixin implements ChunkSystemLevel, ChunkSystemEntityGetter, LevelAccessor, AutoCloseable {

    @Shadow
    public abstract ProfilerFiller getProfiler();


    @Unique
    private EntityLookup entityLookup;

    @Override
    public final EntityLookup moonrise$getEntityLookup() {
        return this.entityLookup;
    }

    @Override
    public void moonrise$setEntityLookup(final EntityLookup entityLookup) {
        if (this.entityLookup != null && !(this.entityLookup instanceof DefaultEntityLookup)) {
            throw new IllegalStateException("Entity lookup already initialised");
        }
        this.entityLookup = entityLookup;
    }

    /**
     * @reason Default initialise entity lookup incase mods extend Level
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initHook(final CallbackInfo ci) {
        this.entityLookup = new DefaultEntityLookup((Level)(Object)this);
    }

    /**
     * @reason Route to faster lookup
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public List<Entity> getEntities(final Entity entity, final AABB boundingBox, final Predicate<? super Entity> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<Entity> ret = new ArrayList<>();

        ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(entity, boundingBox, ret, predicate);

        return ret;
    }

    /**
     * @reason Route to faster lookup
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Entity> void getEntities(final EntityTypeTest<Entity, T> entityTypeTest,
                                               final AABB boundingBox, final Predicate<? super T> predicate,
                                               final List<? super T> into, final int maxCount) {
        this.getProfiler().incrementCounter("getEntities");

        if (entityTypeTest instanceof EntityType<T> byType) {
            if (maxCount != Integer.MAX_VALUE) {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(byType, boundingBox, into, predicate, maxCount);
                return;
            } else {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(byType, boundingBox, into, predicate);
                return;
            }
        }

        if (entityTypeTest == null) {
            if (maxCount != Integer.MAX_VALUE) {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)predicate, maxCount);
                return;
            } else {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)predicate);
                return;
            }
        }

        final Class<? extends Entity> base = entityTypeTest.getBaseClass();

        final Predicate<? super T> modifiedPredicate;
        if (predicate == null) {
            modifiedPredicate = (final T obj) -> {
                return entityTypeTest.tryCast(obj) != null;
            };
        } else {
            modifiedPredicate = (final Entity obj) -> {
                final T casted = entityTypeTest.tryCast(obj);
                if (casted == null) {
                    return false;
                }

                return predicate.test(casted);
            };
        }

        if (base == null || base == Entity.class) {
            if (maxCount != Integer.MAX_VALUE) {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)modifiedPredicate, maxCount);
                return;
            } else {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)modifiedPredicate);
                return;
            }
        } else {
            if (maxCount != Integer.MAX_VALUE) {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(base, null, boundingBox, (List)into, (Predicate)modifiedPredicate, maxCount);
                return;
            } else {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(base, null, boundingBox, (List)into, (Predicate)modifiedPredicate);
                return;
            }
        }
    }

    /**
     * Route to faster lookup
     * @author Spottedleaf
     */
    @Override
    public final <T extends Entity> List<T> getEntitiesOfClass(final Class<T> entityClass, final AABB boundingBox, final Predicate<? super T> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<T> ret = new ArrayList<>();

        ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(entityClass, null, boundingBox, ret, predicate);

        return ret;
    }

    /**
     * Route to faster lookup
     * @author Spottedleaf
     */
    @Override
    public final List<Entity> moonrise$getHardCollidingEntities(final Entity entity, final AABB box, final Predicate<? super Entity> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<Entity> ret = new ArrayList<>();

        ((ChunkSystemLevel)this).moonrise$getEntityLookup().getHardCollidingEntities(entity, box, ret, predicate);

        return ret;
    }

    @Override
    public LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, false);
    }

    @Override
    public ChunkAccess moonrise$getAnyChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
    }

    @Override
    public ChunkAccess moonrise$getSpecificChunkIfLoaded(final int chunkX, final int chunkZ, final ChunkStatus leastStatus) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, leastStatus, false);
    }

    @Override
    public void moonrise$midTickTasks() {
        // no-op on ClientLevel
    }

    /**
     * @reason Allow block updates in non-ticking chunks, as new chunk system sends non-ticking chunks to clients
     * @author Spottedleaf
     */
    @Redirect(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/FullChunkStatus;isOrAfter(Lnet/minecraft/server/level/FullChunkStatus;)Z"
            )
    )
    private boolean sendUpdatesForFullChunks(final FullChunkStatus instance,
                                             final FullChunkStatus fullChunkStatus) {

        return instance.isOrAfter(FullChunkStatus.FULL);
    }

    // TODO: Thread.currentThread() != this.thread to TickThread?


    /**
     * @reason Execute mid-tick chunk tasks during entity ticking
     * @author Spottedleaf
     */
    @Inject(
            method = "guardEntityTick",
            at = @At(
                    value = "INVOKE",
                    shift = At.Shift.AFTER,
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
            )
    )
    private void midTickEntity(final CallbackInfo ci) {
        this.moonrise$midTickTasks();
    }
}
