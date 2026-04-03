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
    public Stream<PoiRecord> getInSquare(final Predicate<Holder<PoiType>> predicate, final BlockPos center, final int radius, final PoiManager.Occupancy occupancy) {
        final List<PoiRecord> ret = new ArrayList<>();

        PoiAccess.findAnyPoiRecords(
            (PoiManager)(Object)this, predicate, (Predicate<BlockPos>)null, center, radius, Double.MAX_VALUE, occupancy, PoiAccess.LOAD_FOR_SEARCHING, Integer.MAX_VALUE, ret
        );

        return ret.stream();
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Stream<PoiRecord> getInRange(final Predicate<Holder<PoiType>> predicate, final BlockPos center, final int radius, final PoiManager.Occupancy occupancy) {
        final List<PoiRecord> ret = new ArrayList<>();

        PoiAccess.findAnyPoiRecords(
            (PoiManager)(Object)this, predicate, (Predicate<BlockPos>)null, center, radius, (double)((long)radius * (long)radius), occupancy, PoiAccess.LOAD_FOR_SEARCHING, Integer.MAX_VALUE, ret
        );

        return ret.stream();
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Stream<BlockPos> findAll(final Predicate<Holder<PoiType>> predicate, final Predicate<BlockPos> filter, final BlockPos center, final int radius,
                                    final PoiManager.Occupancy occupancy) {
        final List<PoiRecord> ret = new ArrayList<>();

        PoiAccess.findAnyPoiRecords(
            (PoiManager)(Object)this, predicate, filter, center, radius, (double)((long)radius * (long)radius), occupancy, PoiAccess.LOAD_FOR_SEARCHING, Integer.MAX_VALUE, ret
        );

        return ret.stream().map(PoiRecord::getPos);
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllWithType(final Predicate<Holder<PoiType>> predicate, final Predicate<BlockPos> filter,
                                                                   final BlockPos center, final int radius, final PoiManager.Occupancy occupancy) {
        final List<PoiRecord> ret = new ArrayList<>();

        PoiAccess.findAnyPoiRecords(
            (PoiManager)(Object)this, predicate, filter, center, radius, (double)((long)radius * (long)radius), occupancy, PoiAccess.LOAD_FOR_SEARCHING, Integer.MAX_VALUE, ret
        );

        return ret.stream().map((final PoiRecord record) -> {
            return Pair.of(record.getPoiType(), record.getPos());
        });
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllClosestFirstWithType(final Predicate<Holder<PoiType>> predicate, final Predicate<BlockPos> filter, final BlockPos center,
                                                                               final int radius, final PoiManager.Occupancy occupancy) {
        final List<PoiRecord> ret = new ArrayList<>();

        PoiAccess.findAnyPoiRecords(
            (PoiManager)(Object)this, predicate, filter, center, radius, (double)((long)radius * (long)radius), occupancy, PoiAccess.LOAD_FOR_SEARCHING, Integer.MAX_VALUE, ret
        );

        ret.sort((final PoiRecord record1, final PoiRecord record2) -> {
            return PoiAccess.compareDistances(center, record1.getPos(), record2.getPos());
        });

        return ret.stream().map((final PoiRecord record) -> {
            return Pair.of(record.getPoiType(), record.getPos());
        });
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> find(final Predicate<Holder<PoiType>> predicate, final Predicate<BlockPos> filter, final BlockPos center,
                                   final int radius, final PoiManager.Occupancy occupancy) {
        return Optional.ofNullable(PoiAccess.findAnyPoiPosition((PoiManager)(Object)this, predicate, filter, center, radius, occupancy, PoiAccess.LOAD_FOR_SEARCHING));
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> findClosest(final Predicate<Holder<PoiType>> predicate, final BlockPos center, final int radius, final PoiManager.Occupancy occupancy) {
        final PoiRecord closest = PoiAccess.findNearestPoiRecord(
            (PoiManager)(Object)this, predicate, null, center, radius, (double)((long)radius * (long)radius), occupancy, PoiAccess.LOAD_FOR_SEARCHING
        );
        return closest == null ? Optional.empty() : Optional.of(closest.getPos());
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(final Predicate<Holder<PoiType>> predicate, final BlockPos center, final int radius,
                                                                         final PoiManager.Occupancy occupancy) {
        final PoiRecord closest = PoiAccess.findNearestPoiRecord(
            (PoiManager)(Object)this, predicate, null, center, radius, (double)((long)radius * (long)radius), occupancy, PoiAccess.LOAD_FOR_SEARCHING
        );
        return closest == null ? Optional.empty() : Optional.of(Pair.of(closest.getPoiType(), closest.getPos()));
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> findClosest(final Predicate<Holder<PoiType>> predicate, final Predicate<BlockPos> filter, final BlockPos center, final int radius,
                                          final PoiManager.Occupancy occupancy) {
        final PoiRecord closest = PoiAccess.findNearestPoiRecord(
            (PoiManager)(Object)this, predicate, filter, center, radius, (double)((long)radius * (long)radius), occupancy, PoiAccess.LOAD_FOR_SEARCHING
        );
        return closest == null ? Optional.empty() : Optional.of(closest.getPos());
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> take(final Predicate<Holder<PoiType>> predicate, final BiPredicate<Holder<PoiType>, BlockPos> filter, final BlockPos center,
                                   final int radius) {
        final PoiRecord record = PoiAccess.findAnyPoiRecord(
            (PoiManager)(Object)this, predicate, filter, center, radius, (double)((long)radius * (long)radius), PoiManager.Occupancy.HAS_SPACE, PoiAccess.LOAD_FOR_SEARCHING
        );

        if (record == null) {
            return Optional.empty();
        }

        record.acquireTicket();
        return Optional.of(record.getPos());
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> getRandom(final Predicate<Holder<PoiType>> predicate, final Predicate<BlockPos> filter, final PoiManager.Occupancy occupancy,
                                        final BlockPos center, final int radius, final RandomSource random) {
        final List<PoiRecord> list = new ArrayList<>();
        PoiAccess.findAnyPoiRecords(
                (PoiManager)(Object)this, predicate, filter, center, radius, (double)((long)radius * (long)radius), occupancy, PoiAccess.LOAD_FOR_SEARCHING, Integer.MAX_VALUE, list
        );

        // the old method shuffled the list and then tried to find the first element in it that
        // matched positionPredicate, however we moved positionPredicate into the poi search. This means we can avoid a
        // shuffle entirely, and just pick a random element from list
        if (list.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(list.get(random.nextInt(list.size())).getPos());
    }
}
