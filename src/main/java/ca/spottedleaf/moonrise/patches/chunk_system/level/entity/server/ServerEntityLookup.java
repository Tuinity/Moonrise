package ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.common.util.ChunkSystem;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelCallback;

public final class ServerEntityLookup extends EntityLookup {

    private static final Entity[] EMPTY_ENTITY_ARRAY = new Entity[0];

    private final ServerLevel serverWorld;
    public final ReferenceList<Entity> trackerEntities = new ReferenceList<>(EMPTY_ENTITY_ARRAY); // Moonrise - entity tracker
    public final ReferenceList<Entity> trackerUnloadedEntities = new ReferenceList<>(EMPTY_ENTITY_ARRAY); // Moonrise - entity tracker

    public ServerEntityLookup(final ServerLevel world, final LevelCallback<Entity> worldCallback) {
        super(world, worldCallback);
        this.serverWorld = world;
    }

    @Override
    protected Boolean blockTicketUpdates() {
        return ((ChunkSystemServerLevel)this.serverWorld).moonrise$getChunkTaskScheduler().chunkHolderManager.blockTicketUpdates();
    }

    @Override
    protected void setBlockTicketUpdates(final Boolean value) {
        ((ChunkSystemServerLevel)this.serverWorld).moonrise$getChunkTaskScheduler().chunkHolderManager.unblockTicketUpdates(value);
    }

    @Override
    protected void checkThread(final int chunkX, final int chunkZ, final String reason) {
        TickThread.ensureTickThread(this.serverWorld, chunkX, chunkZ, reason);
    }

    @Override
    protected void checkThread(final Entity entity, final String reason) {
        TickThread.ensureTickThread(entity, reason);
    }

    @Override
    protected ChunkEntitySlices createEntityChunk(final int chunkX, final int chunkZ, final boolean transientChunk) {
        // loadInEntityChunk will call addChunk for us
        return ((ChunkSystemServerLevel)this.serverWorld).moonrise$getChunkTaskScheduler().chunkHolderManager
                .getOrCreateEntityChunk(chunkX, chunkZ, transientChunk);
    }

    @Override
    protected void onEmptySlices(final int chunkX, final int chunkZ) {
        // entity slices unloading is managed by ticket levels in chunk system
    }

    @Override
    protected void entitySectionChangeCallback(final Entity entity,
                                               final int oldSectionX, final int oldSectionY, final int oldSectionZ,
                                               final int newSectionX, final int newSectionY, final int newSectionZ) {
        if (entity instanceof ServerPlayer player) {
            ((ChunkSystemServerLevel)this.serverWorld).moonrise$getNearbyPlayers().tickPlayer(player);
        }
    }

    @Override
    protected void addEntityCallback(final Entity entity) {
        if (entity instanceof ServerPlayer player) {
            ((ChunkSystemServerLevel)this.serverWorld).moonrise$getNearbyPlayers().addPlayer(player);
        }
    }

    @Override
    protected void removeEntityCallback(final Entity entity) {
        if (entity instanceof ServerPlayer player) {
            ((ChunkSystemServerLevel)this.serverWorld).moonrise$getNearbyPlayers().removePlayer(player);
        }
        this.trackerUnloadedEntities.remove(entity); // Moonrise - entity tracker
    }

    @Override
    protected void entityStartLoaded(final Entity entity) {
        // Moonrise start - entity tracker
        this.trackerEntities.add(entity);
        this.trackerUnloadedEntities.remove(entity);
        // Moonrise end - entity tracker
    }

    @Override
    protected void entityEndLoaded(final Entity entity) {
        // Moonrise start - entity tracker
        this.trackerEntities.remove(entity);
        this.trackerUnloadedEntities.add(entity);
        // Moonrise end - entity tracker
    }

    @Override
    protected void entityStartTicking(final Entity entity) {

    }

    @Override
    protected void entityEndTicking(final Entity entity) {

    }

    @Override
    protected boolean screenEntity(final Entity entity) {
        return ChunkSystem.screenEntity(this.serverWorld, entity);
    }
}
