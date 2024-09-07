package ca.spottedleaf.moonrise.mixin.mob_spawning;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalMobCapCalculator.MobCounts.class)
abstract class LocalMobCapCalculator$MobCountsMixin {

    @Shadow
    @Mutable
    @Final
    private Object2IntMap<MobCategory> counts;

    @Unique
    private static final MobCategory[] CATEGORIES = MobCategory.values();

    @Unique
    private static final Object2IntOpenHashMap<?> DUMMY = new Object2IntOpenHashMap<>();

    @Unique
    private final int[] newCounts = new int[CATEGORIES.length];

    /**
     * @reason Ensure accesses of old field blow up
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void destroyField(final CallbackInfo ci) {
        this.counts = null;
    }

    /**
     * @reason Avoid allocating the map, we null it later
     * @author Spottedleaf
     */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "(I)Lit/unimi/dsi/fastutil/objects/Object2IntOpenHashMap;"
        )
    )
    private <T> Object2IntOpenHashMap<T> avoidAllocation(final int expected) {
        return (Object2IntOpenHashMap<T>)DUMMY;
    }

    /**
     * @reason Do not allocate MobCategory[]
     * @author Spottedleaf
     */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/MobCategory;values()[Lnet/minecraft/world/entity/MobCategory;"
        )
    )
    private MobCategory[] useCachedArray() {
        return CATEGORIES;
    }

    /**
     * @reason Use simple array instead of compute call
     * @author Spottedleaf
     */
    @Overwrite
    public void add(final MobCategory category) {
        ++this.newCounts[category.ordinal()];
    }

    /**
     * @reason Use simple array instead of map get call
     * @author Spottedleaf
     */
    @Overwrite
    public boolean canSpawn(final MobCategory category) {
        return this.newCounts[category.ordinal()] < category.getMaxInstancesPerChunk();
    }
}
