package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnumProperty.class)
public abstract class EnumPropertyMixin<T extends Enum<T> & StringRepresentable> extends Property<T> implements PropertyAccess<T> {

    @Unique
    private int[] idLookupTable;

    protected EnumPropertyMixin(String string, Class<T> class_) {
        super(string, class_);
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
        int id = 0;
        this.idLookupTable = new int[getValueClass().getEnumConstants().length];
        java.util.Arrays.fill(this.idLookupTable, -1);
        for (final T value : this.getPossibleValues()) {
            this.idLookupTable[value.ordinal()] = id++;
        }
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
    public final int getIdFor(final T value) {
        return this.idLookupTable[value.ordinal()];
    }
}
