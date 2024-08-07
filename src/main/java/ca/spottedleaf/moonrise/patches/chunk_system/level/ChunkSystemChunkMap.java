package ca.spottedleaf.moonrise.patches.chunk_system.level;

import net.minecraft.world.level.ChunkPos;
import java.io.IOException;

public interface ChunkSystemChunkMap {

    public void moonrise$writeFinishCallback(final ChunkPos pos) throws IOException;

}
