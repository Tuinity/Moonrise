package ca.spottedleaf.moonrise.mixin.starlight.chunk;

import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EmptyLevelChunk.class)
abstract class EmptyLevelChunkMixin extends LevelChunk implements StarlightChunk {

    public EmptyLevelChunkMixin(final Level level, final ChunkPos pos) {
        super(level, pos);
    }

    @Override
    public SWMRNibbleArray[] starlight$getBlockNibbles() {
        return StarLightEngine.getFilledEmptyLight(this.getLevel());
    }

    @Override
    public void starlight$setBlockNibbles(final SWMRNibbleArray[] nibbles) {}

    @Override
    public SWMRNibbleArray[] starlight$getSkyNibbles() {
        return StarLightEngine.getFilledEmptyLight(this.getLevel());
    }

    @Override
    public void starlight$setSkyNibbles(final SWMRNibbleArray[] nibbles) {}

    @Override
    public boolean[] starlight$getSkyEmptinessMap() {
        return null;
    }

    @Override
    public void starlight$setSkyEmptinessMap(final boolean[] emptinessMap) {}

    @Override
    public boolean[] starlight$getBlockEmptinessMap() {
        return null;
    }

    @Override
    public void starlight$setBlockEmptinessMap(final boolean[] emptinessMap) {}
}
