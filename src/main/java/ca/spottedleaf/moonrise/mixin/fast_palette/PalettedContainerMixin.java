package ca.spottedleaf.moonrise.mixin.fast_palette;

import ca.spottedleaf.moonrise.patches.fast_palette.FastPalette;
import ca.spottedleaf.moonrise.patches.fast_palette.FastPalettedContainer;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> implements PaletteResize<T>, PalettedContainerRO<T>, FastPalettedContainer<T> {

    @Shadow
    public volatile PalettedContainer.Data<T> data;

    @Unique
    private T[] rawPalette;

    @Unique
    private void updateData(final PalettedContainer.Data<T> data) {
        if (data != null) {
            this.rawPalette = ((FastPalette<T>)data.palette).moonrise$getRawPalette(this);
        }
    }

    @Override
    public void moonrise$updatePaletteArray(final T[] palette) {
        this.rawPalette = palette;
    }

    /**
     * @reason Hook to update raw palette data on object construction
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>(Lnet/minecraft/core/IdMap;Ljava/lang/Object;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;)V",
            at = @At(
                    value = "RETURN"
            )
    )
    private void constructorHook1(final CallbackInfo ci) {
        this.updateData(this.data);
    }

    /**
     * @reason Hook to update raw palette data on object construction
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>(Lnet/minecraft/core/IdMap;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;Lnet/minecraft/world/level/chunk/PalettedContainer$Configuration;Lnet/minecraft/util/BitStorage;Ljava/util/List;)V",
            at = @At(
                    value = "RETURN"
            )
    )
    private void constructorHook2(final CallbackInfo ci) {
        this.updateData(this.data);
    }

    /**
     * @reason Hook to update raw palette data on object construction
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>(Lnet/minecraft/core/IdMap;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;Lnet/minecraft/world/level/chunk/PalettedContainer$Data;)V",
            at = @At(
                    value = "RETURN"
            )
    )
    private void constructorHook3(final CallbackInfo ci) {
        this.updateData(this.data);
    }

    /**
     * @reason Hook to update raw palette data on palette resize
     * @author Spottedleaf
     */
    @Inject(
            method = "onResize",
            at = @At(
                    value = "RETURN"
            )
    )
    private void resizeHook(final CallbackInfoReturnable<Integer> cir) {
        this.updateData(this.data);
    }

    /**
     * @reason Hook to update raw palette data on clientside read
     * @author Spottedleaf
     */
    @Inject(
            method = "read",
            at = @At(
                    value = "RETURN"
            )
    )
    private void readHook(final CallbackInfo ci) {
        this.updateData(this.data);
    }

    @Unique
    private T readPaletteSlow(final int paletteIdx) {
        return this.data.palette.valueFor(paletteIdx);
    }

    @Unique
    private T readPalette(final int paletteIdx) {
        final T[] palette = this.rawPalette;
        if (palette == null) {
            return this.readPaletteSlow(paletteIdx);
        }

        final T ret = palette[paletteIdx];
        if (ret == null) {
            throw new IllegalArgumentException("Palette index out of bounds");
        }
        return ret;
    }

    /**
     * @reason Replace palette read with optimised version
     * @author Spottedleaf
     */
    @Overwrite
    private T getAndSet(final int index, final T value) {
        final int paletteIdx = this.data.palette.idFor(value);
        final int prev = this.data.storage.getAndSet(index, paletteIdx);
        return this.readPalette(prev);
    }

    /**
     * @reason Replace palette read with optimised version
     * @author Spottedleaf
     */
    @Overwrite
    public T get(final int index) {
        return this.readPalette(this.data.storage.get(index));
    }
}
