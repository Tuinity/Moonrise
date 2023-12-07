package ca.spottedleaf.moonrise.patches.starlight.light;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;

public interface StarLightLightingProvider {

    public StarLightInterface starlight$getLightEngine();

    public void starlight$clientUpdateLight(final LightLayer lightType, final SectionPos pos,
                                            final DataLayer nibble, final boolean trustEdges);

    public void starlight$clientRemoveLightData(final ChunkPos chunkPos);

    public void starlight$clientChunkLoad(final ChunkPos pos, final LevelChunk chunk);

}
