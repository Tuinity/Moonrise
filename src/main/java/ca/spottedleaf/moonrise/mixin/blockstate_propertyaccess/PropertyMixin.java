package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Property.class)
abstract class PropertyMixin<T extends Comparable<T>> implements PropertyAccess<T> {

    @Unique
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    @Unique
    private int id;

    @Unique
    private T[] byId;

    @Unique
    private volatile boolean byIdInitialised;

    @Shadow
    public abstract List<T> getPossibleValues();

    @Shadow
    public abstract int getInternalIndex(final T value);

    @Override
    public final int moonrise$getId() {
        return this.id;
    }

    @Override
    public final T moonrise$getById(final int id) {
        final T[] byId = this.byId;
        return id < 0 || id >= byId.length ? null : this.byId[id];
    }

    @Override
    public final void moonrise$setById(final T[] byId) {
        if (this.byId != null) {
            throw new IllegalStateException();
        }
        this.byId = byId;
        this.byIdInitialised = true;
    }

    @Override
    public final void moonrise$ensureById() {
        if (this.byIdInitialised) {
            return;
        }
        // properties without a specialised implementation (i.e. custom Property subclasses) build their table here,
        // as subclass fields are not yet initialised when the Property constructor returns
        synchronized (this) {
            if (this.byIdInitialised) {
                return;
            }
            final List<T> values = this.getPossibleValues();
            final T[] byId = (T[])values.toArray(new Comparable[0]);
            for (int i = 0; i < byId.length; ++i) {
                final int id = this.moonrise$getIdFor(byId[i]);
                if (id != i) {
                    throw new IllegalStateException("Property " + this + " returned internal index " + id + " for value " + byId[i] + ", expected " + i);
                }
            }
            this.moonrise$setById(byId);
        }
    }

    @Override
    public int moonrise$getIdFor(final T value) {
        // fallback for properties without a specialised implementation (i.e. custom Property subclasses)
        return this.getInternalIndex(value);
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
    private void initId(final CallbackInfo ci) {
        this.id = ID_GENERATOR.getAndIncrement();
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
