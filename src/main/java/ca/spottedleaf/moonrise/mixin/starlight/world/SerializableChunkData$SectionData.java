package ca.spottedleaf.moonrise.mixin.starlight.world;

import ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SerializableChunkData.SectionData.class)
abstract class SerializableChunkData$SectionData implements StarlightSectionData {

    @Unique
    private int blockLightState = -1;

    @Unique
    private int skyLightState = -1;

    @Override
    public final int starlight$getBlockLightState() {
        return this.blockLightState;
    }

    @Override
    public final void starlight$setBlockLightState(final int state) {
        this.blockLightState = state;
    }

    @Override
    public final int starlight$getSkyLightState() {
        return this.skyLightState;
    }

    @Override
    public final void starlight$setSkyLightState(final int state) {
        this.skyLightState = state;
    }
}
