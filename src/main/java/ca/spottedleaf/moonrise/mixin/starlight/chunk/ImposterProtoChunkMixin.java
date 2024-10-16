package ca.spottedleaf.moonrise.mixin.starlight.chunk;

import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ImposterProtoChunk.class)
abstract class ImposterProtoChunkMixin extends ProtoChunk implements StarlightChunk {

    @Final
    @Shadow
    private LevelChunk wrapped;

    public ImposterProtoChunkMixin(final ChunkPos chunkPos, final UpgradeData upgradeData, final LevelHeightAccessor levelHeightAccessor, final Registry<Biome> registry, @Nullable final BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, registry, blendingData);
    }

    @Override
    public SWMRNibbleArray[] starlight$getBlockNibbles() {
        return ((StarlightChunk)this.wrapped).starlight$getBlockNibbles();
    }

    @Override
    public void starlight$setBlockNibbles(final SWMRNibbleArray[] nibbles) {
        ((StarlightChunk)this.wrapped).starlight$setBlockNibbles(nibbles);
    }

    @Override
    public SWMRNibbleArray[] starlight$getSkyNibbles() {
        return ((StarlightChunk)this.wrapped).starlight$getSkyNibbles();
    }

    @Override
    public void starlight$setSkyNibbles(final SWMRNibbleArray[] nibbles) {
        ((StarlightChunk)this.wrapped).starlight$setSkyNibbles(nibbles);
    }

    @Override
    public boolean[] starlight$getSkyEmptinessMap() {
        return ((StarlightChunk)this.wrapped).starlight$getSkyEmptinessMap();
    }

    @Override
    public void starlight$setSkyEmptinessMap(final boolean[] emptinessMap) {
        ((StarlightChunk)this.wrapped).starlight$setSkyEmptinessMap(emptinessMap);
    }

    @Override
    public boolean[] starlight$getBlockEmptinessMap() {
        return ((StarlightChunk)this.wrapped).starlight$getBlockEmptinessMap();
    }

    @Override
    public void starlight$setBlockEmptinessMap(final boolean[] emptinessMap) {
        ((StarlightChunk)this.wrapped).starlight$setBlockEmptinessMap(emptinessMap);
    }
}
