package ca.spottedleaf.moonrise.mixin.starlight.chunk;

import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ImposterProtoChunk.class)
public abstract class ImposterProtoChunkMixin extends ProtoChunk implements StarlightChunk {

    @Final
    @Shadow
    private LevelChunk wrapped;

    public ImposterProtoChunkMixin(final LevelChunk levelChunk, final boolean bl) {
        super(levelChunk.getPos(), UpgradeData.EMPTY, levelChunk, levelChunk.getLevel().registryAccess().registryOrThrow(Registries.BIOME), levelChunk.getBlendingData());
    }

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return ((StarlightChunk)this.wrapped).getBlockNibbles();
    }

    @Override
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles) {
        ((StarlightChunk)this.wrapped).setBlockNibbles(nibbles);
    }

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return ((StarlightChunk)this.wrapped).getSkyNibbles();
    }

    @Override
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles) {
        ((StarlightChunk)this.wrapped).setSkyNibbles(nibbles);
    }

    @Override
    public boolean[] getSkyEmptinessMap() {
        return ((StarlightChunk)this.wrapped).getSkyEmptinessMap();
    }

    @Override
    public void setSkyEmptinessMap(final boolean[] emptinessMap) {
        ((StarlightChunk)this.wrapped).setSkyEmptinessMap(emptinessMap);
    }

    @Override
    public boolean[] getBlockEmptinessMap() {
        return ((StarlightChunk)this.wrapped).getBlockEmptinessMap();
    }

    @Override
    public void setBlockEmptinessMap(final boolean[] emptinessMap) {
        ((StarlightChunk)this.wrapped).setBlockEmptinessMap(emptinessMap);
    }
}
