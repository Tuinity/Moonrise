package ca.spottedleaf.moonrise.mixin.poi_lookup;

import ca.spottedleaf.moonrise.patches.poi_lookup.PoiAccess;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(PoiManager.class)
abstract class PoiManagerMixin extends SectionStorage<PoiSection, PoiSection.Packed> {

    public PoiManagerMixin(final SimpleRegionStorage simpleRegionStorage, final Codec<PoiSection.Packed> codec, final Function<PoiSection, PoiSection.Packed> function, final BiFunction<PoiSection.Packed, Runnable, PoiSection> biFunction, final Function<Runnable, PoiSection> function2, final RegistryAccess registryAccess, final ChunkIOErrorReporter chunkIOErrorReporter, final LevelHeightAccessor levelHeightAccessor) {
        super(simpleRegionStorage, codec, function, biFunction, function2, registryAccess, chunkIOErrorReporter, levelHeightAccessor);
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> find(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos,
                                   int radius, PoiManager.Occupancy occupationStatus) {
        BlockPos ret = PoiAccess.findAnyPoiPosition((PoiManager)(Object)this, typePredicate, posPredicate, pos, radius, occupationStatus, PoiAccess.LOAD_FOR_SEARCHING);
        return Optional.ofNullable(ret);
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius,
                                          PoiManager.Occupancy occupationStatus) {
        BlockPos ret = PoiAccess.findClosestPoiDataPosition((PoiManager)(Object)this, typePredicate, null, pos, radius, radius * radius, occupationStatus, PoiAccess.LOAD_FOR_SEARCHING);
        return Optional.ofNullable(ret);
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(Predicate<Holder<PoiType>> typePredicate, BlockPos pos,
                                                                         int radius, PoiManager.Occupancy occupationStatus) {
        return Optional.ofNullable(PoiAccess.findClosestPoiDataTypeAndPosition(
                (PoiManager)(Object)this, typePredicate, null, pos, radius, radius * radius, occupationStatus, PoiAccess.LOAD_FOR_SEARCHING
        ));
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos,
                                          int radius, PoiManager.Occupancy occupationStatus) {
        BlockPos ret = PoiAccess.findClosestPoiDataPosition((PoiManager)(Object)this, typePredicate, posPredicate, pos, radius, radius * radius, occupationStatus, PoiAccess.LOAD_FOR_SEARCHING);
        return Optional.ofNullable(ret);
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> take(Predicate<Holder<PoiType>> typePredicate, BiPredicate<Holder<PoiType>, BlockPos> biPredicate,
                                   BlockPos pos, int radius) {
        final PoiRecord closest = PoiAccess.findClosestPoiDataRecord(
                (PoiManager)(Object)this, typePredicate, biPredicate, pos, radius, radius * radius, PoiManager.Occupancy.HAS_SPACE, PoiAccess.LOAD_FOR_SEARCHING
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
        PoiAccess.findAnyPoiRecords(
                (PoiManager)(Object)this, typePredicate, positionPredicate, pos, radius, occupationStatus, PoiAccess.LOAD_FOR_SEARCHING, Integer.MAX_VALUE, list
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
                (PoiManager)(Object)this, predicate, predicate2, blockPos, i, occupancy, PoiAccess.LOAD_FOR_SEARCHING,
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
                (PoiManager)(Object)this, predicate, predicate2, blockPos, i, Double.MAX_VALUE, occupancy, PoiAccess.LOAD_FOR_SEARCHING, Integer.MAX_VALUE, ret
        );

        return ret.stream();
    }
}
