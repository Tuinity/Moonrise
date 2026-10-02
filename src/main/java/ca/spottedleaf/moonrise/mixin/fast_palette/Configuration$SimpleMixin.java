package ca.spottedleaf.moonrise.mixin.fast_palette;

import ca.spottedleaf.moonrise.patches.fast_palette.DualPalette;
import ca.spottedleaf.moonrise.patches.fast_palette.FastHashMapPalette;
import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PaletteResize;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Mixin(Configuration.Simple.class)
abstract class Configuration$SimpleMixin {

    /**
     * @reason Force use of hash palette everywhere to eliminate indirect during palette use.
     *         The hash palette will internally switch to a linear lookup under a given threshold
     *         for {@link Palette#idFor(Object, PaletteResize)} calls, preserving linear performance.
     *
     * @author Spottedleaf
     */
    @Redirect(
        method = "createPalette",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/Palette$Factory;create(ILjava/util/List;)Lnet/minecraft/world/level/chunk/Palette;"
        )
    )
    private <T> Palette<T> useHashEverywhere(final Palette.Factory instance, final int bits, final List<T> palette) {
        return FastHashMapPalette.create(bits, palette);

        /*
        final boolean swap = ThreadLocalRandom.current().nextBoolean();
        final Palette.Factory firstc = LinearPalette::create;
        final Palette.Factory secondc = FastHashMapPalette::create;
        final Palette.Factory first = swap ? secondc : firstc;
        final Palette.Factory second = swap ? firstc : secondc;
        return bits > 4 ? FastHashMapPalette.create(bits, palette) : new DualPalette<>(first.create(bits, palette), second.create(bits, palette), second, bits);
         */
    }
}
