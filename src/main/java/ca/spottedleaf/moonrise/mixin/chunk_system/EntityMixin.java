package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Mixin(Entity.class)
abstract class EntityMixin implements ChunkSystemEntity {

    @Shadow
    private ImmutableList<Entity> passengers;

    @Shadow
    protected abstract Stream<Entity> getIndirectPassengersStream();

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    private Level level;

    @Shadow
    @Nullable
    private Entity.RemovalReason removalReason;


    @Unique
    private final boolean isHardColliding = this.moonrise$isHardCollidingUncached();

    @Unique
    private FullChunkStatus chunkStatus;

    @Unique
    private ChunkData chunkData;

    @Unique
    private int sectionX = Integer.MIN_VALUE;

    @Unique
    private int sectionY = Integer.MIN_VALUE;

    @Unique
    private int sectionZ = Integer.MIN_VALUE;

    @Unique
    private boolean updatingSectionStatus;

    @Override
    public final boolean moonrise$isHardColliding() {
        return this.isHardColliding;
    }

    @Override
    public final FullChunkStatus moonrise$getChunkStatus() {
        return this.chunkStatus;
    }

    @Override
    public final void moonrise$setChunkStatus(final FullChunkStatus status) {
        this.chunkStatus = status;
    }

    @Override
    public final ChunkData moonrise$getChunkData() {
        return this.chunkData;
    }

    @Override
    public final void moonrise$setChunkData(final ChunkData chunkData) {
        this.chunkData = chunkData;
    }

    @Override
    public final int moonrise$getSectionX() {
        return this.sectionX;
    }

    @Override
    public final void moonrise$setSectionX(final int x) {
        this.sectionX = x;
    }

    @Override
    public final int moonrise$getSectionY() {
        return this.sectionY;
    }

    @Override
    public final void moonrise$setSectionY(final int y) {
        this.sectionY = y;
    }

    @Override
    public final int moonrise$getSectionZ() {
        return this.sectionZ;
    }

    @Override
    public final void moonrise$setSectionZ(final int z) {
        this.sectionZ = z;
    }

    @Override
    public final boolean moonrise$isUpdatingSectionStatus() {
        return this.updatingSectionStatus;
    }

    @Override
    public final void moonrise$setUpdatingSectionStatus(final boolean to) {
        this.updatingSectionStatus = to;
    }

    @Override
    public final boolean moonrise$hasAnyPlayerPassengers() {
        if (this.passengers.isEmpty()) {
            return false;
        }
        return this.getIndirectPassengersStream().anyMatch((entity) -> entity instanceof Player);
    }

    /**
     * @reason Stop bad mods from moving entities during section status updates, which otherwise would cause CMEs
     * @author Spottedleaf
     */
    @Inject(
            method = "setPosRaw",
            cancellable = true,
            at = @At(
                    value = "HEAD"
            )
    )
    private void checkUpdatingStatusPoi(final double x, final double y, final double z, final CallbackInfo ci) {
        if (this.updatingSectionStatus) {
            LOGGER.error(
                    "Refusing to update position for entity " + this + " to position " + new Vec3(x, y, z)
                    + " since it is processing a section status update", new Throwable()
            );
            ci.cancel();
            return;
        }
    }

    /**
     * @reason Stop bad mods from removing entities during section status updates, which otherwise would cause CMEs
     * @author Spottedleaf
     */
    @Inject(
            method = "setRemoved",
            cancellable = true,
            at = @At(
                    value = "HEAD"
            )
    )
    private void checkCanRemove(final CallbackInfo ci) {
        if (!((ChunkSystemLevel)this.level).moonrise$getEntityLookup().canRemoveEntity((Entity)(Object)this)) {
            LOGGER.warn("Entity " + this + " is currently prevented from being removed from the world since it is processing section status updates", new Throwable());
            ci.cancel();
            return;
        }
    }

    /**
     * @reason Don't adjust passenger state when unloading, it's just not safe (and messes with our logic in entity chunk unload)
     * @author Spottedleaf
     */
    @Redirect(
            method = "setRemoved",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"
            )
    )
    private void avoidDismountOnUnload(final List<Entity> instance, final Consumer<? super Entity> consumer) {
        if (this.removalReason == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            return;
        }

        instance.forEach(consumer);
    }

    /**
     * @reason We should not save entities with any player passengers
     * @author Spottedleaf
     */
    @Redirect(
            method = "shouldBeSaved",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hasExactlyOnePlayerPassenger()Z"
            )
    )
    private boolean properlyCheckPlayers(final Entity instance) {
        return ((ChunkSystemEntity)instance).moonrise$hasAnyPlayerPassengers();
    }
}
