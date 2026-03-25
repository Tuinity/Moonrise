package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccessStateHolder;
import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.util.ZeroCollidingReferenceStateTable;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;
import java.util.stream.Stream;

@Mixin(StateHolder.class)
abstract class StateHolderMixin<O, S> implements PropertyAccessStateHolder<O, S> {

    @Shadow
    @Final
    protected O owner;

    @Shadow
    public Property<?>[] propertyKeys;

    @Shadow
    public Comparable<?>[] propertyValues;

    @Shadow
    private S[][] neighbors;

    @Shadow
    private static <T extends Comparable<T>> Property.Value<T> createValue(final Property<T> propertyKey, final Comparable<?> propertyValue) {
        throw new UnsupportedOperationException();
    }

    @Unique
    protected ZeroCollidingReferenceStateTable<O, S> optimisedTable;

    @Unique
    protected long tableIndex;

    @Override
    public final long moonrise$getTableIndex() {
        return this.tableIndex;
    }

    @Override
    public final void moonrise$init(final Collection<S> states) {
        this.optimisedTable.loadInTable(states);

        // de-duplicate the tables and remove values, properties, neighbours arrays
        for (final S neighbour : states) {
            ((StateHolderMixin<O, S>)(Object)(StateHolder<O, S>)neighbour).optimisedTable = this.optimisedTable;
            ((StateHolderMixin<O, S>)(Object)(StateHolder<O, S>)neighbour).propertyKeys = null;
            ((StateHolderMixin<O, S>)(Object)(StateHolder<O, S>)neighbour).propertyValues = null;
            ((StateHolderMixin<O, S>)(Object)(StateHolder<O, S>)neighbour).neighbors = null;
        }
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
    private void init(final O owner, final Property<?>[] propertyKeys, final Comparable<?>[] propertyValues, final CallbackInfo ci) {
        this.optimisedTable = new ZeroCollidingReferenceStateTable<>(propertyKeys);
        this.tableIndex = this.optimisedTable.getIndex((StateHolder<O, S>)(Object)this, propertyKeys, propertyValues);
    }

    /**
     * @reason De-duplicate the property keys
     * @author Spottedleaf
     */
    @Overwrite
    public Collection<Property<?>> getProperties() {
        return this.optimisedTable.getProperties();
    }

    /**
     * @reason De-duplicate the property keys
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isSingletonState() {
        return this.optimisedTable.isSingletonState();
    }

    /**
     * @reason De-duplicate the property keys
     * @author Spottedleaf
     */
    @Overwrite
    public Stream<Property.Value<?>> getValues() {
        return this.optimisedTable.getProperties().stream().map((final Property<?> prop) -> {
            return createValue(prop, StateHolderMixin.this.getValue(prop));
        });
    }

    /**
     * @reason Replace with optimisedTable
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Comparable<T>, V extends T> S setValue(final Property<T> property, final V value) {
        final S ret = this.optimisedTable.set(this.tableIndex, property, value);
        if (ret != null) {
            return ret;
        }
        throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner);
    }

    /**
     * @reason Replace with optimisedTable
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Comparable<T>, V extends T> S trySetValue(final Property<T> property, final V value) {
        if (property == null) {
            return (S)(StateHolder<O, S>)(Object)this;
        }
        final S ret = this.optimisedTable.trySet(this.tableIndex, property, value, (S)(StateHolder<O, S>)(Object)this);
        if (ret != null) {
            return ret;
        }
        throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner);
    }

    /**
     * @reason Replace with optimisedTable
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Comparable<T>> T getNullableValue(final Property<T> property) {
        return property == null ? null : this.optimisedTable.get(this.tableIndex, property);
    }

    /**
     * @reason Replace with optimisedTable
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Comparable<T>> T getValue(final Property<T> property) {
        final T ret = this.optimisedTable.get(this.tableIndex, property);
        if (ret != null) {
            return ret;
        }
        throw new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + this.owner);
    }

    /**
     * @reason Replace with optimisedTable
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Comparable<T>> boolean hasProperty(final Property<T> property) {
        return property != null && this.optimisedTable.hasProperty(property);
    }
}
