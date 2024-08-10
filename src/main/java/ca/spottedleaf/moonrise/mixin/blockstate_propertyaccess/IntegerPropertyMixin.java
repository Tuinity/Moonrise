package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegerProperty.class)
abstract class IntegerPropertyMixin extends Property<Integer> implements PropertyAccess<Integer> {

    @Shadow
    @Final
    private int min;

    @Shadow
    @Final
    private int max;

    protected IntegerPropertyMixin(String string, Class<Integer> class_) {
        super(string, class_);
    }

    @Override
    public final int moonrise$getIdFor(final Integer value) {
        final int val = value.intValue();
        final int ret = val - this.min;

        return ret | ((this.max - ret) >> 31);
    }

    /**
     * @reason Properties are identity comparable
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public boolean equals(final Object obj) {
        return this == obj;
    }

    /**
     * @reason Hook into constructor to init fields
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void init(final CallbackInfo ci) {
        final int min = this.min;
        final int max = this.max;

        final Integer[] byId = new Integer[max - min + 1];
        for (int i = min; i <= max; ++i) {
            byId[i - min] = Integer.valueOf(i);
        }

        this.moonrise$setById(byId);
    }
}
