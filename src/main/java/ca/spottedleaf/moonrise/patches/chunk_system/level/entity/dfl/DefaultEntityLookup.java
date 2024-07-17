package ca.spottedleaf.moonrise.patches.chunk_system.level.entity.dfl;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelCallback;

public final class DefaultEntityLookup extends EntityLookup {
    public DefaultEntityLookup(final Level world) {
        super(world, new DefaultLevelCallback());
    }

    @Override
    protected Boolean blockTicketUpdates() {
        return null;
    }

    @Override
    protected void setBlockTicketUpdates(final Boolean value) {}

    @Override
    protected void checkThread(final int chunkX, final int chunkZ, final String reason) {}

    @Override
    protected void checkThread(final Entity entity, final String reason) {}

    @Override
    protected ChunkEntitySlices createEntityChunk(final int chunkX, final int chunkZ, final boolean transientChunk) {
        final ChunkEntitySlices ret = new ChunkEntitySlices(
                this.world, chunkX, chunkZ, FullChunkStatus.FULL,
                WorldUtil.getMinSection(this.world), WorldUtil.getMaxSection(this.world)
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

    protected static final class DefaultLevelCallback implements LevelCallback<Entity> {

        @Override
        public void onCreated(final Entity entity) {}

        @Override
        public void onDestroyed(final Entity entity) {}

        @Override
        public void onTickingStart(final Entity entity) {}

        @Override
        public void onTickingEnd(final Entity entity) {}

        @Override
        public void onTrackingStart(final Entity entity) {}

        @Override
        public void onTrackingEnd(final Entity entity) {}

        @Override
        public void onSectionChange(final Entity entity) {}
    }
}
