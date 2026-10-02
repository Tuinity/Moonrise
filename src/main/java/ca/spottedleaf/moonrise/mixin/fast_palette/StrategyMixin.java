package ca.spottedleaf.moonrise.mixin.fast_palette;

import ca.spottedleaf.moonrise.patches.fast_palette.FastHashMapPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.Strategy;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Strategy.class)
abstract class StrategyMixin {

    @Shadow
    @Final
    @Mutable
    private static Palette.Factory HASHMAP_PALETTE_FACTORY;

    /**
     * @reason Replace Vanilla hash map palette with faster impl
     * @author Spottedleaf
     */
    @Redirect(
        method = "<clinit>",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.PUTSTATIC,
            target = "Lnet/minecraft/world/level/chunk/Strategy;HASHMAP_PALETTE_FACTORY:Lnet/minecraft/world/level/chunk/Palette$Factory;"
        )
    )
    private static void useHashPalette(Palette.Factory value) {
        HASHMAP_PALETTE_FACTORY = FastHashMapPalette::create;
    }
}
