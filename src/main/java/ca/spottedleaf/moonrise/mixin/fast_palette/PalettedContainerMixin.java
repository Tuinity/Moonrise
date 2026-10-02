package ca.spottedleaf.moonrise.mixin.fast_palette;

import ca.spottedleaf.moonrise.patches.fast_palette.FastHashMapPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PalettedContainer.class)
abstract class PalettedContainerMixin<T> implements PaletteResize<T>, PalettedContainerRO<T> {

    @Shadow
    public volatile PalettedContainer.Data<T> data;

    @Unique
    private static <T> T readPalette(final PalettedContainer.Data<T> data, final int paletteIdx) {
        final Palette<T> palette = data.palette();
        if (palette instanceof FastHashMapPalette<T> hashMapPalette) {
            return hashMapPalette.valueFor(paletteIdx);
        }
        return palette.valueFor(paletteIdx);
    }

    /**
     * @reason Replace palette read with optimised version
     * @author Spottedleaf
     */
    @Overwrite
    public T getAndSet(final int index, final T value) {
        final int paletteIdx = this.data.palette().idFor(value, this);
        final PalettedContainer.Data<T> data = this.data;
        final int prev = data.storage().getAndSet(index, paletteIdx);
        return readPalette(data, prev);
    }

    /**
     * @reason Replace palette read with optimised version
     * @author Spottedleaf
     */
    @Overwrite
    public T get(final int index) {
        final PalettedContainer.Data<T> data = this.data;
        return readPalette(data, data.storage().get(index));
    }
}
