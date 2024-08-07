package ca.spottedleaf.moonrise.mixin.fast_palette;

import ca.spottedleaf.moonrise.patches.fast_palette.FastPalette;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Palette.class)
interface PaletteMixin<T> extends FastPalette<T> {

}
