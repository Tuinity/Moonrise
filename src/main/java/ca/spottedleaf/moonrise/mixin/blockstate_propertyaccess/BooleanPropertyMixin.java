package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BooleanProperty.class)
public abstract class BooleanPropertyMixin extends Property<Boolean> implements PropertyAccess<Boolean> {
    protected BooleanPropertyMixin(String string, Class<Boolean> class_) {
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
    public final int getIdFor(final Boolean value) {
        return value.booleanValue() ? 1 : 0;
    }
}
