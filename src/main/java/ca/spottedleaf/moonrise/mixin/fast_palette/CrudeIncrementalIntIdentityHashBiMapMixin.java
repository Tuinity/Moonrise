package ca.spottedleaf.moonrise.mixin.fast_palette;

import ca.spottedleaf.moonrise.patches.fast_palette.FastPalette;
import ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData;
import net.minecraft.core.IdMap;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrudeIncrementalIntIdentityHashBiMap.class)
public abstract class CrudeIncrementalIntIdentityHashBiMapMixin<K> implements IdMap<K>, FastPalette<K> {

    @Shadow
    private K[] byId;


    @Unique
    private FastPaletteData<K> reference;

    @Override
    public final K[] moonrise$getRawPalette(final FastPaletteData<K> src) {
        this.reference = src;
        return this.byId;
    }

    /**
     * @reason Hook to call back to paletted container to update raw palette array
     * @author Spottedleaf
     */
    @Inject(
            method = "grow",
            at = @At(
                    value = "RETURN"
            )
    )
    private void growHook(final CallbackInfo ci) {
        final FastPaletteData<K> ref = this.reference;
        if (ref != null) {
            ref.moonrise$setPalette(this.byId);
        }
    }
}
