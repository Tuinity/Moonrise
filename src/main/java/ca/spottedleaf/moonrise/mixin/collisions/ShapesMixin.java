package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteCubeMerger;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.IndexMerger;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Arrays;
import java.util.function.Supplier;

@Mixin(Shapes.class)
public abstract class ShapesMixin {

    @Shadow
    protected static int findBits(double d, double e) {
        return 0;
    }

    @Shadow
    @Final
    private static VoxelShape BLOCK;

    @Shadow
    @Final
    private static VoxelShape EMPTY;

    @Shadow
    protected static IndexMerger createIndexMerger(int i, DoubleList doubleList, DoubleList doubleList2, boolean bl, boolean bl2) {
        return null;
    }

    @Unique
    private static final boolean DEBUG_SHAPE_MERGING = false;

    /**
     * Collisions are optimized for ArrayVoxelShape, so we should use that instead.
     */
    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/Util;make(Ljava/util/function/Supplier;)Ljava/lang/Object;"
            )
    )
    private static Object forceArrayVoxelShape(final Supplier<VoxelShape> supplier) {
        final DiscreteVoxelShape shape = new BitSetDiscreteVoxelShape(1, 1, 1);
        shape.fill(0, 0, 0);

        return new ArrayVoxelShape(
                shape,
                CollisionUtil.ZERO_ONE, CollisionUtil.ZERO_ONE, CollisionUtil.ZERO_ONE
        );
    }

    @Unique
    private static final DoubleArrayList[] PARTS_BY_BITS = new DoubleArrayList[] {
            DoubleArrayList.wrap(generateCubeParts(1 << 0)),
            DoubleArrayList.wrap(generateCubeParts(1 << 1)),
            DoubleArrayList.wrap(generateCubeParts(1 << 2)),
            DoubleArrayList.wrap(generateCubeParts(1 << 3))
    };

    @Unique
    private static double[] generateCubeParts(final int parts) {
        // note: parts is a power of two, so we do not need to worry about loss of precision here
        // note: parts is from [2^0, 2^3]
        final double inc = 1.0 / (double)parts;

        final double[] ret = new double[parts + 1];
        double val = 0.0;
        for (int i = 0; i <= parts; ++i) {
            ret[i] = val;
            val += inc;
        }

        return ret;
    }

    /**
     * @reason Avoid creating CubeVoxelShape instances
     * @author Spottedleaf
     */
    @Overwrite
    public static VoxelShape create(final double minX, final double minY, final double minZ, final double maxX, final double maxY, final double maxZ) {
        if (!(maxX - minX < 1.0E-7) && !(maxY - minY < 1.0E-7) && !(maxZ - minZ < 1.0E-7)) {
            final int bitsX = findBits(minX, maxX);
            final int bitsY = findBits(minY, maxY);
            final int bitsZ = findBits(minZ, maxZ);
            if (bitsX >= 0 && bitsY >= 0 && bitsZ >= 0) {
                if (bitsX == 0 && bitsY == 0 && bitsZ == 0) {
                    return BLOCK;
                } else {
                    final int sizeX = 1 << bitsX;
                    final int sizeY = 1 << bitsY;
                    final int sizeZ = 1 << bitsZ;
                    final BitSetDiscreteVoxelShape shape = BitSetDiscreteVoxelShape.withFilledBounds(
                            sizeX, sizeY, sizeZ,
                            (int)Math.round(minX * (double)sizeX), (int)Math.round(minY * (double)sizeY), (int)Math.round(minZ * (double)sizeZ),
                            (int)Math.round(maxX * (double)sizeX), (int)Math.round(maxY * (double)sizeY), (int)Math.round(maxZ * (double)sizeZ)
                    );
                    return new ArrayVoxelShape(
                            shape,
                            PARTS_BY_BITS[bitsX],
                            PARTS_BY_BITS[bitsY],
                            PARTS_BY_BITS[bitsZ]
                    );
                }
            } else {
                return new ArrayVoxelShape(
                        BLOCK.shape,
                        minX == 0.0 && maxX == 1.0 ? CollisionUtil.ZERO_ONE : DoubleArrayList.wrap(new double[] { minX, maxX }),
                        minY == 0.0 && maxY == 1.0 ? CollisionUtil.ZERO_ONE : DoubleArrayList.wrap(new double[] { minY, maxY }),
                        minZ == 0.0 && maxZ == 1.0 ? CollisionUtil.ZERO_ONE : DoubleArrayList.wrap(new double[] { minZ, maxZ })
                );
            }
        } else {
            return EMPTY;
        }
    }

    /**
     * @reason Stop using streams
     * @author Spottedleaf
     */
    @Overwrite
    public static VoxelShape or(final VoxelShape shape, final VoxelShape... others) {
        int size = others.length;
        if (size == 0) {
            return shape;
        }

        // reduce complexity of joins by splitting the merges

        // add extra slot for first shape
        ++size;
        final VoxelShape[] tmp = Arrays.copyOf(others, size);
        // insert first shape
        tmp[size - 1] = shape;

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

                    tmp[newSize++] = Shapes.or(first, second);
                }
            }
            size = newSize;
        }

        return tmp[0];
    }

    @Unique
    private static VoxelShape joinUnoptimizedVanilla(final VoxelShape voxelShape, final VoxelShape voxelShape2, final BooleanOp booleanOp) {
        if (booleanOp.apply(false, false)) {
            throw (IllegalArgumentException) Util.pauseInIde(new IllegalArgumentException());
        } else if (voxelShape == voxelShape2) {
            return booleanOp.apply(true, true) ? voxelShape : EMPTY;
        } else {
            boolean bl = booleanOp.apply(true, false);
            boolean bl2 = booleanOp.apply(false, true);
            if (voxelShape.isEmpty()) {
                return bl2 ? voxelShape2 : EMPTY;
            } else if (voxelShape2.isEmpty()) {
                return bl ? voxelShape : EMPTY;
            } else {
                IndexMerger indexMerger = createIndexMerger(1, voxelShape.getCoords(Direction.Axis.X), voxelShape2.getCoords(Direction.Axis.X), bl, bl2);
                IndexMerger indexMerger2 = createIndexMerger(indexMerger.size() - 1, voxelShape.getCoords(Direction.Axis.Y), voxelShape2.getCoords(Direction.Axis.Y), bl, bl2);
                IndexMerger indexMerger3 = createIndexMerger((indexMerger.size() - 1) * (indexMerger2.size() - 1), voxelShape.getCoords(Direction.Axis.Z), voxelShape2.getCoords(Direction.Axis.Z), bl, bl2);
                BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = BitSetDiscreteVoxelShape.join(voxelShape.shape, voxelShape2.shape, indexMerger, indexMerger2, indexMerger3, booleanOp);
                return (VoxelShape) (indexMerger instanceof DiscreteCubeMerger && indexMerger2 instanceof DiscreteCubeMerger && indexMerger3 instanceof DiscreteCubeMerger ? new CubeVoxelShape(bitSetDiscreteVoxelShape) : new ArrayVoxelShape(bitSetDiscreteVoxelShape, indexMerger.getList(), indexMerger2.getList(), indexMerger3.getList()));
            }
        }
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Overwrite
    public static VoxelShape join(final VoxelShape first, final VoxelShape second, final BooleanOp mergeFunction) {
        final VoxelShape ret = CollisionUtil.joinOptimized(first, second, mergeFunction);
        if (DEBUG_SHAPE_MERGING) {
            final VoxelShape vanilla = joinUnoptimizedVanilla(first, second, mergeFunction);
            if (!CollisionUtil.equals(ret, vanilla.optimize())) {
                CollisionUtil.joinUnoptimized(first, second, mergeFunction);
                joinUnoptimizedVanilla(first, second, mergeFunction);
                throw new IllegalStateException("TRAP");
            }
        }
        return ret;
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Overwrite
    public static VoxelShape joinUnoptimized(final VoxelShape first, final VoxelShape second, final BooleanOp mergeFunction) {
        final VoxelShape ret = CollisionUtil.joinUnoptimized(first, second, mergeFunction);
        if (DEBUG_SHAPE_MERGING) {
            final VoxelShape vanilla = joinUnoptimizedVanilla(first, second, mergeFunction);
            if (!CollisionUtil.equals(ret, vanilla)) {
                CollisionUtil.joinUnoptimized(first, second, mergeFunction);
                joinUnoptimizedVanilla(first, second, mergeFunction);
                throw new IllegalStateException("TRAP");
            }
        }
        return ret;
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Overwrite
    public static boolean joinIsNotEmpty(final VoxelShape first, final VoxelShape second, final BooleanOp mergeFunction) {
        final boolean ret = CollisionUtil.isJoinNonEmpty(first, second, mergeFunction);
        if (DEBUG_SHAPE_MERGING) {
            if (ret != !joinUnoptimizedVanilla(first, second, mergeFunction).isEmpty()) {
                CollisionUtil.isJoinNonEmpty(first, second, mergeFunction);
                joinUnoptimizedVanilla(first, second, mergeFunction).isEmpty();
                throw new IllegalStateException("TRAP");
            }
        }
        return ret;
    }

    /**
     * @reason Route to use cache
     * @author Spottedleaf
     */
    @Overwrite
    public static VoxelShape getFaceShape(final VoxelShape shape, final Direction direction) {
        return ((CollisionVoxelShape)shape).moonrise$getFaceShapeClamped(direction);
    }

    @Unique
    private static boolean mergedMayOccludeBlock(final VoxelShape shape1, final VoxelShape shape2) {
        // if the combined bounds of the two shapes cannot occlude, then neither can the merged
        final AABB bounds1 = shape1.bounds();
        final AABB bounds2 = shape2.bounds();

        final double minX = Math.min(bounds1.minX, bounds2.minX);
        final double minY = Math.min(bounds1.minY, bounds2.minY);
        final double minZ = Math.min(bounds1.minZ, bounds2.minZ);

        final double maxX = Math.max(bounds1.maxX, bounds2.maxX);
        final double maxY = Math.max(bounds1.maxY, bounds2.maxY);
        final double maxZ = Math.max(bounds1.maxZ, bounds2.maxZ);

        return (minX <= CollisionUtil.COLLISION_EPSILON && maxX >= (1 - CollisionUtil.COLLISION_EPSILON)) &&
                (minY <= CollisionUtil.COLLISION_EPSILON && maxY >= (1 - CollisionUtil.COLLISION_EPSILON)) &&
                (minZ <= CollisionUtil.COLLISION_EPSILON && maxZ >= (1 - CollisionUtil.COLLISION_EPSILON));
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Overwrite
    public static boolean mergedFaceOccludes(final VoxelShape first, final VoxelShape second, final Direction direction) {
        // see if any of the shapes on their own occludes, only if cached
        if (((CollisionVoxelShape)first).moonrise$occludesFullBlockIfCached() || ((CollisionVoxelShape)second).moonrise$occludesFullBlockIfCached()) {
            return true;
        }

        if (first.isEmpty() & second.isEmpty()) {
            return false;
        }

        // we optimise getOpposite, so we can use it
        // secondly, use our cache to retrieve sliced shape
        final VoxelShape newFirst = ((CollisionVoxelShape)first).moonrise$getFaceShapeClamped(direction);
        final VoxelShape newSecond = ((CollisionVoxelShape)second).moonrise$getFaceShapeClamped(direction.getOpposite());

        // see if any of the shapes on their own occludes, only if cached
        if (((CollisionVoxelShape)newFirst).moonrise$occludesFullBlockIfCached() || ((CollisionVoxelShape)newSecond).moonrise$occludesFullBlockIfCached()) {
            return true;
        }

        final boolean firstEmpty = newFirst.isEmpty();
        final boolean secondEmpty = newSecond.isEmpty();

        if (firstEmpty & secondEmpty) {
            return false;
        }

        if (firstEmpty | secondEmpty) {
            return secondEmpty ? ((CollisionVoxelShape)newFirst).moonrise$occludesFullBlock() : ((CollisionVoxelShape)newSecond).moonrise$occludesFullBlock();
        }

        if (newFirst == newSecond) {
            return ((CollisionVoxelShape)newFirst).moonrise$occludesFullBlock();
        }

        return mergedMayOccludeBlock(newFirst, newSecond) && ((CollisionVoxelShape)((CollisionVoxelShape)newFirst).moonrise$orUnoptimized(newSecond)).moonrise$occludesFullBlock();
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Overwrite
    public static boolean blockOccudes(final VoxelShape first, final VoxelShape second, final Direction direction) {
        final boolean firstBlock = first == BLOCK;
        final boolean secondBlock = second == BLOCK;

        if (firstBlock & secondBlock) {
            return true;
        }

        if (first.isEmpty() | second.isEmpty()) {
            return false;
        }

        // we optimise getOpposite, so we can use it
        // secondly, use our cache to retrieve sliced shape
        final VoxelShape newFirst = ((CollisionVoxelShape)first).moonrise$getFaceShapeClamped(direction);
        if (newFirst.isEmpty()) {
            return false;
        }
        final VoxelShape newSecond = ((CollisionVoxelShape)second).moonrise$getFaceShapeClamped(direction.getOpposite());
        if (newSecond.isEmpty()) {
            return false;
        }

        return !joinIsNotEmpty(newFirst, newSecond, BooleanOp.ONLY_FIRST);
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Overwrite
    public static boolean faceShapeOccludes(final VoxelShape shape1, final VoxelShape shape2) {
        if (((CollisionVoxelShape)shape1).moonrise$occludesFullBlockIfCached() || ((CollisionVoxelShape)shape2).moonrise$occludesFullBlockIfCached()) {
            return true;
        }

        final boolean s1Empty = shape1.isEmpty();
        final boolean s2Empty = shape2.isEmpty();
        if (s1Empty & s2Empty) {
            return false;
        }

        if (s1Empty | s2Empty) {
            return s2Empty ? ((CollisionVoxelShape)shape1).moonrise$occludesFullBlock() : ((CollisionVoxelShape)shape2).moonrise$occludesFullBlock();
        }

        if (shape1 == shape2) {
            return ((CollisionVoxelShape)shape1).moonrise$occludesFullBlock();
        }

        return mergedMayOccludeBlock(shape1, shape2) && ((CollisionVoxelShape)((CollisionVoxelShape)shape1).moonrise$orUnoptimized(shape2)).moonrise$occludesFullBlock();
    }
}
