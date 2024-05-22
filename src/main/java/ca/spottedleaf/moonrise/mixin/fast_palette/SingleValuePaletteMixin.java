package ca.spottedleaf.moonrise.mixin.fast_palette;

import ca.spottedleaf.moonrise.patches.fast_palette.FastPalette;
import ca.spottedleaf.moonrise.patches.fast_palette.FastPalettedContainer;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SingleValuePalette.class)
public abstract class SingleValuePaletteMixin<T> implements Palette<T>, FastPalette<T> {

    @Shadow
    private T value;

    @Override
    public T[] moonrise$getRawPalette(final FastPalettedContainer<T> container) {
        return (T[])new Object[] { this.value };
    }
}
