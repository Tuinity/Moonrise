package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevelReader;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelReader.class)
public interface LevelReaderMixin extends ChunkSystemLevelReader, BlockAndTintGetter, CollisionGetter, SignalGetter, BiomeManager.NoiseBiomeSource {

    @Override
    public default ChunkAccess moonrise$syncLoadNonFull(final int chunkX, final int chunkZ, final ChunkStatus status) {
        if (status == null || status.isOrAfter(ChunkStatus.FULL)) {
            throw new IllegalArgumentException("Status: " + status.toString());
        }
        return ((LevelReader)this).getChunk(chunkX, chunkZ, status, true);
    }

}
