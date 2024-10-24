package ca.spottedleaf.moonrise.mixin.starlight.chunk;

import ca.spottedleaf.starlight.common.chunk.StarlightChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
abstract class ChunkAccessMixin implements StarlightChunk {

    @Shadow
    protected ChunkSkyLightSources skyLightSources;


    @Unique
    private volatile SWMRNibbleArray[] blockNibbles;

    @Unique
    private volatile SWMRNibbleArray[] skyNibbles;

    @Unique
    private volatile boolean[] skyEmptinessMap;

    @Unique
    private volatile boolean[] blockEmptinessMap;

    @Override
    public SWMRNibbleArray[] starlight$getBlockNibbles() {
        return this.blockNibbles;
    }

    @Override
    public void starlight$setBlockNibbles(final SWMRNibbleArray[] nibbles) {
        this.blockNibbles = nibbles;
    }

    @Override
    public SWMRNibbleArray[] starlight$getSkyNibbles() {
        return this.skyNibbles;
    }

    @Override
    public void starlight$setSkyNibbles(final SWMRNibbleArray[] nibbles) {
        this.skyNibbles = nibbles;
    }

    @Override
    public boolean[] starlight$getSkyEmptinessMap() {
        return this.skyEmptinessMap;
    }

    @Override
    public void starlight$setSkyEmptinessMap(final boolean[] emptinessMap) {
        this.skyEmptinessMap = emptinessMap;
    }

    @Override
    public boolean[] starlight$getBlockEmptinessMap() {
        return this.blockEmptinessMap;
    }

    @Override
    public void starlight$setBlockEmptinessMap(final boolean[] emptinessMap) {
        this.blockEmptinessMap = emptinessMap;
    }

    /**
     * @reason Remove unused skylight sources, and initialise nibble arrays.
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void nullSources(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor,
                             Registry registry, long l, LevelChunkSection[] levelChunkSections, BlendingData blendingData,
                             CallbackInfo ci) {
        this.skyLightSources = null;
        if (!((Object)this instanceof ImposterProtoChunk)) {
            this.starlight$setBlockNibbles(StarLightEngine.getFilledEmptyLight(levelHeightAccessor));
            this.starlight$setSkyNibbles(StarLightEngine.getFilledEmptyLight(levelHeightAccessor));
        }
    }

    /**
     * @reason Remove unused skylight sources
     * @author Spottedleaf
     */
    @Redirect(
            method = "initializeLightSources",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/ChunkSkyLightSources;fillFrom(Lnet/minecraft/world/level/chunk/ChunkAccess;)V"
            )
    )
    private void skipInit(final ChunkSkyLightSources instance, final ChunkAccess chunkAccess) {}
}
