package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Consumer;

@Mixin(EntityTickList.class)
abstract class EntityTickListMixin {

    @Shadow
    private Int2ObjectMap<Entity> active;

    @Shadow
    private Int2ObjectMap<Entity> passive;

    @Unique
    private final IteratorSafeOrderedReferenceSet<Entity> entities = new IteratorSafeOrderedReferenceSet<>();

    /**
     * @reason Initialise new fields and destroy old state
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initHook(final CallbackInfo ci) {
        this.active = null;
        this.passive = null;
    }


    /**
     * @reason Do not delay removals
     * @author Spottedleaf
     */
    @Overwrite
    public void ensureActiveIsNotIterated() {}

    /**
     * @reason Route to new entity list
     * @author Spottedleaf
     */
    @Redirect(
            method = "add",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;put(ILjava/lang/Object;)Ljava/lang/Object;",
                    remap = false
            )
    )
    private <V> V hookAdd(final Int2ObjectMap<V> instance, final int key, final V value) {
        this.entities.add((Entity)value);
        return null;
    }


    /**
     * @reason Route to new entity list
     * @author Spottedleaf
     */
    @Redirect(
            method = "remove",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;remove(I)Ljava/lang/Object;",
                    remap = false
            )
    )
    private <V> V hookRemoveAvoidNPE(final Int2ObjectMap<V> instance, final int key,
                                     final @Local(ordinal = 0, argsOnly = true) Entity entity) {
        this.entities.remove(entity);
        return null;
    }

    /**
     * @reason Route to new entity list
     * @author Spottedleaf
     */
    @Overwrite
    public boolean contains(Entity entity) {
        return this.entities.contains(entity);
    }

    /**
     * @reason Route to new entity list
     * @author Spottedleaf
     */
    @Inject(
        method = "forEach",
        at = @At("HEAD"),
        cancellable = true
    )
    private void injectForEach(final Consumer<Entity> consumer, final CallbackInfo ci) {
        this.forEach(consumer);
        ci.cancel();
    }

    @Unique
    private void forEach(final Consumer<Entity> action) {
        // To ensure nothing weird happens with dimension travelling, do not iterate over new entries...
        // (by dfl iterator() is configured to not iterate over new entries)
        final IteratorSafeOrderedReferenceSet.Iterator<Entity> iterator = this.entities.iterator();
        try {
            while (iterator.hasNext()) {
                action.accept(iterator.next());
             }
        } finally {
            iterator.finishedIterating();
        }
    }
}
