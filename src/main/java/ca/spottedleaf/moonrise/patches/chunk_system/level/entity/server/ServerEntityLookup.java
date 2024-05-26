package ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server;

import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelCallback;

public final class ServerEntityLookup extends EntityLookup {

    private final ServerLevel serverWorld;

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
}
