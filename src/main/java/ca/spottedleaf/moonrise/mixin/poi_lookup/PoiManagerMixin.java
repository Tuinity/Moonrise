package ca.spottedleaf.moonrise.mixin.poi_lookup;

import ca.spottedleaf.moonrise.patches.poi_lookup.PoiAccess;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(PoiManager.class)
public abstract class PoiManagerMixin extends SectionStorage<PoiSection> {
    public PoiManagerMixin(Path path, Function<Runnable, Codec<PoiSection>> function, Function<Runnable, PoiSection> function2,
                           DataFixer dataFixer, DataFixTypes dataFixTypes, boolean bl, RegistryAccess registryAccess,
                           LevelHeightAccessor levelHeightAccessor) {
        super(path, function, function2, dataFixer, dataFixTypes, bl, registryAccess, levelHeightAccessor);
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> find(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos,
                                   int radius, PoiManager.Occupancy occupationStatus) {
        // Diff from Paper: use load=true
        BlockPos ret = PoiAccess.findAnyPoiPosition((PoiManager)(Object)this, typePredicate, posPredicate, pos, radius, occupationStatus, true);
        return Optional.ofNullable(ret);
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius,
                                          PoiManager.Occupancy occupationStatus) {
        // Diff from Paper: use load=true
        BlockPos ret = PoiAccess.findClosestPoiDataPosition((PoiManager)(Object)this, typePredicate, null, pos, radius, radius * radius, occupationStatus, true);
        return Optional.ofNullable(ret);
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(Predicate<Holder<PoiType>> typePredicate, BlockPos pos,
                                                                         int radius, PoiManager.Occupancy occupationStatus) {
        // Diff from Paper: use load=true
        return Optional.ofNullable(PoiAccess.findClosestPoiDataTypeAndPosition(
                (PoiManager)(Object)this, typePredicate, null, pos, radius, radius * radius, occupationStatus, true
        ));
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos,
                                          int radius, PoiManager.Occupancy occupationStatus) {
        // Diff from Paper: use load=true
        BlockPos ret = PoiAccess.findClosestPoiDataPosition((PoiManager)(Object)this, typePredicate, posPredicate, pos, radius, radius * radius, occupationStatus, true);
        return Optional.ofNullable(ret);
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> take(Predicate<Holder<PoiType>> typePredicate, BiPredicate<Holder<PoiType>, BlockPos> biPredicate,
                                   BlockPos pos, int radius) {
        // Diff from Paper: use load=true
        final PoiRecord closest = PoiAccess.findClosestPoiDataRecord(
                (PoiManager)(Object)this, typePredicate, biPredicate, pos, radius, radius * radius, PoiManager.Occupancy.HAS_SPACE, true
        );
        return Optional.ofNullable(closest).map(poi -> {
             poi.acquireTicket();
             return poi.getPos();
         });
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> getRandom(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> positionPredicate,
                                        PoiManager.Occupancy occupationStatus, BlockPos pos, int radius, RandomSource random) {
        List<PoiRecord> list = new ArrayList<>();
        // Diff from Paper: use load=true
        PoiAccess.findAnyPoiRecords(
                (PoiManager)(Object)this, typePredicate, positionPredicate, pos, radius, occupationStatus, true, Integer.MAX_VALUE, list
        );

        // the old method shuffled the list and then tried to find the first element in it that
        // matched positionPredicate, however we moved positionPredicate into the poi search. This means we can avoid a
        // shuffle entirely, and just pick a random element from list
        if (list.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(list.get(random.nextInt(list.size())).getPos());
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllWithType(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> predicate2,
                                                                   BlockPos blockPos, int i, PoiManager.Occupancy occupancy) {
        List<Pair<Holder<PoiType>, BlockPos>> ret = new ArrayList<>();

        PoiAccess.findAnyPoiPositions(
                (PoiManager)(Object)this, predicate, predicate2, blockPos, i, occupancy, true,
                Integer.MAX_VALUE, ret
        );

        return ret.stream();
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllClosestFirstWithType(Predicate<Holder<PoiType>> predicate,
                                                                               Predicate<BlockPos> predicate2, BlockPos blockPos,
                                                                               int i, PoiManager.Occupancy occupancy) {
        List<Pair<Holder<PoiType>, BlockPos>> ret = new ArrayList<>();

        PoiAccess.findNearestPoiPositions(
                (PoiManager)(Object)this, predicate, predicate2, blockPos, i, Double.MAX_VALUE, occupancy, true, Integer.MAX_VALUE, ret
        );

        return ret.stream();
    }
}
