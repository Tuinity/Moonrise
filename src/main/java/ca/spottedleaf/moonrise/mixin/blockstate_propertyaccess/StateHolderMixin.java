package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccessStateHolder;
import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.util.ZeroCollidingReferenceStateTable;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Mixin(StateHolder.class)
abstract class StateHolderMixin<O, S> implements PropertyAccessStateHolder {

    @Shadow
    @Final
    protected O owner;

    @Shadow
    @Mutable
    @Final
    private Reference2ObjectArrayMap<Property<?>, Comparable<?>> values;

    @Unique
    protected ZeroCollidingReferenceStateTable<O, S> optimisedTable;

    @Unique
    protected long tableIndex;

    @Override
    public final long moonrise$getTableIndex() {
        return this.tableIndex;
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
        this.optimisedTable = new ZeroCollidingReferenceStateTable<>(this.values.keySet());
        this.tableIndex = this.optimisedTable.getIndex((StateHolder<O, S>)(Object)this);
    }

    /**
     * @reason Init table for ZCST
     * @author Spottedleaf
     */
    @Inject(
            method = "populateNeighbours",
            cancellable = true,
            at = @At(
                    value = "HEAD"
            )
    )
    private void loadTable(final Map<Map<Property<?>, Comparable<?>>, S> map, final CallbackInfo ci) {
        // Uses #entrySet() instead of #values() for ModernFix compat (until when/if they implement #values() on their map) (also in ZCRST#loadInTable)

        if (this.optimisedTable.isLoaded()) {
            ci.cancel();
            return;
        }
        this.optimisedTable.loadInTable(map);

        // de-duplicate the tables
        for (final Map.Entry<Map<Property<?>, Comparable<?>>, S> entry : map.entrySet()) {
            final S value = entry.getValue();
            ((StateHolderMixin<O, S>)(Object)(StateHolder<O, S>)value).optimisedTable = this.optimisedTable;
        }

        // remove values arrays
        for (final Map.Entry<Map<Property<?>, Comparable<?>>, S> entry : map.entrySet()) {
            final S value = entry.getValue();
            ((StateHolderMixin<O, S>)(Object)(StateHolder<O, S>)value).values = null;
        }

        ci.cancel();
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
    public <T extends Comparable<T>> T getNullableValue(Property<T> property) {
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

    /**
     * @reason Replace with optimisedTable
     * @author embeddedt
     */
    @Overwrite
    public Collection<Property<?>> getProperties() {
        return this.optimisedTable.getProperties();
    }

    /**
     * @reason Replace with optimisedTable
     * @author embeddedt
     */
    @Overwrite
    public Map<Property<?>, Comparable<?>> getValues() {
        ZeroCollidingReferenceStateTable<O, S> table = this.optimisedTable;
        // We have to use this.values until the table is loaded
        return table.isLoaded() ? table.getMapView(this.tableIndex) : this.values;
    }
}
