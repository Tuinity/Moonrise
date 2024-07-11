package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.FlatBitsetUtil;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData;
import ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache;
import com.google.common.math.DoubleMath;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.Util;
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
import net.minecraft.world.phys.shapes.SliceShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Mixin(VoxelShape.class)
public abstract class VoxelShapeMixin implements CollisionVoxelShape {

    @Shadow
    public abstract DoubleList getCoords(final Direction.Axis axis);

    @Shadow
    @Final
    public DiscreteVoxelShape shape;

    @Shadow
    public abstract void forAllBoxes(final Shapes.DoubleLineConsumer doubleLineConsumer);

    @Unique
    private double offsetX;
    @Unique
    private double offsetY;
    @Unique
    private double offsetZ;
    @Unique
    private AABB singleAABBRepresentation;
    @Unique
    private double[] rootCoordinatesX;
    @Unique
    private double[] rootCoordinatesY;
    @Unique
    private double[] rootCoordinatesZ;

    @Unique
    private CachedShapeData cachedShapeData;
    @Unique
    private boolean isEmpty;

    @Unique
    private CachedToAABBs cachedToAABBs;
    @Unique
    private AABB cachedBounds;

    @Unique
    private Boolean isFullBlock;

    @Unique
    private Boolean occludesFullBlock;

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
        return this.singleAABBRepresentation;
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
            if (raw.length == expected) {
                return raw;
            } else {
                return Arrays.copyOf(raw, expected);
            }
        } else {
            return list.toDoubleArray();
        }
    }

    @Override
    public final void moonrise$initCache() {
        this.cachedShapeData = ((CollisionDiscreteVoxelShape)this.shape).moonrise$getOrCreateCachedShapeData();
        this.isEmpty = this.cachedShapeData.isEmpty();

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
            this.singleAABBRepresentation = new AABB(
                    this.rootCoordinatesX[0] + this.offsetX, this.rootCoordinatesY[0] + this.offsetY, this.rootCoordinatesZ[0] + this.offsetZ,
                    this.rootCoordinatesX[1] + this.offsetX, this.rootCoordinatesY[1] + this.offsetY, this.rootCoordinatesZ[1] + this.offsetZ
            );
            this.cachedBounds = this.singleAABBRepresentation;
        }
    }

    @Override
    public final CachedShapeData moonrise$getCachedVoxelData() {
        return this.cachedShapeData;
    }

    @Unique
    private VoxelShape[] faceShapeClampedCache;

    @Override
    public final VoxelShape moonrise$getFaceShapeClamped(final Direction direction) {
        if (this.isEmpty) {
            return (VoxelShape)(Object)this;
        }
        if ((VoxelShape)(Object)this == Shapes.block()) {
            return (VoxelShape)(Object)this;
        }

        VoxelShape[] cache = this.faceShapeClampedCache;
        if (cache != null) {
            final VoxelShape ret = cache[direction.ordinal()];
            if (ret != null) {
                return ret;
            }
        }


        if (cache == null) {
            this.faceShapeClampedCache = cache = new VoxelShape[6];
        }

        final Direction.Axis axis = direction.getAxis();

        final VoxelShape ret;

        if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            if (DoubleMath.fuzzyEquals(this.max(axis), 1.0, CollisionUtil.COLLISION_EPSILON)) {
                ret = tryForceBlock(new SliceShape((VoxelShape)(Object)this, axis, this.shape.getSize(axis) - 1));
            } else {
                ret = Shapes.empty();
            }
        } else {
            if (DoubleMath.fuzzyEquals(this.min(axis), 0.0, CollisionUtil.COLLISION_EPSILON)) {
                ret = tryForceBlock(new SliceShape((VoxelShape)(Object)this, axis, 0));
            } else {
                ret = Shapes.empty();
            }
        }

        cache[direction.ordinal()] = ret;

        return ret;
    }

    @Unique
    private static VoxelShape tryForceBlock(final VoxelShape other) {
        if (other == Shapes.block()) {
            return other;
        }

        final AABB otherAABB = ((CollisionVoxelShape)other).moonrise$getSingleAABBRepresentation();
        if (otherAABB == null) {
            return other;
        }

        if (((CollisionVoxelShape)Shapes.block()).moonrise$getSingleAABBRepresentation().equals(otherAABB)) {
            return Shapes.block();
        }

        return other;
    }

    @Unique
    private boolean computeOccludesFullBlock() {
        if (this.isEmpty) {
            this.occludesFullBlock = Boolean.FALSE;
            return false;
        }

        if (this.moonrise$isFullBlock()) {
            this.occludesFullBlock = Boolean.TRUE;
            return true;
        }

        final AABB singleAABB = this.singleAABBRepresentation;
        if (singleAABB != null) {
            // check if the bounding box encloses the full cube
            final boolean ret =
                    (singleAABB.minY <= CollisionUtil.COLLISION_EPSILON && singleAABB.maxY >= (1 - CollisionUtil.COLLISION_EPSILON)) &&
                    (singleAABB.minX <= CollisionUtil.COLLISION_EPSILON && singleAABB.maxX >= (1 - CollisionUtil.COLLISION_EPSILON)) &&
                    (singleAABB.minZ <= CollisionUtil.COLLISION_EPSILON && singleAABB.maxZ >= (1 - CollisionUtil.COLLISION_EPSILON));
            this.occludesFullBlock = Boolean.valueOf(ret);
            return ret;
        }

        final boolean ret = !Shapes.joinIsNotEmpty(Shapes.block(), ((VoxelShape)(Object)this), BooleanOp.ONLY_FIRST);
        this.occludesFullBlock = Boolean.valueOf(ret);
        return ret;
    }

    @Override
    public final boolean moonrise$occludesFullBlock() {
        final Boolean ret = this.occludesFullBlock;
        if (ret != null) {
            return ret.booleanValue();
        }

        return this.computeOccludesFullBlock();
    }

    @Override
    public final boolean moonrise$occludesFullBlockIfCached() {
        final Boolean ret = this.occludesFullBlock;
        return ret != null ? ret.booleanValue() : false;
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

        if (this.isEmpty) {
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
        return this.isEmpty;
    }

    /**
     * @author Spottedleaf
     * @reason Route to optimized collision method
     */
    @Overwrite
    public double collide(final Direction.Axis axis, final AABB source, final double source_move) {
        if (this.isEmpty) {
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
    private static DoubleList offsetList(final DoubleList src, final double by) {
        if (src instanceof OffsetDoubleList offsetDoubleList) {
            return new OffsetDoubleList(offsetDoubleList.delegate, by + offsetDoubleList.offset);
        }
        return new OffsetDoubleList(src, by);
    }

    /**
     * @author Spottedleaf
     * @reason Do not nest offset double lists
     */
    @Overwrite
    public VoxelShape move(final double x, final double y, final double z) {
        if (this.isEmpty) {
            return Shapes.empty();
        }

        final ArrayVoxelShape ret = new ArrayVoxelShape(
                this.shape,
                offsetList(this.getCoords(Direction.Axis.X), x),
                offsetList(this.getCoords(Direction.Axis.Y), y),
                offsetList(this.getCoords(Direction.Axis.Z), z)
        );

        final CachedToAABBs cachedToAABBs = this.cachedToAABBs;
        if (cachedToAABBs != null) {
            ((VoxelShapeMixin)(Object)ret).cachedToAABBs = CachedToAABBs.offset(cachedToAABBs, x, y, z);
        }

        return ret;
    }

    @Unique
    private List<AABB> toAabbsUncached() {
        final List<AABB> ret = new ArrayList<>();
        if (this.singleAABBRepresentation != null) {
            ret.add(this.singleAABBRepresentation);
        } else {
            final double[] coordsX = this.rootCoordinatesX;
            final double[] coordsY = this.rootCoordinatesY;
            final double[] coordsZ = this.rootCoordinatesZ;

            final double offX = this.offsetX;
            final double offY = this.offsetY;
            final double offZ = this.offsetZ;

            this.shape.forAllBoxes((final int minX, final int minY, final int minZ,
                                    final int maxX, final int maxY, final int maxZ) -> {
                ret.add(new AABB(
                        coordsX[minX] + offX,
                        coordsY[minY] + offY,
                        coordsZ[minZ] + offZ,


                        coordsX[maxX] + offX,
                        coordsY[maxY] + offY,
                        coordsZ[maxZ] + offZ
                ));
            }, true);
        }

        // cache result
        this.cachedToAABBs = new CachedToAABBs(ret, false, 0.0, 0.0, 0.0);

        return ret;
    }

    /**
     * @author Spottedleaf
     * @reason Cache toAABBs result
     */
    @Overwrite
    public List<AABB> toAabbs() {
        CachedToAABBs cachedToAABBs = this.cachedToAABBs;
        if (cachedToAABBs != null) {
            if (!cachedToAABBs.isOffset()) {
                return cachedToAABBs.aabbs();
            }

            // all we need to do is offset the cache
            cachedToAABBs = cachedToAABBs.removeOffset();
            // update cache
            this.cachedToAABBs = cachedToAABBs;

            return cachedToAABBs.aabbs();
        }

        // make new cache
        return this.toAabbsUncached();
    }

    @Unique
    private boolean computeFullBlock() {
        Boolean ret;
        if (this.isEmpty) {
            ret = Boolean.FALSE;
        } else if ((VoxelShape)(Object)this == Shapes.block()) {
            ret = Boolean.TRUE;
        } else {
            final AABB singleAABB = this.singleAABBRepresentation;
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

                    ret = Boolean.TRUE;

                    check_full:
                    for (int x = sMinX; x < sMaxX; ++x) {
                        for (int y = sMinY; y < sMaxY; ++y) {
                            final int baseIndex = y*sizeZ + x*(sizeZ*sizeY);
                            if (!FlatBitsetUtil.isRangeSet(bitset, baseIndex + sMinZ, baseIndex + sMaxZ)) {
                                ret = Boolean.FALSE;
                                break check_full;
                            }
                        }
                    }
                } else {
                    ret = Boolean.FALSE;
                }
            } else {
                ret = Boolean.valueOf(
                        Math.abs(singleAABB.minX) <= CollisionUtil.COLLISION_EPSILON &&
                           Math.abs(singleAABB.minY) <= CollisionUtil.COLLISION_EPSILON &&
                           Math.abs(singleAABB.minZ) <= CollisionUtil.COLLISION_EPSILON &&

                           Math.abs(1.0 - singleAABB.maxX) <= CollisionUtil.COLLISION_EPSILON &&
                           Math.abs(1.0 - singleAABB.maxY) <= CollisionUtil.COLLISION_EPSILON &&
                           Math.abs(1.0 - singleAABB.maxZ) <= CollisionUtil.COLLISION_EPSILON
                );
            }
        }

        this.isFullBlock = ret;

        return ret.booleanValue();
    }

    @Override
    public final boolean moonrise$isFullBlock() {
        final Boolean ret = this.isFullBlock;

        if (ret != null) {
            return ret.booleanValue();
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
        if (this.isEmpty) {
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

        final AABB singleAABB = this.singleAABBRepresentation;
        if (singleAABB != null) {
            if (singleAABB.contains(fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
                return new BlockHitResult(fromBehind, Direction.getNearest(directionOpposite.x, directionOpposite.y, directionOpposite.z).getOpposite(), offset, true);
            }
            return clip(singleAABB, from, to, offset);
        }

        if (CollisionUtil.strictlyContains((VoxelShape)(Object)this, fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
            return new BlockHitResult(fromBehind, Direction.getNearest(directionOpposite.x, directionOpposite.y, directionOpposite.z).getOpposite(), offset, true);
        }

        return AABB.clip(((VoxelShape)(Object)this).toAabbs(), from, to, offset);
    }

    /**
     * @reason Cache bounds
     * @author Spottedleaf
     */
    @Overwrite
    public AABB bounds() {
        if (this.isEmpty) {
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
        if (this.isEmpty) {
            return Shapes.empty();
        }

        if (this.singleAABBRepresentation != null) {
            // note: the isFullBlock() is fuzzy, and Shapes.create() is also fuzzy which would return block()
            return this.moonrise$isFullBlock() ? Shapes.block() : (VoxelShape)(Object)this;
        }

        final List<AABB> aabbs = this.toAabbs();

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
        if (this.isEmpty) {
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
