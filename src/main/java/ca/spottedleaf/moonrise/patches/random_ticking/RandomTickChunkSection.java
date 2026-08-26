package ca.spottedleaf.moonrise.patches.random_ticking;

import net.minecraft.world.level.chunk.LevelChunk;

public interface RandomTickChunkSection {

    public void moonrise$bindRandomTickChunk(final LevelChunk chunk, final int index);

    public void moonrise$unbindRandomTickChunk();

}
