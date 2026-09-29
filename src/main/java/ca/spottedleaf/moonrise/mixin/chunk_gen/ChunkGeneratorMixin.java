package ca.spottedleaf.moonrise.mixin.chunk_gen;

import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.function.Consumer;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {

    /**
     * @reason We do not need to search the entire biome section, as the biome section is never
     *         modified after being created - meaning that the palette only contains entries
     *         that are present in the section (provided that the palette is NOT global).
     * @author Spottedleaf
     */
    @Redirect(
        method = "lambda$applyBiomeDecoration$1",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/PalettedContainerRO;getAll(Ljava/util/function/Consumer;)V"
        )
    )
    private static <T> void usePalette(final PalettedContainerRO<T> instance, final Consumer<T> tConsumer) {
        final Palette<T> palette = ((PalettedContainer<T>)instance).data.palette();
        if (palette instanceof GlobalPalette<T>) {
            instance.getAll(tConsumer);
        } else {
            for (int i = 0, len = palette.getSize(); i < len; ++i) {
                tConsumer.accept(palette.valueFor(i));
            }
        }
    }
}
