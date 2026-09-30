package ca.spottedleaf.moonrise.mixin.typed_instance;

import ca.spottedleaf.moonrise.patches.typed_instance.InternIdentifier;
import com.google.common.collect.Interner;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TagKey.class)
abstract class TagKeyMixin<T> {

    @Shadow
    @Final
    private static Interner<TagKey<?>> VALUES;

    @Shadow
    @Final
    private Identifier location;

    @Shadow
    @Final
    private ResourceKey<? extends Registry<T>> registry;


    @Unique
    private int hash;

    /**
     * @reason Initialise hash cache field
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void initHash(final CallbackInfo ci) {
        this.hash = 31 * this.location.hashCode() + this.registry.hashCode();
    }

    /**
     * @reason Cache the hashcode
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public final int hashCode() {
        return this.hash;
    }

    /**
     * @reason Use reference equality check for location before object equality
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public final boolean equals(final Object obj) {
        if (obj == null || obj.getClass() != TagKey.class) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        final TagKey<?> other = ((TagKey<?>)obj);
        return this.registry == other.registry() && (this.location == other.location() || this.location.equals(other.location()));
    }

    /**
     * @reason Additionally intern the Identifier
     * @author Spottedleaf
     */
    @Overwrite
    public static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, Identifier location) {
        return (TagKey)VALUES.intern(new TagKey(registry, ((InternIdentifier)(Object)location).moonrise$intern()));
    }
}
