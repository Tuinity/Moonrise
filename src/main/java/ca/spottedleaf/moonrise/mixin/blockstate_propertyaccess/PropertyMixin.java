package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Property.class)
abstract class PropertyMixin<T extends Comparable<T>> implements PropertyAccess<T> {

    @Unique
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    @Unique
    private int id;

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

    @Override
    public final int moonrise$getId() {
        return this.id;
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

    // this is re-declared here so that calls to Property#getIdFor are a virtual method invoke, rather than an interface invoke
    @Override
    public abstract int moonrise$getIdFor(final T value);
}
