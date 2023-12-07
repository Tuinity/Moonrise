package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(IntegerProperty.class)
public abstract class IntegerPropertyMixin extends Property<Integer> implements PropertyAccess<Integer> {

    @Shadow
    @Final
    private int min;

    @Shadow
    @Final
    private int max;

    protected IntegerPropertyMixin(String string, Class<Integer> class_) {
        super(string, class_);
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

    @Override
    public final int moonrise$getIdFor(final Integer value) {
        final int val = value.intValue();
        final int ret = val - this.min;

        return ret | ((this.max - ret) >> 31);
    }
}
