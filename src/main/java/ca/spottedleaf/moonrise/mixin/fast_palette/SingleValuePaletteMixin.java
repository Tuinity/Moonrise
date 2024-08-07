package ca.spottedleaf.moonrise.mixin.fast_palette;

import ca.spottedleaf.moonrise.patches.fast_palette.FastPalette;
import ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SingleValuePalette.class)
abstract class SingleValuePaletteMixin<T> implements Palette<T>, FastPalette<T> {

    @Shadow
    private T value;

    @Unique
    private T[] rawPalette;

    @Override
    public final T[] moonrise$getRawPalette(final FastPaletteData<T> container) {
        if (this.rawPalette != null) {
            return this.rawPalette;
        }
        return this.rawPalette = (T[])new Object[] { this.value };
    }

    /**
     * @reason Hook for updating the raw palette array on value update
     * @author Spottedleaf
     */
    @Inject(
            method = "idFor",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/world/level/chunk/SingleValuePalette;value:Ljava/lang/Object;"
            )
    )
    private void updateRawPalette1(final T object, final CallbackInfoReturnable<Integer> cir) {
        if (this.rawPalette != null) {
            this.rawPalette[0] = object;
        }
    }

    /**
     * @reason Hook for updating the raw palette array on section read
     * @author Spottedleaf
     */
    @Redirect(
            method = "read",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/world/level/chunk/SingleValuePalette;value:Ljava/lang/Object;"
            )
    )
    private void updateRawPalette2(final SingleValuePalette<T> instance, final T value) {
        ((SingleValuePaletteMixin<T>)(Object)instance).value = value;
        if (this.rawPalette != null) {
            this.rawPalette[0] = value;
        }
    }
}
