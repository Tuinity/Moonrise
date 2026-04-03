package ca.spottedleaf.moonrise.mixin.poi_lookup;

import ca.spottedleaf.moonrise.patches.poi_lookup.PoiAccess;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(AcquirePoi.class)
abstract class AcquirePoiMixin {

    /**
     * @reason Limit return count for POI lookup to the limit vanilla will apply
     * @author Spottedleaf
     */
    @Redirect(
            method = {
                "lambda$create$3"
            },
            at = @At(
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;findAllClosestFirstWithType(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;",
                    value = "INVOKE",
                    ordinal = 0
            )
    )
    private static Stream<Pair<Holder<PoiType>, BlockPos>> useLimitedSearch(final PoiManager poiManager, final Predicate<Holder<PoiType>> predicate,
                                                                            final Predicate<BlockPos> filter, final BlockPos center, final int radius,
                                                                            final PoiManager.Occupancy occupancy) {
        final List<Pair<Holder<PoiType>, BlockPos>> ret = new ArrayList<>();

        PoiAccess.findNearestPoiPositions(
                poiManager, predicate, filter, center, radius, Double.MAX_VALUE, occupancy, PoiAccess.LOAD_FOR_SEARCHING, 5, ret
        );

        return ret.stream();
    }
}
