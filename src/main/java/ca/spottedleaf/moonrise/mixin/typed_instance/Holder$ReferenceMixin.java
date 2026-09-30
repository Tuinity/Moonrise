package ca.spottedleaf.moonrise.mixin.typed_instance;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;
import java.util.HashSet;

@Mixin(Holder.Reference.class)
abstract class Holder$ReferenceMixin<T> {

    @Unique
    private HashSet<TagKey<?>> boundTags;

    /**
     * @reason Initialise bound tags set
     * @author Spottedleaf
     */
    @Inject(
        method = "bindTags",
        at = @At(
            value = "RETURN"
        )
    )
    private void createHashSet(final Collection<TagKey<T>> tags, final CallbackInfo ci) {
        this.boundTags = new HashSet<>(tags.size(), 0.5f);
        this.boundTags.addAll(tags);
    }

    /**
     * @reason Use HashSet, which will: not use floorMod, will check hash before equality, will check identity equality before object equality
     * @author Spottedleaf
     */
    @Overwrite
    public boolean is(final TagKey<T> tag) {
        // note: NPE acts as not bound check
        return this.boundTags.contains(tag);
    }
}
