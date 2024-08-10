package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;

@Mixin(EnumProperty.class)
abstract class EnumPropertyMixin<T extends Enum<T> & StringRepresentable> extends Property<T> implements PropertyAccess<T> {

    @Shadow
    public abstract Collection<T> getPossibleValues();

    @Unique
    private int[] idLookupTable;

    protected EnumPropertyMixin(String string, Class<T> class_) {
        super(string, class_);
    }

    @Override
    public final int moonrise$getIdFor(final T value) {
        final Class<T> target = this.getValueClass();
        return ((value.getClass() != target && value.getDeclaringClass() != target)) ? -1 : this.idLookupTable[value.ordinal()];
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
        final Collection<T> values = this.getPossibleValues();
        final Class<T> clazz = this.getValueClass();

        int id = 0;
        this.idLookupTable = new int[clazz.getEnumConstants().length];
        Arrays.fill(this.idLookupTable, -1);
        final T[] byId = (T[])Array.newInstance(clazz, values.size());

        for (final T value : values) {
            final int valueId = id++;
            this.idLookupTable[value.ordinal()] = valueId;
            byId[valueId] = value;
        }

        this.moonrise$setById(byId);
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
}
