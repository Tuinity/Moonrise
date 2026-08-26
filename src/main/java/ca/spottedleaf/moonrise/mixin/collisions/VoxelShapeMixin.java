package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.FlatBitsetUtil;
import ca.spottedleaf.moonrise.common.util.VoxelShapeInternPool;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData;
import ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionArrayVoxelShape;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache;
import com.google.common.math.DoubleMath;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.OffsetDoubleList;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Mixin(VoxelShape.class)
abstract class VoxelShapeMixin implements CollisionVoxelShape {

    @Shadow
    public abstract DoubleList getCoords(final Direction.Axis axis);

    @Shadow
    @Final
    @Mutable
    public DiscreteVoxelShape shape;

    @Unique
    private double offsetX;
    @Unique
    private double offsetY;
    @Unique
    private double offsetZ;
    @Unique
    private double[] rootCoordinatesX;
    @Unique
    private double[] rootCoordinatesY;
    @Unique
    private double[] rootCoordinatesZ;

    @Unique
    private CachedShapeData cachedShapeData;

    // null = uncached, List<AABB> = materialized, CachedToAABBs = delayed offset
    @Unique
    private Object cachedToAABBs;
    // Also stores the single-AABB representation when FLAG_SINGLE_AABB is set.
    @Unique
    private AABB cachedBounds;

    // Pack small cache states into one field to reduce the per-shape footprint.
    @Unique
    private int cacheFlags;

    @Unique
    private static final int FLAG_EMPTY = 1;
    @Unique
    private static final int FLAG_SINGLE_AABB = 1 << 1;
    @Unique
    private static final int FLAG_FULL_BLOCK_KNOWN = 1 << 2;
    @Unique
    private static final int FLAG_FULL_BLOCK_VALUE = 1 << 3;
    @Unique
    private static final int FLAG_OCCLUDES_FULL_BLOCK_KNOWN = 1 << 4;
    @Unique
    private static final int FLAG_OCCLUDES_FULL_BLOCK_VALUE = 1 << 5;
    @Unique
    private static final int FLAG_RETAINED_GEOMETRY_INTERNED = 1 << 6;

    // must be power of two
    @Unique
    private static final int MERGED_CACHE_SIZE = 16;

    @Unique
    private MergedORCache[] mergedORCache;

    @Override
    public final double moonrise$offsetX() {
        return this.offsetX;
    }

    @Override
    public final double moonrise$offsetY() {
        return this.offsetY;
    }

    @Override
    public final double moonrise$offsetZ() {
        return this.offsetZ;
    }

    @Override
    public final AABB moonrise$getSingleAABBRepresentation() {
        return (this.cacheFlags & FLAG_SINGLE_AABB) != 0 ? this.cachedBounds : null;
    }

    @Unique
    private boolean moonrise$isEmptyCached() {
        return (this.cacheFlags & FLAG_EMPTY) != 0;
    }

    @Unique
    private void moonrise$setFullBlockCached(final boolean value) {
        int flags = this.cacheFlags | FLAG_FULL_BLOCK_KNOWN;
        if (value) {
            flags |= FLAG_FULL_BLOCK_VALUE;
        } else {
            flags &= ~FLAG_FULL_BLOCK_VALUE;
        }
        this.cacheFlags = flags;
    }

    @Unique
    private void moonrise$setOccludesFullBlockCached(final boolean value) {
        int flags = this.cacheFlags | FLAG_OCCLUDES_FULL_BLOCK_KNOWN;
        if (value) {
            flags |= FLAG_OCCLUDES_FULL_BLOCK_VALUE;
        } else {
            flags &= ~FLAG_OCCLUDES_FULL_BLOCK_VALUE;
        }
        this.cacheFlags = flags;
    }

    @Override
    public final double[] moonrise$rootCoordinatesX() {
        return this.rootCoordinatesX;
    }

    @Override
    public final double[] moonrise$rootCoordinatesY() {
        return this.rootCoordinatesY;
    }

    @Override
    public final double[] moonrise$rootCoordinatesZ() {
        return this.rootCoordinatesZ;
    }

    @Unique
    private static double[] extractRawArray(final DoubleList list) {
        if (list instanceof DoubleArrayList rawList) {
            final double[] raw = rawList.elements();
            final int expected = rawList.size();
            return raw.length == expected ? raw : Arrays.copyOf(raw, expected);
        }

        return list.toDoubleArray();
    }

    @Override
    public final void moonrise$initCache() {
        // Construction itself is not proof of retention.
        this.cachedShapeData = ((CollisionDiscreteVoxelShape)(Object)this.shape).moonrise$getOrCreateCachedShapeData(false);
        if (this.cachedShapeData.isEmpty()) {
            this.cacheFlags |= FLAG_EMPTY;
        }

        final DoubleList xList = this.getCoords(Direction.Axis.X);
        final DoubleList yList = this.getCoords(Direction.Axis.Y);
        final DoubleList zList = this.getCoords(Direction.Axis.Z);

        if (xList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetX = offsetDoubleList.offset;
            this.rootCoordinatesX = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.rootCoordinatesX = extractRawArray(xList);
        }

        if (yList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetY = offsetDoubleList.offset;
            this.rootCoordinatesY = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.rootCoordinatesY = extractRawArray(yList);
        }

        if (zList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetZ = offsetDoubleList.offset;
            this.rootCoordinatesZ = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.rootCoordinatesZ = extractRawArray(zList);
        }

        if (this.cachedShapeData.hasSingleAABB()) {
            this.cacheFlags |= FLAG_SINGLE_AABB;
            this.cachedBounds = new AABB(
                    this.rootCoordinatesX[0] + this.offsetX, this.rootCoordinatesY[0] + this.offsetY, this.rootCoordinatesZ[0] + this.offsetZ,
                    this.rootCoordinatesX[1] + this.offsetX, this.rootCoordinatesY[1] + this.offsetY, this.rootCoordinatesZ[1] + this.offsetZ
            );
        }
    }

    @Override
    public final void moonrise$promoteRetainedGeometry() {
        if ((this.cacheFlags & FLAG_RETAINED_GEOMETRY_INTERNED) != 0) {
            return;
        }

        // ArrayVoxelShape owns the retained DoubleList fields. Canonicalise those
        // first, then refresh the raw coordinate references cached in VoxelShape.
        if ((Object)this instanceof CollisionArrayVoxelShape arrayShape) {
            arrayShape.moonrise$internRetainedCoordinates();

            final DoubleList xList = this.getCoords(Direction.Axis.X);
            final DoubleList yList = this.getCoords(Direction.Axis.Y);
            final DoubleList zList = this.getCoords(Direction.Axis.Z);

            if (xList instanceof OffsetDoubleList offsetDoubleList) {
                this.offsetX = offsetDoubleList.offset;
                this.rootCoordinatesX = extractRawArray(offsetDoubleList.delegate);
            } else {
                this.offsetX = 0.0;
                this.rootCoordinatesX = extractRawArray(xList);
            }

            if (yList instanceof OffsetDoubleList offsetDoubleList) {
                this.offsetY = offsetDoubleList.offset;
                this.rootCoordinatesY = extractRawArray(offsetDoubleList.delegate);
            } else {
                this.offsetY = 0.0;
                this.rootCoordinatesY = extractRawArray(yList);
            }

            if (zList instanceof OffsetDoubleList offsetDoubleList) {
                this.offsetZ = offsetDoubleList.offset;
                this.rootCoordinatesZ = extractRawArray(offsetDoubleList.delegate);
            } else {
                this.offsetZ = 0.0;
                this.rootCoordinatesZ = extractRawArray(zList);
            }
        }

        // Promote CachedShapeData before the identity-keyed DiscreteVoxelShape pool.
        this.cachedShapeData =
            ((CollisionDiscreteVoxelShape)(Object)this.shape).moonrise$getOrCreateCachedShapeData(true);
        this.shape = VoxelShapeInternPool.internDiscreteShape(this.shape);
        this.cachedShapeData =
            ((CollisionDiscreteVoxelShape)(Object)this.shape).moonrise$getOrCreateCachedShapeData(true);

        if (this.cachedBounds != null
            && this.offsetX == 0.0 && this.offsetY == 0.0 && this.offsetZ == 0.0) {
            this.cachedBounds = VoxelShapeInternPool.internAABB(this.cachedBounds);
        }

        this.cacheFlags |= FLAG_RETAINED_GEOMETRY_INTERNED;
    }

    @Override
    public final CachedShapeData moonrise$getCachedVoxelData() {
        return this.cachedShapeData;
    }

    @Unique
    private VoxelShape[] faceShapeClampedCache;

    @Override
    public final VoxelShape moonrise$getFaceShapeClamped(final Direction direction) {
        if (this.moonrise$isEmptyCached()) {
            return (VoxelShape)(Object)this;
        }
        if ((VoxelShape)(Object)this == Shapes.block()) {
            return (VoxelShape)(Object)this;
        }

        VoxelShape[] cache = this.faceShapeClampedCache;
        if (cache != null) {
            final VoxelShape cached = cache[direction.ordinal()];
            if (cached != null) {
                return cached;
            }
        }

        if (cache == null) {
            this.faceShapeClampedCache = cache = new VoxelShape[6];
        }

        final Direction.Axis axis = direction.getAxis();
        final VoxelShape ret;

        if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            if (DoubleMath.fuzzyEquals(this.max(axis), 1.0, CollisionUtil.COLLISION_EPSILON)) {
                ret = CollisionUtil.sliceShape((VoxelShape)(Object)this, axis, this.shape.getSize(axis) - 1);
            } else {
                ret = Shapes.empty();
            }
        } else {
            if (DoubleMath.fuzzyEquals(this.min(axis), 0.0, CollisionUtil.COLLISION_EPSILON)) {
                ret = CollisionUtil.sliceShape((VoxelShape)(Object)this, axis, 0);
            } else {
                ret = Shapes.empty();
            }
        }

        cache[direction.ordinal()] = ret;
        return ret;
    }

    @Unique
    private boolean computeOccludesFullBlock() {
        if (this.moonrise$isEmptyCached()) {
            this.moonrise$setOccludesFullBlockCached(false);
            return false;
        }

        if (this.moonrise$isFullBlock()) {
            this.moonrise$setOccludesFullBlockCached(true);
            return true;
        }

        final AABB singleAABB = this.moonrise$getSingleAABBRepresentation();
        if (singleAABB != null) {
            // check if the bounding box encloses the full cube
            final boolean ret =
                    (singleAABB.minY <= CollisionUtil.COLLISION_EPSILON && singleAABB.maxY >= (1 - CollisionUtil.COLLISION_EPSILON)) &&
                    (singleAABB.minX <= CollisionUtil.COLLISION_EPSILON && singleAABB.maxX >= (1 - CollisionUtil.COLLISION_EPSILON)) &&
                    (singleAABB.minZ <= CollisionUtil.COLLISION_EPSILON && singleAABB.maxZ >= (1 - CollisionUtil.COLLISION_EPSILON));
            this.moonrise$setOccludesFullBlockCached(ret);
            return ret;
        }

        final boolean ret = !Shapes.joinIsNotEmpty(Shapes.block(), ((VoxelShape)(Object)this), BooleanOp.ONLY_FIRST);
        this.moonrise$setOccludesFullBlockCached(ret);
        return ret;
    }

    @Override
    public final boolean moonrise$occludesFullBlock() {
        final int flags = this.cacheFlags;
        if ((flags & FLAG_OCCLUDES_FULL_BLOCK_KNOWN) != 0) {
            return (flags & FLAG_OCCLUDES_FULL_BLOCK_VALUE) != 0;
        }

        return this.computeOccludesFullBlock();
    }

    @Override
    public final boolean moonrise$occludesFullBlockIfCached() {
        final int flags = this.cacheFlags;
        return (flags & FLAG_OCCLUDES_FULL_BLOCK_KNOWN) != 0
            && (flags & FLAG_OCCLUDES_FULL_BLOCK_VALUE) != 0;
    }

    @Unique
    private static int hash(final VoxelShape key) {
        return HashCommon.mix(System.identityHashCode(key));
    }

    @Override
    public final VoxelShape moonrise$orUnoptimized(final VoxelShape other) {
        // don't cache simple cases
        if (((VoxelShape)(Object)this) == other) {
            return other;
        }

        if (this.moonrise$isEmptyCached()) {
            return other;
        }

        if (other.isEmpty()) {
            return (VoxelShape)(Object)this;
        }

        // try this cache first
        final int thisCacheKey = hash(other) & (MERGED_CACHE_SIZE - 1);
        final MergedORCache cached = this.mergedORCache == null ? null : this.mergedORCache[thisCacheKey];
        if (cached != null && cached.key() == other) {
            return cached.result();
        }

        // try other cache
        final int otherCacheKey = hash((VoxelShape)(Object)this) & (MERGED_CACHE_SIZE - 1);
        final MergedORCache otherCache = ((VoxelShapeMixin)(Object)other).mergedORCache == null ? null : ((VoxelShapeMixin)(Object)other).mergedORCache[otherCacheKey];
        if (otherCache != null && otherCache.key() == (VoxelShape)(Object)this) {
            return otherCache.result();
        }

        // note: unsure if joinUnoptimized(1, 2, OR) == joinUnoptimized(2, 1, OR) for all cases
        final VoxelShape result = Shapes.joinUnoptimized((VoxelShape)(Object)this, other, BooleanOp.OR);

        if (cached != null && otherCache == null) {
            // try to use second cache instead of replacing an entry in this cache
            if (((VoxelShapeMixin)(Object)other).mergedORCache == null) {
                ((VoxelShapeMixin)(Object)other).mergedORCache = new MergedORCache[MERGED_CACHE_SIZE];
            }
            ((VoxelShapeMixin)(Object)other).mergedORCache[otherCacheKey] = new MergedORCache((VoxelShape)(Object)this, result);
        } else {
            // line is not occupied or other cache line is full
            // always bias to replace this cache, as this cache is the first we check
            if (this.mergedORCache == null) {
                this.mergedORCache = new MergedORCache[MERGED_CACHE_SIZE];
            }
            this.mergedORCache[thisCacheKey] = new MergedORCache(other, result);
        }

        return result;
    }

    // mixin hooks

    /**
     * @author Spottedleaf
     * @reason Use cached value instead
     */
    @Overwrite
    public boolean isEmpty() {
        return this.moonrise$isEmptyCached();
    }

    /**
     * @author Spottedleaf
     * @reason Use cached bounds
     */
    @Overwrite
    public VoxelShape singleEncompassing() {
        if (this.moonrise$isEmptyCached()) {
            return Shapes.empty();
        }
        return Shapes.create(this.bounds());
    }

    /**
     * @author Spottedleaf
     * @reason Optimise implementation to avoid indirection
     */
    @Overwrite
    protected double get(final Direction.Axis axis, final int idx) {
        switch (axis) {
            case X: {
                return this.rootCoordinatesX[idx] + this.offsetX;
            }
            case Y: {
                return this.rootCoordinatesY[idx] + this.offsetY;
            }
            case Z: {
                return this.rootCoordinatesZ[idx] + this.offsetZ;
            }
            default: {
                throw new IllegalStateException("Unknown axis: " + axis);
            }
        }
    }

    /**
     * @author Spottedleaf
     * @reason Optimise implementation to avoid indirection
     */
    @Overwrite
    public int findIndex(final Direction.Axis axis, final double value) {
        switch (axis) {
            case X: {
                final double[] values = this.rootCoordinatesX;
                return CollisionUtil.findFloor(
                    values, this.offsetX, value, 0, values.length - 1
                );
            }
            case Y: {
                final double[] values = this.rootCoordinatesY;
                return CollisionUtil.findFloor(
                    values, this.offsetY, value, 0, values.length - 1
                );
            }
            case Z: {
                final double[] values = this.rootCoordinatesZ;
                return CollisionUtil.findFloor(
                    values, this.offsetZ, value, 0, values.length - 1
                );
            }
            default: {
                throw new IllegalStateException("Unknown axis: " + axis);
            }
        }
    }

    @Unique
    private VoxelShape calculateFaceDirect(final Direction direction, final Direction.Axis axis, final double[] coords, final double offset) {
        if (coords.length == 2 &&
            DoubleMath.fuzzyEquals(coords[0] + offset, 0.0, CollisionUtil.COLLISION_EPSILON) &&
            DoubleMath.fuzzyEquals(coords[1] + offset, 1.0, CollisionUtil.COLLISION_EPSILON)) {
            return (VoxelShape)(Object)this;
        }

        final boolean positiveDir = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;

        // see findIndex
        final int index = CollisionUtil.findFloor(
            coords, offset, (positiveDir ? (1.0 - CollisionUtil.COLLISION_EPSILON) : (0.0 + CollisionUtil.COLLISION_EPSILON)),
            0, coords.length - 1
        );

        return CollisionUtil.sliceShape(
            (VoxelShape)(Object)this, axis, index
        );
    }

    /**
     * @author Spottedleaf
     * @reason Avoid creating SliceShape
     */
    @Overwrite
    public VoxelShape calculateFace(final Direction direction) {
        final Direction.Axis axis = direction.getAxis();
        switch (axis) {
            case X: {
                return this.calculateFaceDirect(direction, axis, this.rootCoordinatesX, this.offsetX);
            }
            case Y: {
                return this.calculateFaceDirect(direction, axis, this.rootCoordinatesY, this.offsetY);
            }
            case Z: {
                return this.calculateFaceDirect(direction, axis, this.rootCoordinatesZ, this.offsetZ);
            }
            default: {
                throw new IllegalStateException("Unknown axis: " + axis);
            }
        }
    }

    /**
     * @author Spottedleaf
     * @reason Route to optimized collision method
     */
    @Overwrite
    public double collide(final Direction.Axis axis, final AABB source, final double source_move) {
        if (this.moonrise$isEmptyCached()) {
            return source_move;
        }
        if (Math.abs(source_move) < CollisionUtil.COLLISION_EPSILON) {
            return 0.0;
        }
        switch (axis) {
            case X: {
                return CollisionUtil.collideX((VoxelShape)(Object)this, source, source_move);
            }
            case Y: {
                return CollisionUtil.collideY((VoxelShape)(Object)this, source, source_move);
            }
            case Z: {
                return CollisionUtil.collideZ((VoxelShape)(Object)this, source, source_move);
            }
            default: {
                throw new RuntimeException("Unknown axis: " + axis);
            }
        }
    }

    @Unique
    private static DoubleList offsetList(final double[] src, final double by) {
        final DoubleArrayList wrap = DoubleArrayList.wrap(src);
        if (by == 0.0) {
            return wrap;
        }
        return new OffsetDoubleList(wrap, by);
    }

    /**
     * @author Spottedleaf
     * @reason Do not nest offset double lists
     */
    @Overwrite
    public VoxelShape move(final double x, final double y, final double z) {
        if (this.moonrise$isEmptyCached()) {
            return Shapes.empty();
        }

        final ArrayVoxelShape ret = new ArrayVoxelShape(
            this.shape,
            offsetList(this.rootCoordinatesX, this.offsetX + x),
            offsetList(this.rootCoordinatesY, this.offsetY + y),
            offsetList(this.rootCoordinatesZ, this.offsetZ + z)
        );

        final Object cached = this.cachedToAABBs;
        if (cached instanceof CachedToAABBs offsetCache) {
            ((VoxelShapeMixin)(Object)ret).cachedToAABBs = offsetCache.offset(x, y, z);
        } else if (cached instanceof List<?> list) {
            if (x == 0.0 && y == 0.0 && z == 0.0) {
                ((VoxelShapeMixin)(Object)ret).cachedToAABBs = list;
            } else {
                @SuppressWarnings("unchecked")
                final List<AABB> aabbs = (List<AABB>)list;
                ((VoxelShapeMixin)(Object)ret).cachedToAABBs = new CachedToAABBs(aabbs, x, y, z);
            }
        }

        return ret;
    }

    @Unique
    private List<AABB> toAabbsUncached() {
        final ArrayList<AABB> ret;
        final AABB singleAABB = this.moonrise$getSingleAABBRepresentation();

        if (singleAABB != null) {
            ret = new ArrayList<>(1);
            ret.add(singleAABB);
        } else {
            ret = new ArrayList<>();

            final double[] coordsX = this.rootCoordinatesX;
            final double[] coordsY = this.rootCoordinatesY;
            final double[] coordsZ = this.rootCoordinatesZ;

            final double offX = this.offsetX;
            final double offY = this.offsetY;
            final double offZ = this.offsetZ;
            final boolean hasNoOffset = offX == 0.0 && offY == 0.0 && offZ == 0.0;

            this.shape.forAllBoxes((final int minX, final int minY, final int minZ,
                                    final int maxX, final int maxY, final int maxZ) -> {
                AABB box = new AABB(
                        coordsX[minX] + offX,
                        coordsY[minY] + offY,
                        coordsZ[minZ] + offZ,

                        coordsX[maxX] + offX,
                        coordsY[maxY] + offY,
                        coordsZ[maxZ] + offZ
                );

                if (hasNoOffset) {
                    box = VoxelShapeInternPool.internAABB(box);
                }

                ret.add(box);
            }, true);

            // retained for the lifetime of the shape
            ret.trimToSize();
        }

        this.cachedToAABBs = ret;

        return ret;
    }

    /**
     * @author Spottedleaf
     * @reason Cache toAABBs result
     */
    @Overwrite
    public List<AABB> toAabbs() {
        final Object cached = this.cachedToAABBs;

        if (cached instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            final List<AABB> ret = (List<AABB>)list;
            return ret;
        }

        if (cached instanceof CachedToAABBs offsetCache) {
            final List<AABB> ret = offsetCache.removeOffset();
            this.cachedToAABBs = ret;
            return ret;
        }

        return this.toAabbsUncached();
    }

    @Unique
    private boolean computeFullBlock() {
        boolean ret;

        if (this.moonrise$isEmptyCached()) {
            ret = false;
        } else if ((VoxelShape)(Object)this == Shapes.block()) {
            ret = true;
        } else {
            final AABB singleAABB = this.moonrise$getSingleAABBRepresentation();
            if (singleAABB == null) {
                final CachedShapeData shapeData = this.cachedShapeData;
                final int sMinX = shapeData.minFullX();
                final int sMinY = shapeData.minFullY();
                final int sMinZ = shapeData.minFullZ();

                final int sMaxX = shapeData.maxFullX();
                final int sMaxY = shapeData.maxFullY();
                final int sMaxZ = shapeData.maxFullZ();

                if (Math.abs(this.rootCoordinatesX[sMinX] + this.offsetX) <= CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(this.rootCoordinatesY[sMinY] + this.offsetY) <= CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(this.rootCoordinatesZ[sMinZ] + this.offsetZ) <= CollisionUtil.COLLISION_EPSILON &&

                    Math.abs(1.0 - (this.rootCoordinatesX[sMaxX] + this.offsetX)) <= CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(1.0 - (this.rootCoordinatesY[sMaxY] + this.offsetY)) <= CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(1.0 - (this.rootCoordinatesZ[sMaxZ] + this.offsetZ)) <= CollisionUtil.COLLISION_EPSILON) {

                    // index = z + y*sizeZ + x*(sizeZ*sizeY)

                    final int sizeY = shapeData.sizeY();
                    final int sizeZ = shapeData.sizeZ();

                    final long[] bitset = shapeData.voxelSet();

                    ret = true;

                    check_full:
                    for (int x = sMinX; x < sMaxX; ++x) {
                        for (int y = sMinY; y < sMaxY; ++y) {
                            final int baseIndex = y*sizeZ + x*(sizeZ*sizeY);
                            if (!FlatBitsetUtil.isRangeSet(bitset, baseIndex + sMinZ, baseIndex + sMaxZ)) {
                                ret = false;
                                break check_full;
                            }
                        }
                    }
                } else {
                    ret = false;
                }
            } else {
                ret =
                        Math.abs(singleAABB.minX) <= CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(singleAABB.minY) <= CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(singleAABB.minZ) <= CollisionUtil.COLLISION_EPSILON &&

                        Math.abs(1.0 - singleAABB.maxX) <= CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(1.0 - singleAABB.maxY) <= CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(1.0 - singleAABB.maxZ) <= CollisionUtil.COLLISION_EPSILON;
            }
        }

        this.moonrise$setFullBlockCached(ret);
        return ret;
    }

    @Override
    public final boolean moonrise$isFullBlock() {
        final int flags = this.cacheFlags;

        if ((flags & FLAG_FULL_BLOCK_KNOWN) != 0) {
            return (flags & FLAG_FULL_BLOCK_VALUE) != 0;
        }

        return this.computeFullBlock();
    }

    /**
     * Copy of AABB#clip but for one AABB
     */
    @Unique
    private static BlockHitResult clip(final AABB aabb, final Vec3 from, final Vec3 to, final BlockPos offset) {
        final double[] minDistanceArr = new double[] { 1.0 };
        final double diffX = to.x - from.x;
        final double diffY = to.y - from.y;
        final double diffZ = to.z - from.z;

        final Direction direction = AABB.getDirection(aabb.move(offset), from, minDistanceArr, null, diffX, diffY, diffZ);

        if (direction == null) {
            return null;
        }

        final double minDistance = minDistanceArr[0];
        return new BlockHitResult(from.add(minDistance * diffX, minDistance * diffY, minDistance * diffZ), direction, offset, false);
    }

    /**
     * @reason Use single cached AABB for clipping if possible
     * @author Spottedleaf
     */
    @Overwrite
    public BlockHitResult clip(final Vec3 from, final Vec3 to, final BlockPos offset) {
        if (this.moonrise$isEmptyCached()) {
            return null;
        }

        final Vec3 directionOpposite = to.subtract(from);
        if (directionOpposite.lengthSqr() < CollisionUtil.COLLISION_EPSILON) {
            return null;
        }

        final Vec3 fromBehind = from.add(directionOpposite.scale(0.001));
        final double fromBehindOffsetX = fromBehind.x - (double)offset.getX();
        final double fromBehindOffsetY = fromBehind.y - (double)offset.getY();
        final double fromBehindOffsetZ = fromBehind.z - (double)offset.getZ();

        final AABB singleAABB = this.moonrise$getSingleAABBRepresentation();
        if (singleAABB != null) {
            if (singleAABB.contains(fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
                return new BlockHitResult(fromBehind, Direction.getApproximateNearest(directionOpposite.x, directionOpposite.y, directionOpposite.z).getOpposite(), offset, true);
            }
            return clip(singleAABB, from, to, offset);
        }

        if (CollisionUtil.strictlyContains((VoxelShape)(Object)this, fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
            return new BlockHitResult(fromBehind, Direction.getApproximateNearest(directionOpposite.x, directionOpposite.y, directionOpposite.z).getOpposite(), offset, true);
        }

        return AABB.clip(((VoxelShape)(Object)this).toAabbs(), from, to, offset);
    }

    /**
     * @reason Cache bounds
     * @author Spottedleaf
     */
    @Overwrite
    public AABB bounds() {
        if (this.moonrise$isEmptyCached()) {
            throw Util.pauseInIde(new UnsupportedOperationException("No bounds for empty shape."));
        }
        AABB cached = this.cachedBounds;
        if (cached != null) {
            return cached;
        }

        final CachedShapeData shapeData = this.cachedShapeData;

        final double[] coordsX = this.rootCoordinatesX;
        final double[] coordsY = this.rootCoordinatesY;
        final double[] coordsZ = this.rootCoordinatesZ;

        final double offX = this.offsetX;
        final double offY = this.offsetY;
        final double offZ = this.offsetZ;

        // note: if not empty, then there is one full AABB so no bounds checks are needed on the minFull/maxFull indices
        cached = new AABB(
                coordsX[shapeData.minFullX()] + offX,
                coordsY[shapeData.minFullY()] + offY,
                coordsZ[shapeData.minFullZ()] + offZ,

                coordsX[shapeData.maxFullX()] + offX,
                coordsY[shapeData.maxFullY()] + offY,
                coordsZ[shapeData.maxFullZ()] + offZ
        );

        if (offX == 0.0 && offY == 0.0 && offZ == 0.0) {
            cached = VoxelShapeInternPool.internAABB(cached);
        }

        this.cachedBounds = cached;
        return cached;
    }

    /**
     * @reason Reduce indirection from axis
     * @author Spottedleaf
     */
    @Overwrite
    public double min(final Direction.Axis axis) {
        final CachedShapeData shapeData = this.cachedShapeData;
        switch (axis) {
            case X: {
                final int idx = shapeData.minFullX();
                return idx >= shapeData.sizeX() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesX[idx] + this.offsetX);
            }
            case Y: {
                final int idx = shapeData.minFullY();
                return idx >= shapeData.sizeY() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesY[idx] + this.offsetY);
            }
            case Z: {
                final int idx = shapeData.minFullZ();
                return idx >= shapeData.sizeZ() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesZ[idx] + this.offsetZ);
            }
            default: {
                // should never get here
                return Double.POSITIVE_INFINITY;
            }
        }
    }

    /**
     * @reason Reduce indirection from axis
     * @author Spottedleaf
     */
    @Overwrite
    public double max(final Direction.Axis axis) {
        final CachedShapeData shapeData = this.cachedShapeData;
        switch (axis) {
            case X: {
                final int idx = shapeData.maxFullX();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesX[idx] + this.offsetX);
            }
            case Y: {
                final int idx = shapeData.maxFullY();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesY[idx] + this.offsetY);
            }
            case Z: {
                final int idx = shapeData.maxFullZ();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesZ[idx] + this.offsetZ);
            }
            default: {
                // should never get here
                return Double.NEGATIVE_INFINITY;
            }
        }
    }


    /**
     * @reason Optimise merge strategy to increase the number of simple joins, and additionally forward the toAabbs cache
     * to result
     * @author Spottedleaf
     */
    @Overwrite
    public VoxelShape optimize() {
        if (this.moonrise$isEmptyCached()) {
            return Shapes.empty();
        }

        if (this.moonrise$getSingleAABBRepresentation() != null) {
            // note: the isFullBlock() is fuzzy, and Shapes.create() is also fuzzy which would return block()
            return this.moonrise$isFullBlock() ? Shapes.block() : (VoxelShape)(Object)this;
        }

        final List<AABB> aabbs = this.toAabbs();

        if (aabbs.isEmpty()) {
            // We are a SliceShape, which does not properly fill isEmpty for every case
            return Shapes.empty();
        }

        if (aabbs.size() == 1) {
            final AABB singleAABB = aabbs.get(0);
            final VoxelShape ret = Shapes.create(singleAABB);

            // forward AABB cache
            if (((VoxelShapeMixin)(Object)ret).cachedToAABBs == null) {
                ((VoxelShapeMixin)(Object)ret).cachedToAABBs = this.cachedToAABBs;
            }

            return ret;
        } else {
            // reduce complexity of joins by splitting the merges (old complexity: n^2, new: nlogn)

            // set up flat array so that this merge is done in-place
            final VoxelShape[] tmp = new VoxelShape[aabbs.size()];

            // initialise as unmerged
            for (int i = 0, len = aabbs.size(); i < len; ++i) {
                tmp[i] = Shapes.create(aabbs.get(i));
            }

            int size = aabbs.size();
            while (size > 1) {
                int newSize = 0;
                for (int i = 0; i < size; i += 2) {
                    final int next = i + 1;
                    if (next >= size) {
                        // nothing to merge with, so leave it for next iteration
                        tmp[newSize++] = tmp[i];
                        break;
                    } else {
                        // merge with adjacent
                        final VoxelShape first = tmp[i];
                        final VoxelShape second = tmp[next];

                        tmp[newSize++] = Shapes.joinUnoptimized(first, second, BooleanOp.OR);
                    }
                }
                size = newSize;
            }

            final VoxelShape ret = tmp[0];

            // forward AABB cache
            if (((VoxelShapeMixin)(Object)ret).cachedToAABBs == null) {
                ((VoxelShapeMixin)(Object)ret).cachedToAABBs = this.cachedToAABBs;
            }

            return ret;
        }
    }

    /**
     * @reason Use AABBs cache
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<Vec3> closestPointTo(final Vec3 point) {
        if (this.moonrise$isEmptyCached()) {
            return Optional.empty();
        }

        Vec3 ret = null;
        double retDistance = Double.MAX_VALUE;

        final List<AABB> aabbs = this.toAabbs();
        for (int i = 0, len = aabbs.size(); i < len; ++i) {
            final AABB aabb = aabbs.get(i);
            final double x = Mth.clamp(point.x, aabb.minX, aabb.maxX);
            final double y = Mth.clamp(point.y, aabb.minY, aabb.maxY);
            final double z = Mth.clamp(point.z, aabb.minZ, aabb.maxZ);

            double dist = point.distanceToSqr(x, y, z);
            if (dist < retDistance) {
                ret = new Vec3(x, y, z);
                retDistance = dist;
            }
        }

        return Optional.ofNullable(ret);
    }
}
