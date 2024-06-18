package ca.spottedleaf.moonrise.patches.starlight.light;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public interface StarLightLightingProvider {

    public StarLightInterface starlight$getLightEngine();

    public void starlight$clientUpdateLight(final LightLayer lightType, final SectionPos pos,
                                            final DataLayer nibble, final boolean trustEdges);

    public void starlight$clientRemoveLightData(final ChunkPos chunkPos);

    public void starlight$clientChunkLoad(final ChunkPos pos, final LevelChunk chunk);

    public default int starlight$serverRelightChunks(final Collection<ChunkPos> chunks,
                                                      final Consumer<ChunkPos> chunkLightCallback,
                                                      final IntConsumer onComplete) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

}
