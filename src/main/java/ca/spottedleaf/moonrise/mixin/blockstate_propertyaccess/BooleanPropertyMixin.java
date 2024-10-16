package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BooleanProperty.class)
abstract class BooleanPropertyMixin extends Property<Boolean> implements PropertyAccess<Boolean> {
    protected BooleanPropertyMixin(String string, Class<Boolean> class_) {
        super(string, class_);
    }

    @Override
    public final int moonrise$getIdFor(final Boolean value) {
        return value.booleanValue() ? 1 : 0;
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
        this.moonrise$setById(new Boolean[]{ Boolean.FALSE, Boolean.TRUE });
    }
}
