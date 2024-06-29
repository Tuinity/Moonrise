package ca.spottedleaf.moonrise.patches.chunk_system.level;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface ChunkSystemLevel {

    public EntityLookup moonrise$getEntityLookup();

    public void moonrise$setEntityLookup(final EntityLookup entityLookup);

    public LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ);

    public ChunkAccess moonrise$getAnyChunkIfLoaded(final int chunkX, final int chunkZ);

    public ChunkAccess moonrise$getSpecificChunkIfLoaded(final int chunkX, final int chunkZ, final ChunkStatus leastStatus);

    public void moonrise$midTickTasks();

}
