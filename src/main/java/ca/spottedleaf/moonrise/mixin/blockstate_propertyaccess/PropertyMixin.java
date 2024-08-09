package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Property.class)
abstract class PropertyMixin<T extends Comparable<T>> implements PropertyAccess<T> {

    @Shadow
    public abstract Collection<T> getPossibleValues();

    @Unique
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    @Unique
    private int id;

    @Unique
    private Object2IntOpenHashMap<T> defaultById;

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

        final Collection<T> values = this.getPossibleValues();

        if (this.moonrise$requiresDefaultImpl()) {
            this.defaultById = new Object2IntOpenHashMap<>(values.size());

            int id = 0;
            for (final T value : values) {
                this.defaultById.put(value, id++);
            }
        }
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

    // add default implementation in case mods create their own Properties
    @Override
    public int moonrise$getIdFor(final T value) {
        return this.defaultById.getOrDefault(value, -1);
    }
}
