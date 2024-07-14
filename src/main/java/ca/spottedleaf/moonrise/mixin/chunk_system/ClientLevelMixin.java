package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.client.ClientEntityLookup;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Supplier;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin extends Level implements ChunkSystemLevel {

    @Shadow
    private TransientEntitySectionManager<Entity> entityStorage;

    @Shadow
    @Final
    private ClientChunkCache chunkSource;

    protected ClientLevelMixin(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder<DimensionType> holder, Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l, int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, supplier, bl, bl2, l, i);
    }

    /**
     * @reason Initialise fields / destroy entity manager state
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(ClientPacketListener clientPacketListener, ClientLevel.ClientLevelData clientLevelData,
                      ResourceKey<Level> resourceKey, Holder<DimensionType> holder, int i, int j, Supplier<ProfilerFiller> supplier,
                      LevelRenderer levelRenderer, boolean bl, long l, CallbackInfo ci) {
        this.entityStorage = null;

        this.moonrise$setEntityLookup(new ClientEntityLookup(this, ((ClientLevel)(Object)this).new EntityCallbacks()));
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Overwrite
    public int getEntityCount() {
        return this.moonrise$getEntityLookup().getEntityCount();
    }

    /**
     * @reason The implementation in Level will perform two virtual invokes:
     *         1. When retrieving the ChunkSource
     *         2. When calling getChunk on the ChunkSource
     *         We can reduce the virtual invokes to 1 by instead directly implementing getChunk
     *         in both ServerLevel and ClientLevel. Additionally, for dedicated servers, the virtual invoke
     *         may be elided entirely as ClientLevel is not used. For code only used by the server, it may
     *         also be elided.
     * @author Spottedleaf
     */
    @Override
    public ChunkAccess getChunk(final int x, final int z, final ChunkStatus status, final boolean load) {
        // client never returns null for load=true, so do not null check the result
        return this.chunkSource.getChunk(x, z, status, load);
    }

    /**
     * @author Spottedleaf
     */
    @Override
    public LevelChunk getChunk(final int x, final int z) {
        return (LevelChunk)this.chunkSource.getChunk(x, z, ChunkStatus.FULL, true);
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "addEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/TransientEntitySectionManager;addEntity(Lnet/minecraft/world/level/entity/EntityAccess;)V"
            )
    )
    private <T extends EntityAccess> void addEntityHook(final TransientEntitySectionManager<T> instance, final T entityAccess) {
        this.moonrise$getEntityLookup().addNewEntity((Entity)entityAccess);
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "getEntities()Lnet/minecraft/world/level/entity/LevelEntityGetter;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/TransientEntitySectionManager;getEntityGetter()Lnet/minecraft/world/level/entity/LevelEntityGetter;"
            )
    )
    private LevelEntityGetter<Entity> redirectGetEntities(final TransientEntitySectionManager<Entity> instance) {
        return this.moonrise$getEntityLookup();
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "gatherChunkSourceStats",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/TransientEntitySectionManager;gatherStats()Ljava/lang/String;"
            )
    )
    private String redirectGatherChunkSourceStats(final TransientEntitySectionManager<Entity> instance) {
        return this.moonrise$getEntityLookup().getDebugInfo();
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "unload",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/TransientEntitySectionManager;stopTicking(Lnet/minecraft/world/level/ChunkPos;)V"
            )
    )
    private <T extends EntityAccess> void chunkUnloadHook(final TransientEntitySectionManager<T> instance,
                                                          final ChunkPos pos) {
        ((ClientEntityLookup)this.moonrise$getEntityLookup()).markNonTicking(pos.toLong());
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "onChunkLoaded",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/TransientEntitySectionManager;startTicking(Lnet/minecraft/world/level/ChunkPos;)V"
            )
    )
    private <T extends EntityAccess> void chunkLoadHook(final TransientEntitySectionManager<T> instance, final ChunkPos pos) {
        ((ClientEntityLookup)this.moonrise$getEntityLookup()).markTicking(pos.toLong());
    }
}
