package ca.spottedleaf.moonrise.mixin.entity_tracker;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DataFixer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;

@Mixin(ChunkMap.class)
abstract class ChunkMapMixin extends ChunkStorage implements ChunkHolder.PlayerProvider, GeneratingChunkMap {
    @Shadow
    @Final
    public ServerLevel level;

    public ChunkMapMixin(RegionStorageInfo regionStorageInfo, Path path, DataFixer dataFixer, boolean bl) {
        super(regionStorageInfo, path, dataFixer, bl);
    }

    /**
     * @reason The new tracker tick method will perform the necessary tracker updates.
     * @author Spottedleaf
     */
    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;hasNext()Z",
                    ordinal = 0
            )
    )
    private boolean skipMoveTrackerUpdate(final Iterator<?> iterator) {
        return false;
    }

    /**
     * @reason New entity tracker tick method which scales better.
     * @author Spottedleaf
     */
    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;hasNext()Z",
                    ordinal = 1
            )
    )
    private boolean newTrackerTick(final Iterator<?> iterator) {
        final ServerEntityLookup entityLookup = (ServerEntityLookup)((ChunkSystemServerLevel)this.level).moonrise$getEntityLookup();;

        final ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
        for (int i = 0, len = trackerEntities.size(); i < len; ++i) {
            final Entity entity = trackerEntitiesRaw[i];
            final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity)entity).moonrise$getTrackedEntity();
            if (tracker == null) {
                continue;
            }
            ((EntityTrackerTrackedEntity)tracker).moonrise$tick(((ChunkSystemEntity)entity).moonrise$getChunkData().nearbyPlayers);
            if (((EntityTrackerTrackedEntity)tracker).moonrise$hasPlayers()
                || ((ChunkSystemEntity)entity).moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                tracker.serverEntity.sendChanges();
            }
        }

        return false;
    }

    /**
     * @reason Update tracker field
     * @author Spottedleaf
     */
    @Inject(
            method = "addEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;put(ILjava/lang/Object;)Ljava/lang/Object;",
                    shift = At.Shift.AFTER
            )
    )
    private void addEntityTrackerField(final Entity entity, final CallbackInfo ci,
                                       @Local(ordinal = 0) final ChunkMap.TrackedEntity trackedEntity) {
        if (((EntityTrackerEntity)entity).moonrise$getTrackedEntity() != null) {
            throw new IllegalStateException("Entity is already tracked");
        }
        ((EntityTrackerEntity)entity).moonrise$setTrackedEntity(trackedEntity);
    }

    /**
     * @reason Update tracker field
     * @author Spottedleaf
     */
    @Inject(
            method = "removeEntity",
            at = @At(
                    value = "RETURN"
            )
    )
    private void removeEntityTrackerField(final Entity entity, final CallbackInfo ci) {
        ((EntityTrackerEntity)entity).moonrise$setTrackedEntity(null);
    }
}
