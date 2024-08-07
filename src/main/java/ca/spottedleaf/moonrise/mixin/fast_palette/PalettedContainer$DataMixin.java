package ca.spottedleaf.moonrise.mixin.fast_palette;

import ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PalettedContainer.Data.class)
abstract class PalettedContainer$DataMixin<T> implements FastPaletteData<T> {

    @Unique
    private T[] palette;

    @Override
    public final T[] moonrise$getPalette() {
        return this.palette;
    }

    @Override
    public final void moonrise$setPalette(final T[] palette) {
        this.palette = palette;
    }
}
