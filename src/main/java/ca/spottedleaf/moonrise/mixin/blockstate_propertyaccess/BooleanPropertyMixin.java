package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(BooleanProperty.class)
abstract class BooleanPropertyMixin extends Property<Boolean> implements PropertyAccess<Boolean> {
    protected BooleanPropertyMixin(String string, Class<Boolean> class_) {
        super(string, class_);
    }

    /**
     * This skips all ops after the identity comparison in the original code.
     *
     * @reason Properties are identity comparable
     * @author Spottedleaf
     */
    @WrapOperation(
        method = "equals",
        constant = @Constant(
            classValue = BooleanProperty.class,
            ordinal = 0
        )
    )
    private boolean skipFurtherComparison(final Object obj, final Operation<Boolean> orig) {
        return false;
    }

    @Override
    public final boolean moonrise$requiresDefaultImpl() {
        return false;
    }

    @Override
    public final int moonrise$getIdFor(final Boolean value) {
        return value.booleanValue() ? 1 : 0;
    }
}
