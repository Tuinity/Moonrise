package ca.spottedleaf.moonrise.common.util;

import ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import java.util.Arrays;

/**
 * Interns reusable geometry retained by VoxelShape collision caches.
 * Geometry shared here is treated as immutable after VoxelShape construction.
 * Pools are bounded because their entries live for the lifetime of the process.
 */
public final class VoxelShapeInternPool {

    private static final int MAX_COORDINATE_PATTERNS = 4096;
    private static final int MAX_COORDINATE_LIST_SIZE = 32;

    // Global interning is for reusable block-local geometry. World-space geometry has
    // high-cardinality coordinates and should bypass hashing/locking. This only controls whether
    // an object is shared; values outside this range keep their normal behaviour.
    private static final double MAX_ABSOLUTE_LOCAL_COORDINATE = 16.0;

    private static final int MAX_SHAPE_PATTERNS = 8192;
    // 64 words cover a 16x16x16 voxel grid. Larger shapes are not retained globally.
    private static final int MAX_VOXEL_SET_WORDS = 64;

    private static final int MAX_AABB_PATTERNS = MAX_SHAPE_PATTERNS * 2;

    private static final Hash.Strategy<DoubleArrayList> COORDINATE_LIST_STRATEGY = new Hash.Strategy<>() {
        @Override
        public int hashCode(final DoubleArrayList list) {
            if (list == null) {
                return 0;
            }

            int hash = 1;
            for (int i = 0, len = list.size(); i < len; ++i) {
                hash = 31 * hash + Double.hashCode(list.getDouble(i));
            }
            return hash;
        }

        @Override
        public boolean equals(final DoubleArrayList first, final DoubleArrayList second) {
            if (first == second) {
                return true;
            }
            if (first == null || second == null || first.size() != second.size()) {
                return false;
            }

            for (int i = 0, len = first.size(); i < len; ++i) {
                if (Double.doubleToLongBits(first.getDouble(i)) != Double.doubleToLongBits(second.getDouble(i))) {
                    return false;
                }
            }
            return true;
        }
    };

    private static final Hash.Strategy<CachedShapeData> SHAPE_DATA_STRATEGY = new Hash.Strategy<>() {
        @Override
        public int hashCode(final CachedShapeData data) {
            if (data == null) {
                return 0;
            }

            int hash = Arrays.hashCode(data.voxelSet());
            hash = 31 * hash + data.sizeX();
            hash = 31 * hash + data.sizeY();
            hash = 31 * hash + data.sizeZ();
            hash = 31 * hash + data.minFullX();
            hash = 31 * hash + data.minFullY();
            hash = 31 * hash + data.minFullZ();
            hash = 31 * hash + data.maxFullX();
            hash = 31 * hash + data.maxFullY();
            hash = 31 * hash + data.maxFullZ();
            hash = 31 * hash + (data.isEmpty() ? 1 : 0);
            hash = 31 * hash + (data.hasSingleAABB() ? 1 : 0);
            return hash;
        }

        @Override
        public boolean equals(final CachedShapeData first, final CachedShapeData second) {
            if (first == second) {
                return true;
            }
            if (first == null || second == null) {
                return false;
            }

            return first.sizeX() == second.sizeX()
                    && first.sizeY() == second.sizeY()
                    && first.sizeZ() == second.sizeZ()
                    && first.minFullX() == second.minFullX()
                    && first.minFullY() == second.minFullY()
                    && first.minFullZ() == second.minFullZ()
                    && first.maxFullX() == second.maxFullX()
                    && first.maxFullY() == second.maxFullY()
                    && first.maxFullZ() == second.maxFullZ()
                    && first.isEmpty() == second.isEmpty()
                    && first.hasSingleAABB() == second.hasSingleAABB()
                    && Arrays.equals(first.voxelSet(), second.voxelSet());
        }
    };

    private static final Hash.Strategy<AABB> AABB_STRATEGY = new Hash.Strategy<>() {
        @Override
        public int hashCode(final AABB box) {
            if (box == null) {
                return 0;
            }

            int hash = Long.hashCode(Double.doubleToLongBits(box.minX));
            hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(box.minY));
            hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(box.minZ));
            hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(box.maxX));
            hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(box.maxY));
            hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(box.maxZ));
            return hash;
        }

        @Override
        public boolean equals(final AABB first, final AABB second) {
            if (first == second) {
                return true;
            }
            if (first == null || second == null) {
                return false;
            }

            return Double.doubleToLongBits(first.minX) == Double.doubleToLongBits(second.minX)
                    && Double.doubleToLongBits(first.minY) == Double.doubleToLongBits(second.minY)
                    && Double.doubleToLongBits(first.minZ) == Double.doubleToLongBits(second.minZ)
                    && Double.doubleToLongBits(first.maxX) == Double.doubleToLongBits(second.maxX)
                    && Double.doubleToLongBits(first.maxY) == Double.doubleToLongBits(second.maxY)
                    && Double.doubleToLongBits(first.maxZ) == Double.doubleToLongBits(second.maxZ);
        }
    };

    private static final ObjectOpenCustomHashSet<DoubleArrayList> COORDINATE_LISTS =
        new ObjectOpenCustomHashSet<>(COORDINATE_LIST_STRATEGY);

    private static final ObjectOpenCustomHashSet<CachedShapeData> SHAPE_DATA =
        new ObjectOpenCustomHashSet<>(SHAPE_DATA_STRATEGY);

    // Eligible CachedShapeData is canonical before it reaches this map, so identity is sufficient
    // and avoids hashing the voxel array a second time during DiscreteVoxelShape interning.
    private static final Reference2ObjectOpenHashMap<CachedShapeData, DiscreteVoxelShape> DISCRETE_SHAPES =
        new Reference2ObjectOpenHashMap<>();

    private static final ObjectOpenCustomHashSet<AABB> AABBS =
        new ObjectOpenCustomHashSet<>(AABB_STRATEGY);

    private VoxelShapeInternPool() {}

    private static boolean isOutsideLocalInterningRange(final double value) {
        return !Double.isFinite(value) || Math.abs(value) > MAX_ABSOLUTE_LOCAL_COORDINATE;
    }

    private static boolean hasOutOfRangeCoordinates(final DoubleArrayList list) {
        for (int i = 0, len = list.size(); i < len; ++i) {
            if (isOutsideLocalInterningRange(list.getDouble(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOutOfRangeCoordinates(final AABB box) {
        return isOutsideLocalInterningRange(box.minX)
            || isOutsideLocalInterningRange(box.minY)
            || isOutsideLocalInterningRange(box.minZ)
            || isOutsideLocalInterningRange(box.maxX)
            || isOutsideLocalInterningRange(box.maxY)
            || isOutsideLocalInterningRange(box.maxZ);
    }

    private static boolean canInternCoordinateList(final DoubleList list) {
        if (list == null || list.getClass() != DoubleArrayList.class) {
            return false;
        }

        final DoubleArrayList rawList = (DoubleArrayList)list;
        final int size = rawList.size();
        return size > 0 && size <= MAX_COORDINATE_LIST_SIZE && !hasOutOfRangeCoordinates(rawList);
    }

    public static DoubleList internCoordinateList(final DoubleList list) {
        if (!canInternCoordinateList(list)) {
            return list;
        }

        final DoubleArrayList rawList = (DoubleArrayList)list;
        final int size = rawList.size();

        synchronized (COORDINATE_LISTS) {
            final DoubleArrayList existing = COORDINATE_LISTS.get(rawList);
            if (existing != null) {
                return existing;
            }
            if (COORDINATE_LISTS.size() >= MAX_COORDINATE_PATTERNS) {
                return list;
            }

            // Only allocate a trimmed backing array for a value that will actually be retained.
            final double[] raw = rawList.elements();
            final DoubleArrayList candidate = raw.length == size
                ? rawList
                : DoubleArrayList.wrap(Arrays.copyOf(raw, size));
            COORDINATE_LISTS.add(candidate);
            return candidate;
        }
    }

    public static CachedShapeData internShapeData(final CachedShapeData data) {
        if (exceedsShapeInterningLimit(data)) {
            return data;
        }

        synchronized (SHAPE_DATA) {
            if (SHAPE_DATA.size() >= MAX_SHAPE_PATTERNS) {
                final CachedShapeData existing = SHAPE_DATA.get(data);
                return existing != null ? existing : data;
            }

            return SHAPE_DATA.addOrGet(data);
        }
    }

    public static DiscreteVoxelShape internDiscreteShape(final DiscreteVoxelShape shape) {
        // A subclass may contain state which is not represented by CachedShapeData.
        if (shape == null || shape.getClass() != BitSetDiscreteVoxelShape.class) {
            return shape;
        }

        // Mixin adds CollisionDiscreteVoxelShape to DiscreteVoxelShape at runtime.
        final CachedShapeData data = ((CollisionDiscreteVoxelShape)(Object)shape).moonrise$getOrCreateCachedShapeData(true);
        if (exceedsShapeInterningLimit(data)) {
            return shape;
        }

        synchronized (DISCRETE_SHAPES) {
            final DiscreteVoxelShape existing = DISCRETE_SHAPES.get(data);
            if (existing != null) {
                return existing;
            }

            if (DISCRETE_SHAPES.size() >= MAX_SHAPE_PATTERNS) {
                return shape;
            }

            DISCRETE_SHAPES.put(data, shape);
            return shape;
        }
    }

    public static AABB internAABB(final AABB box) {
        // Reject high-cardinality world-space values before hashing or taking the pool lock.
        if (hasOutOfRangeCoordinates(box)) {
            return box;
        }

        synchronized (AABBS) {
            if (AABBS.size() >= MAX_AABB_PATTERNS) {
                final AABB existing = AABBS.get(box);
                return existing != null ? existing : box;
            }

            return AABBS.addOrGet(box);
        }
    }

    private static boolean exceedsShapeInterningLimit(final CachedShapeData data) {
        return data.voxelSet().length > MAX_VOXEL_SET_WORDS;
    }
}
