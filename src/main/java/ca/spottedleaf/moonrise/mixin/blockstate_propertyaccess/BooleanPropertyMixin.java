package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
    /*
    TODO: idk why this isn't working, we'll just redirect the super call for now and deal with the useless instanceof op
    @WrapOperation(
        method = "equals",
        at = @At(
            value = "CONSTANT",
            opcode = Opcodes.INSTANCEOF,
            args = "classValue=net/minecraft/world/level/block/state/properties/BooleanProperty"
        )
    )
    private boolean skipFurtherComparison(final Object obj, final Operation<Boolean> orig) {
        return false;
    }
     */

    /**
     * @reason Properties are identity comparable
     * @author Spottedleaf
     */
    @Redirect(
        method = "equals",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/properties/Property;equals(Ljava/lang/Object;)Z"
        )
    )
    private boolean skipSuperCheck(final Property instance, final Object object) {
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
