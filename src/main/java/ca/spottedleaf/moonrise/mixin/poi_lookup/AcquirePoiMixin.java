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
public abstract class AcquirePoiMixin {

    /**
     * @reason Limit return count for POI lookup to the limit vanilla will apply
     * @author Spottedleaf
     */
    @Redirect(
            method = "method_46885",
            at = @At(
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;findAllClosestFirstWithType(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;",
                    value = "INVOKE",
                    ordinal = 0
            )
    )
    private static Stream<Pair<Holder<PoiType>, BlockPos>> aaa(PoiManager poiManager, Predicate<Holder<PoiType>> predicate,
                                                               Predicate<BlockPos> predicate2, BlockPos blockPos, int i,
                                                               PoiManager.Occupancy occup) {
        final List<Pair<Holder<PoiType>, BlockPos>> ret = new ArrayList<>();

        PoiAccess.findNearestPoiPositions(
                poiManager, predicate, predicate2, blockPos, i, Double.MAX_VALUE, occup, true, 5, ret
        );

        return ret.stream();
    }
}
