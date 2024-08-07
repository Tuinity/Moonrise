package ca.spottedleaf.moonrise.mixin.starlight.chunk;

import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ProtoChunk.class)
abstract class ProtoChunkMixin implements StarlightChunk {

    /**
     * @reason Remove unused skylight sources
     * @author Spottedleaf
     */
    @Redirect(
            method = "setBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/ChunkSkyLightSources;update(Lnet/minecraft/world/level/BlockGetter;III)Z"
            )
    )
    private boolean skipLightSources(final ChunkSkyLightSources instance, final BlockGetter blockGetter,
                                     final int x, final int y, final int z) {
        return false;
    }
}
