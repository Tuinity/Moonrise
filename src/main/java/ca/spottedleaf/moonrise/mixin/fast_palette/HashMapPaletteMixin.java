package ca.spottedleaf.moonrise.mixin.fast_palette;

import ca.spottedleaf.moonrise.patches.fast_palette.FastPalette;
import ca.spottedleaf.moonrise.patches.fast_palette.FastPalettedContainer;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(HashMapPalette.class)
public abstract class HashMapPaletteMixin<T> implements Palette<T>, FastPalette<T> {

    @Shadow
    @Final
    private CrudeIncrementalIntIdentityHashBiMap<T> values;

    @Override
    public T[] moonrise$getRawPalette(final FastPalettedContainer<T> container) {
        return ((FastPalette<T>)this.values).moonrise$getRawPalette(container);
    }
}