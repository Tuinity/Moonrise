package ca.spottedleaf.moonrise.patches.chunk_system.level.entity.client;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelCallback;

public final class ClientEntityLookup extends EntityLookup {

    private final LongOpenHashSet tickingChunks = new LongOpenHashSet();

    public ClientEntityLookup(final Level world, final LevelCallback<Entity> worldCallback) {
        super(world, worldCallback);
    }

    @Override
    protected Boolean blockTicketUpdates() {
        // not present on client
        return null;
    }

    @Override
    protected void setBlockTicketUpdates(Boolean value) {
        // not present on client
    }

    @Override
    protected void checkThread(final int chunkX, final int chunkZ, final String reason) {
        // TODO implement?
    }

    @Override
    protected void checkThread(final Entity entity, final String reason) {
        // TODO implement?
    }

    @Override
    protected ChunkEntitySlices createEntityChunk(final int chunkX, final int chunkZ, final boolean transientChunk) {
        final boolean ticking = this.tickingChunks.contains(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        final ChunkEntitySlices ret = new ChunkEntitySlices(
                this.world, chunkX, chunkZ,
                ticking ? FullChunkStatus.ENTITY_TICKING : FullChunkStatus.FULL, WorldUtil.getMinSection(this.world), WorldUtil.getMaxSection(this.world)
        );

        // note: not handled by superclass
        this.addChunk(chunkX, chunkZ, ret);

        return ret;
    }

    @Override
    protected void onEmptySlices(final int chunkX, final int chunkZ) {
        this.removeChunk(chunkX, chunkZ);
    }

    @Override
    protected void entitySectionChangeCallback(final Entity entity,
                                               final int oldSectionX, final int oldSectionY, final int oldSectionZ,
                                               final int newSectionX, final int newSectionY, final int newSectionZ) {

    }

    @Override
    protected void addEntityCallback(final Entity entity) {

    }

    @Override
    protected void removeEntityCallback(final Entity entity) {

    }

    @Override
    protected void entityStartLoaded(final Entity entity) {

    }

    @Override
    protected void entityEndLoaded(final Entity entity) {

    }

    @Override
    protected void entityStartTicking(final Entity entity) {

    }

    @Override
    protected void entityEndTicking(final Entity entity) {

    }

    @Override
    protected boolean screenEntity(final Entity entity) {
        return true;
    }

    public void markTicking(final long pos) {
        if (this.tickingChunks.add(pos)) {
            final int chunkX = CoordinateUtils.getChunkX(pos);
            final int chunkZ = CoordinateUtils.getChunkZ(pos);
            if (this.getChunk(chunkX, chunkZ) != null) {
                this.chunkStatusChange(chunkX, chunkZ, FullChunkStatus.ENTITY_TICKING);
            }
        }
    }

    public void markNonTicking(final long pos) {
        if (this.tickingChunks.remove(pos)) {
            final int chunkX = CoordinateUtils.getChunkX(pos);
            final int chunkZ = CoordinateUtils.getChunkZ(pos);
            if (this.getChunk(chunkX, chunkZ) != null) {
                this.chunkStatusChange(chunkX, chunkZ, FullChunkStatus.FULL);
            }
        }
    }
}
