package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.util.ZeroCollidingReferenceStateTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
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
import java.util.Map;
import java.util.Optional;

@Mixin(StateHolder.class)
abstract class StateHolderMixin<O, S> {

    @Shadow
    private Table<Property<?>, Comparable<?>, S> neighbours;

    @Shadow
    @Final
    protected O owner;

    @Shadow
    @Final
    private Reference2ObjectArrayMap<Property<?>, Comparable<?>> values;

    @Unique
    protected ZeroCollidingReferenceStateTable optimisedTable;

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
        this.optimisedTable = new ZeroCollidingReferenceStateTable((StateHolder<O, S>)(Object)this, this.values); // Paper - optimise state lookup
    }

    /**
     * @reason Init table for ZCST
     * @author Spottedleaf
     */
    @Inject(
            method = "populateNeighbours",
            at = @At(
                    value = "RETURN"
            )
    )
    private void loadTable(final Map<Map<Property<?>, Comparable<?>>, S> map, final CallbackInfo ci) {
        this.optimisedTable.loadInTable((Table<Property<?>, Comparable<?>, StateHolder<?,?>>)this.neighbours, this.values);
    }

    /**
     * @reason Replace with optimisedTable
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Comparable<T>, V extends T> S setValue(final Property<T> property, final V value) {
        final S ret = (S)this.optimisedTable.get(property, value);
        if (ret == null) {
            throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value");
        }
        return ret;
    }

    /**
     * @reason Replace with optimisedTable
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Comparable<T>> Optional<T> getOptionalValue(final Property<T> property) {
        final Comparable<?> ret = this.optimisedTable.get(property);
        return ret == null ? Optional.empty() : Optional.of((T)ret);
    }

    /**
     * @reason Replace with optimisedTable
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Comparable<T>> T getValue(final Property<T> property) {
        final Comparable<?> ret = this.optimisedTable.get(property);
        if (ret == null) {
            throw new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + this.owner);
        } else {
            return (T)ret;
        }
    }

    /**
     * @reason Replace with optimisedTable
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Comparable<T>> boolean hasProperty(final Property<T> property) {
        return this.optimisedTable.get(property) != null;
    }
}
