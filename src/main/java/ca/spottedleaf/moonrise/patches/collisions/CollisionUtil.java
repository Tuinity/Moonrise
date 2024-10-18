package ca.spottedleaf.moonrise.patches.collisions;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemEntityGetter;
import ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState;
import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.OffsetDoubleList;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.SliceShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class CollisionUtil {

    public static final double COLLISION_EPSILON = 1.0E-7;
    public static final DoubleArrayList ZERO_ONE = DoubleArrayList.wrap(new double[] { 0.0, 1.0 });

    public static boolean isSpecialCollidingBlock(final net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase block) {
        return block.hasLargeCollisionShape() || block.getBlock() == Blocks.MOVING_PISTON;
    }

    public static boolean isEmpty(final AABB aabb) {
        return (aabb.maxX - aabb.minX) < COLLISION_EPSILON || (aabb.maxY - aabb.minY) < COLLISION_EPSILON || (aabb.maxZ - aabb.minZ) < COLLISION_EPSILON;
    }

    public static boolean isEmpty(final double minX, final double minY, final double minZ,
                                  final double maxX, final double maxY, final double maxZ) {
        return (maxX - minX) < COLLISION_EPSILON || (maxY - minY) < COLLISION_EPSILON || (maxZ - minZ) < COLLISION_EPSILON;
    }

    public static AABB getBoxForChunk(final int chunkX, final int chunkZ) {
        double x = (double)(chunkX << 4);
        double z = (double)(chunkZ << 4);
        // use a bounding box bigger than the chunk to prevent entities from entering it on move
        return new AABB(x - 3*COLLISION_EPSILON, Double.NEGATIVE_INFINITY, z - 3*COLLISION_EPSILON,
            x + (16.0 + 3*COLLISION_EPSILON), Double.POSITIVE_INFINITY, z + (16.0 + 3*COLLISION_EPSILON));
    }

    /*
      A couple of rules for VoxelShape collisions:
      Two shapes only intersect if they are actually more than EPSILON units into each other. This also applies to movement
      checks.
      If the two shapes strictly collide, then the return value of a collide call will return a value in the opposite
      direction of the source move. However, this value will not be greater in magnitude than EPSILON. Collision code
      will automatically round it to 0.
     */

    public static boolean voxelShapeIntersect(final double minX1, final double minY1, final double minZ1, final double maxX1,
                                              final double maxY1, final double maxZ1, final double minX2, final double minY2,
                                              final double minZ2, final double maxX2, final double maxY2, final double maxZ2) {
        return (minX1 - maxX2) < -COLLISION_EPSILON && (maxX1 - minX2) > COLLISION_EPSILON &&
               (minY1 - maxY2) < -COLLISION_EPSILON && (maxY1 - minY2) > COLLISION_EPSILON &&
               (minZ1 - maxZ2) < -COLLISION_EPSILON && (maxZ1 - minZ2) > COLLISION_EPSILON;
    }

    public static boolean voxelShapeIntersect(final AABB box, final double minX, final double minY, final double minZ,
                                              final double maxX, final double maxY, final double maxZ) {
        return (box.minX - maxX) < -COLLISION_EPSILON && (box.maxX - minX) > COLLISION_EPSILON &&
               (box.minY - maxY) < -COLLISION_EPSILON && (box.maxY - minY) > COLLISION_EPSILON &&
               (box.minZ - maxZ) < -COLLISION_EPSILON && (box.maxZ - minZ) > COLLISION_EPSILON;
    }

    public static boolean voxelShapeIntersect(final AABB box1, final AABB box2) {
        return (box1.minX - box2.maxX) < -COLLISION_EPSILON && (box1.maxX - box2.minX) > COLLISION_EPSILON &&
               (box1.minY - box2.maxY) < -COLLISION_EPSILON && (box1.maxY - box2.minY) > COLLISION_EPSILON &&
               (box1.minZ - box2.maxZ) < -COLLISION_EPSILON && (box1.maxZ - box2.minZ) > COLLISION_EPSILON;
    }

    // assume !isEmpty(target) && abs(source_move) >= COLLISION_EPSILON
    public static double collideX(final AABB target, final AABB source, final double source_move) {
        if ((source.minY - target.maxY) < -COLLISION_EPSILON && (source.maxY - target.minY) > COLLISION_EPSILON &&
            (source.minZ - target.maxZ) < -COLLISION_EPSILON && (source.maxZ - target.minZ) > COLLISION_EPSILON) {
            if (source_move >= 0.0) {
                final double max_move = target.minX - source.maxX; // < 0.0 if no strict collision
                if (max_move < -COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.min(max_move, source_move);
            } else {
                final double max_move = target.maxX - source.minX; // > 0.0 if no strict collision
                if (max_move > COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.max(max_move, source_move);
            }
        }
        return source_move;
    }

    // assume !isEmpty(target) && abs(source_move) >= COLLISION_EPSILON
    public static double collideY(final AABB target, final AABB source, final double source_move) {
        if ((source.minX - target.maxX) < -COLLISION_EPSILON && (source.maxX - target.minX) > COLLISION_EPSILON &&
            (source.minZ - target.maxZ) < -COLLISION_EPSILON && (source.maxZ - target.minZ) > COLLISION_EPSILON) {
            if (source_move >= 0.0) {
                final double max_move = target.minY - source.maxY; // < 0.0 if no strict collision
                if (max_move < -COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.min(max_move, source_move);
            } else {
                final double max_move = target.maxY - source.minY; // > 0.0 if no strict collision
                if (max_move > COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.max(max_move, source_move);
            }
        }
        return source_move;
    }

    // assume !isEmpty(target) && abs(source_move) >= COLLISION_EPSILON
    public static double collideZ(final AABB target, final AABB source, final double source_move) {
        if ((source.minX - target.maxX) < -COLLISION_EPSILON && (source.maxX - target.minX) > COLLISION_EPSILON &&
            (source.minY - target.maxY) < -COLLISION_EPSILON && (source.maxY - target.minY) > COLLISION_EPSILON) {
            if (source_move >= 0.0) {
                final double max_move = target.minZ - source.maxZ; // < 0.0 if no strict collision
                if (max_move < -COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.min(max_move, source_move);
            } else {
                final double max_move = target.maxZ - source.minZ; // > 0.0 if no strict collision
                if (max_move > COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.max(max_move, source_move);
            }
        }
        return source_move;
    }

    // startIndex and endIndex inclusive
    // assumes indices are in range of array
    public static int findFloor(final double[] values, final double value, int startIndex, int endIndex) {
        Objects.checkFromToIndex(startIndex, endIndex + 1, values.length);
        do {
            final int middle = (startIndex + endIndex) >>> 1;
            final double middleVal = values[middle];

            if (value < middleVal) {
                endIndex = middle - 1;
            } else {
                startIndex = middle + 1;
            }
        } while (startIndex <= endIndex);

        return startIndex - 1;
    }

    private static VoxelShape sliceShapeVanilla(final VoxelShape src, final Direction.Axis axis,
                                                final int index) {
        return new SliceShape(src, axis, index);
    }

    private static DoubleList offsetList(final double[] src, final double by) {
        final DoubleArrayList wrap = DoubleArrayList.wrap(src);
        if (by == 0.0) {
            return wrap;
        }
        return new OffsetDoubleList(wrap, by);
    }

    private static VoxelShape sliceShapeOptimised(final VoxelShape src, final Direction.Axis axis,
                                                  final int index) {
        // assume index in range
        final double off_x = ((CollisionVoxelShape)src).moonrise$offsetX();
        final double off_y = ((CollisionVoxelShape)src).moonrise$offsetY();
        final double off_z = ((CollisionVoxelShape)src).moonrise$offsetZ();

        final double[] coords_x = ((CollisionVoxelShape)src).moonrise$rootCoordinatesX();
        final double[] coords_y = ((CollisionVoxelShape)src).moonrise$rootCoordinatesY();
        final double[] coords_z = ((CollisionVoxelShape)src).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data = ((CollisionVoxelShape)src).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        final long[] bitset = cached_shape_data.voxelSet();

        final DoubleList list_x;
        final DoubleList list_y;
        final DoubleList list_z;
        final int shape_sx;
        final int shape_ex;
        final int shape_sy;
        final int shape_ey;
        final int shape_sz;
        final int shape_ez;

        switch (axis) {
            case X: {
                // validate index
                if (index < 0 || index >= size_x) {
                    return Shapes.empty();
                }

                // test if input is already "sliced"
                if (coords_x.length == 2 && (coords_x[0] + off_x) == 0.0 && (coords_x[1] + off_x) == 1.0) {
                    return src;
                }

                // test if result would be full box
                if (coords_y.length == 2 && coords_z.length == 2 &&
                    (coords_y[0] + off_y) == 0.0 && (coords_y[1] + off_y) == 1.0 &&
                    (coords_z[0] + off_z) == 0.0 && (coords_z[1] + off_z) == 1.0) {
                    // note: size_y == size_z == 1
                    final int bitIdx = 0 + 0*size_z + index*(size_z*size_y);
                    return (bitset[bitIdx >>> 6] & (1L << bitIdx)) == 0L ? Shapes.empty() : Shapes.block();
                }

                list_x = ZERO_ONE;
                list_y = offsetList(coords_y, off_y);
                list_z = offsetList(coords_z, off_z);
                shape_sx = index;
                shape_ex = index + 1;
                shape_sy = 0;
                shape_ey = size_y;
                shape_sz = 0;
                shape_ez = size_z;

                break;
            }
            case Y: {
                // validate index
                if (index < 0 || index >= size_y) {
                    return Shapes.empty();
                }

                // test if input is already "sliced"
                if (coords_y.length == 2 && (coords_y[0] + off_y) == 0.0 && (coords_y[1] + off_y) == 1.0) {
                    return src;
                }

                // test if result would be full box
                if (coords_x.length == 2 && coords_z.length == 2 &&
                    (coords_x[0] + off_x) == 0.0 && (coords_x[1] + off_x) == 1.0 &&
                    (coords_z[0] + off_z) == 0.0 && (coords_z[1] + off_z) == 1.0) {
                    // note: size_x == size_z == 1
                    final int bitIdx = 0 + index*size_z + 0*(size_z*size_y);
                    return (bitset[bitIdx >>> 6] & (1L << bitIdx)) == 0L ? Shapes.empty() : Shapes.block();
                }

                list_x = offsetList(coords_x, off_x);
                list_y = ZERO_ONE;
                list_z = offsetList(coords_z, off_z);
                shape_sx = 0;
                shape_ex = size_x;
                shape_sy = index;
                shape_ey = index + 1;
                shape_sz = 0;
                shape_ez = size_z;

                break;
            }
            case Z: {
                // validate index
                if (index < 0 || index >= size_z) {
                    return Shapes.empty();
                }

                // test if input is already "sliced"
                if (coords_z.length == 2 && (coords_z[0] + off_z) == 0.0 && (coords_z[1] + off_z) == 1.0) {
                    return src;
                }

                // test if result would be full box
                if (coords_x.length == 2 && coords_y.length == 2 &&
                    (coords_x[0] + off_x) == 0.0 && (coords_x[1] + off_x) == 1.0 &&
                    (coords_y[0] + off_y) == 0.0 && (coords_y[1] + off_y) == 1.0) {
                    // note: size_x == size_y == 1
                    final int bitIdx = index + 0*size_z + 0*(size_z*size_y);
                    return (bitset[bitIdx >>> 6] & (1L << bitIdx)) == 0L ? Shapes.empty() : Shapes.block();
                }

                list_x = offsetList(coords_x, off_x);
                list_y = offsetList(coords_y, off_y);
                list_z = ZERO_ONE;
                shape_sx = 0;
                shape_ex = size_x;
                shape_sy = 0;
                shape_ey = size_y;
                shape_sz = index;
                shape_ez = index + 1;

                break;
            }
            default: {
                throw new IllegalStateException("Unknown axis: " + axis);
            }
        }

        final int local_len_x = shape_ex - shape_sx;
        final int local_len_y = shape_ey - shape_sy;
        final int local_len_z = shape_ez - shape_sz;

        final BitSetDiscreteVoxelShape shape = new BitSetDiscreteVoxelShape(local_len_x, local_len_y, local_len_z);

        final int bitset_mul_x = size_z*size_y;
        final int idx_off = shape_sz + shape_sy*size_z + shape_sx*bitset_mul_x;
        final int shape_mul_x = local_len_y*local_len_z;
        for (int x = 0; x < local_len_x; ++x) {
            boolean setX = false;
            for (int y = 0; y < local_len_y; ++y) {
                boolean setY = false;
                for (int z = 0; z < local_len_z; ++z) {
                    final int unslicedIdx = idx_off + z + y*size_z + x*bitset_mul_x;
                    if ((bitset[unslicedIdx >>> 6] & (1L << unslicedIdx)) == 0L) {
                        continue;
                    }

                    setY = true;
                    setX = true;
                    shape.zMin = Math.min(shape.zMin, z);
                    shape.zMax = Math.max(shape.zMax, z + 1);

                    shape.storage.set(
                        z + y*local_len_z + x*shape_mul_x
                    );
                }

                if (setY) {
                    shape.yMin = Math.min(shape.yMin, y);
                    shape.yMax = Math.max(shape.yMax, y + 1);
                }
            }
            if (setX) {
                shape.xMin = Math.min(shape.xMin, x);
                shape.xMax = Math.max(shape.xMax, x + 1);
            }
        }

        return shape.isEmpty() ? Shapes.empty() : new ArrayVoxelShape(
            shape, list_x, list_y, list_z
        );
    }

    private static final boolean DEBUG_SLICE_SHAPE = false;

    public static VoxelShape sliceShape(final VoxelShape src, final Direction.Axis axis,
                                        final int index) {
        final VoxelShape ret = sliceShapeOptimised(src, axis, index);
        if (DEBUG_SLICE_SHAPE) {
            final VoxelShape vanilla = sliceShapeVanilla(src, axis, index);
            if (!equals(ret, vanilla)) {
                // special case: SliceShape is not empty when it should be!
                if (areAnyFull(ret.shape) || areAnyFull(vanilla.shape)) {
                    equals(ret, vanilla);
                    sliceShapeOptimised(src, axis, index);
                    throw new IllegalStateException("Slice shape mismatch");
                }
            }
        }

        return ret;
    }

    public static boolean voxelShapeIntersectNoEmpty(final VoxelShape voxel, final AABB aabb) {
        if (voxel.isEmpty()) {
            return false;
        }

        // note: this function assumes that for any i in coords that coord[i + 1] - coord[i] > COLLISION_EPSILON is true

        // offsets that should be applied to coords
        final double off_x = ((CollisionVoxelShape)voxel).moonrise$offsetX();
        final double off_y = ((CollisionVoxelShape)voxel).moonrise$offsetY();
        final double off_z = ((CollisionVoxelShape)voxel).moonrise$offsetZ();

        final double[] coords_x = ((CollisionVoxelShape)voxel).moonrise$rootCoordinatesX();
        final double[] coords_y = ((CollisionVoxelShape)voxel).moonrise$rootCoordinatesY();
        final double[] coords_z = ((CollisionVoxelShape)voxel).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data = ((CollisionVoxelShape)voxel).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        // note: voxel bitset with set index (x, y, z) indicates that
        //       an AABB(coords_x[x], coords_y[y], coords_z[z], coords_x[x + 1], coords_y[y + 1], coords_z[z + 1])
        //       is collidable. this is the fundamental principle of operation for the voxel collision operation

        // note: we should be offsetting coords, but we can also just subtract from source as well - which is
        //       a win in terms of ops / simplicity (see findFloor, allows us to not modify coords for that)
        // note: for intersection, one we find the floor of the min we can use that as the start index
        //       for the next check as source max >= source min
        // note: we can fast check intersection on the two other axis by seeing if the min index is >= size,
        //       as this implies that coords[coords.length - 1] < source min
        //       we can also fast check by seeing if max index is < 0, as this implies that coords[0] > source max

        final int floor_min_x = Math.max(
                0,
                findFloor(coords_x, (aabb.minX - off_x) + COLLISION_EPSILON, 0, size_x)
        );
        if (floor_min_x >= size_x) {
            // cannot intersect
            return false;
        }

        final int ceil_max_x = Math.min(
                size_x,
                findFloor(coords_x, (aabb.maxX - off_x) - COLLISION_EPSILON, floor_min_x, size_x) + 1
        );
        if (floor_min_x >= ceil_max_x) {
            // cannot intersect
            return false;
        }

        final int floor_min_y = Math.max(
                0,
                findFloor(coords_y, (aabb.minY - off_y) + COLLISION_EPSILON, 0, size_y)
        );
        if (floor_min_y >= size_y) {
            // cannot intersect
            return false;
        }

        final int ceil_max_y = Math.min(
                size_y,
                findFloor(coords_y, (aabb.maxY - off_y) - COLLISION_EPSILON, floor_min_y, size_y) + 1
        );
        if (floor_min_y >= ceil_max_y) {
            // cannot intersect
            return false;
        }

        final int floor_min_z = Math.max(
                0,
                findFloor(coords_z, (aabb.minZ - off_z) + COLLISION_EPSILON, 0, size_z)
        );
        if (floor_min_z >= size_z) {
            // cannot intersect
            return false;
        }

        final int ceil_max_z = Math.min(
                size_z,
                findFloor(coords_z, (aabb.maxZ - off_z) - COLLISION_EPSILON, floor_min_z, size_z) + 1
        );
        if (floor_min_z >= ceil_max_z) {
            // cannot intersect
            return false;
        }

        final long[] bitset = cached_shape_data.voxelSet();

        // check bitset to check if any shapes in range are full

        final int mul_x = size_y*size_z;
        for (int curr_x = floor_min_x; curr_x < ceil_max_x; ++curr_x) {
            for (int curr_y = floor_min_y; curr_y < ceil_max_y; ++curr_y) {
                for (int curr_z = floor_min_z; curr_z < ceil_max_z; ++curr_z) {
                    final int index = curr_z + curr_y*size_z + curr_x*mul_x;
                    // note: JLS states long shift operators ANDS shift by 63
                    if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // assume !target.isEmpty() && abs(source_move) >= COLLISION_EPSILON
    public static double collideX(final VoxelShape target, final AABB source, final double source_move) {
        final AABB single_aabb = ((CollisionVoxelShape)target).moonrise$getSingleAABBRepresentation();
        if (single_aabb != null) {
            return collideX(single_aabb, source, source_move);
        }
        // note: this function assumes that for any i in coords that coord[i + 1] - coord[i] > COLLISION_EPSILON is true

        // offsets that should be applied to coords
        final double off_x = ((CollisionVoxelShape)target).moonrise$offsetX();
        final double off_y = ((CollisionVoxelShape)target).moonrise$offsetY();
        final double off_z = ((CollisionVoxelShape)target).moonrise$offsetZ();

        final double[] coords_x = ((CollisionVoxelShape)target).moonrise$rootCoordinatesX();
        final double[] coords_y = ((CollisionVoxelShape)target).moonrise$rootCoordinatesY();
        final double[] coords_z = ((CollisionVoxelShape)target).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data = ((CollisionVoxelShape)target).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        // note: voxel bitset with set index (x, y, z) indicates that
        //       an AABB(coords_x[x], coords_y[y], coords_z[z], coords_x[x + 1], coords_y[y + 1], coords_z[z + 1])
        //       is collidable. this is the fundamental principle of operation for the voxel collision operation


        // note: we should be offsetting coords, but we can also just subtract from source as well - which is
        //       a win in terms of ops / simplicity (see findFloor, allows us to not modify coords for that)
        // note: for intersection, one we find the floor of the min we can use that as the start index
        //       for the next check as source max >= source min
        // note: we can fast check intersection on the two other axis by seeing if the min index is >= size,
        //       as this implies that coords[coords.length - 1] < source min
        //       we can also fast check by seeing if max index is < 0, as this implies that coords[0] > source max

        final int floor_min_y = Math.max(
                0,
                findFloor(coords_y, (source.minY - off_y) + COLLISION_EPSILON, 0, size_y)
        );
        if (floor_min_y >= size_y) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_y = Math.min(
                size_y,
                findFloor(coords_y, (source.maxY - off_y) - COLLISION_EPSILON, floor_min_y, size_y) + 1
        );
        if (floor_min_y >= ceil_max_y) {
            // cannot intersect
            return source_move;
        }

        final int floor_min_z = Math.max(
                0,
                findFloor(coords_z, (source.minZ - off_z) + COLLISION_EPSILON, 0, size_z)
        );
        if (floor_min_z >= size_z) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_z = Math.min(
                size_z,
                findFloor(coords_z, (source.maxZ - off_z) - COLLISION_EPSILON, floor_min_z, size_z) + 1
        );
        if (floor_min_z >= ceil_max_z) {
            // cannot intersect
            return source_move;
        }

        // index = z + y*size_z + x*(size_z*size_y)

        final long[] bitset = cached_shape_data.voxelSet();

        if (source_move > 0.0) {
            final double source_max = source.maxX - off_x;
            final int ceil_max_x = findFloor(
                    coords_x, source_max - COLLISION_EPSILON, 0, size_x
            ) + 1; // add one, we are not interested in (coords[i] + COLLISION_EPSILON) < max

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index size on the collision axis for forward movement

            final int mul_x = size_y*size_z;
            for (int curr_x = ceil_max_x; curr_x < size_x; ++curr_x) {
                double max_dist = coords_x[curr_x] - source_max;
                if (max_dist >= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max < source_move, as coords[curr + n] < coords[curr + n + 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist >= -COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.min(max_dist, source_move);
                }
                for (int curr_y = floor_min_y; curr_y < ceil_max_y; ++curr_y) {
                    for (int curr_z = floor_min_z; curr_z < ceil_max_z; ++curr_z) {
                        final int index = curr_z + curr_y*size_z + curr_x*mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        } else {
            final double source_min = source.minX - off_x;
            final int floor_min_x = findFloor(
                    coords_x, source_min + COLLISION_EPSILON, 0, size_x
            );

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index 0 on the collision axis for backwards movement

            // note: we offset the collision axis by - 1 for the voxel bitset index, but use + 1 for the
            //       coordinate index as the voxelset stores whether the shape is solid for [index, index + 1]
            //       thus, we need to use the voxel index i-1 if we want to check that the face at index i is solid
            final int mul_x = size_y*size_z;
            for (int curr_x = floor_min_x - 1; curr_x >= 0; --curr_x) {
                double max_dist = coords_x[curr_x + 1] - source_min;
                if (max_dist <= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max > source_move, as coords[curr + n] > coords[curr + n - 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is possibly bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist <= COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.max(max_dist, source_move);
                }
                for (int curr_y = floor_min_y; curr_y < ceil_max_y; ++curr_y) {
                    for (int curr_z = floor_min_z; curr_z < ceil_max_z; ++curr_z) {
                        final int index = curr_z + curr_y*size_z + curr_x*mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        }
    }

    public static double collideY(final VoxelShape target, final AABB source, final double source_move) {
        final AABB single_aabb = ((CollisionVoxelShape)target).moonrise$getSingleAABBRepresentation();
        if (single_aabb != null) {
            return collideY(single_aabb, source, source_move);
        }
        // note: this function assumes that for any i in coords that coord[i + 1] - coord[i] > COLLISION_EPSILON is true

        // offsets that should be applied to coords
        final double off_x = ((CollisionVoxelShape)target).moonrise$offsetX();
        final double off_y = ((CollisionVoxelShape)target).moonrise$offsetY();
        final double off_z = ((CollisionVoxelShape)target).moonrise$offsetZ();

        final double[] coords_x = ((CollisionVoxelShape)target).moonrise$rootCoordinatesX();
        final double[] coords_y = ((CollisionVoxelShape)target).moonrise$rootCoordinatesY();
        final double[] coords_z = ((CollisionVoxelShape)target).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data = ((CollisionVoxelShape)target).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        // note: voxel bitset with set index (x, y, z) indicates that
        //       an AABB(coords_x[x], coords_y[y], coords_z[z], coords_x[x + 1], coords_y[y + 1], coords_z[z + 1])
        //       is collidable. this is the fundamental principle of operation for the voxel collision operation


        // note: we should be offsetting coords, but we can also just subtract from source as well - which is
        //       a win in terms of ops / simplicity (see findFloor, allows us to not modify coords for that)
        // note: for intersection, one we find the floor of the min we can use that as the start index
        //       for the next check as source max >= source min
        // note: we can fast check intersection on the two other axis by seeing if the min index is >= size,
        //       as this implies that coords[coords.length - 1] < source min
        //       we can also fast check by seeing if max index is < 0, as this implies that coords[0] > source max

        final int floor_min_x = Math.max(
                0,
                findFloor(coords_x, (source.minX - off_x) + COLLISION_EPSILON, 0, size_x)
        );
        if (floor_min_x >= size_x) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_x = Math.min(
                size_x,
                findFloor(coords_x, (source.maxX - off_x) - COLLISION_EPSILON, floor_min_x, size_x) + 1
        );
        if (floor_min_x >= ceil_max_x) {
            // cannot intersect
            return source_move;
        }

        final int floor_min_z = Math.max(
                0,
                findFloor(coords_z, (source.minZ - off_z) + COLLISION_EPSILON, 0, size_z)
        );
        if (floor_min_z >= size_z) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_z = Math.min(
                size_z,
                findFloor(coords_z, (source.maxZ - off_z) - COLLISION_EPSILON, floor_min_z, size_z) + 1
        );
        if (floor_min_z >= ceil_max_z) {
            // cannot intersect
            return source_move;
        }

        // index = z + y*size_z + x*(size_z*size_y)

        final long[] bitset = cached_shape_data.voxelSet();

        if (source_move > 0.0) {
            final double source_max = source.maxY - off_y;
            final int ceil_max_y = findFloor(
                    coords_y, source_max - COLLISION_EPSILON, 0, size_y
            ) + 1; // add one, we are not interested in (coords[i] + COLLISION_EPSILON) < max

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index size on the collision axis for forward movement

            final int mul_x = size_y*size_z;
            for (int curr_y = ceil_max_y; curr_y < size_y; ++curr_y) {
                double max_dist = coords_y[curr_y] - source_max;
                if (max_dist >= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max < source_move, as coords[curr + n] < coords[curr + n + 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist >= -COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.min(max_dist, source_move);
                }
                for (int curr_x = floor_min_x; curr_x < ceil_max_x; ++curr_x) {
                    for (int curr_z = floor_min_z; curr_z < ceil_max_z; ++curr_z) {
                        final int index = curr_z + curr_y*size_z + curr_x*mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        } else {
            final double source_min = source.minY - off_y;
            final int floor_min_y = findFloor(
                    coords_y, source_min + COLLISION_EPSILON, 0, size_y
            );

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index 0 on the collision axis for backwards movement

            // note: we offset the collision axis by - 1 for the voxel bitset index, but use + 1 for the
            //       coordinate index as the voxelset stores whether the shape is solid for [index, index + 1]
            //       thus, we need to use the voxel index i-1 if we want to check that the face at index i is solid
            final int mul_x = size_y*size_z;
            for (int curr_y = floor_min_y - 1; curr_y >= 0; --curr_y) {
                double max_dist = coords_y[curr_y + 1] - source_min;
                if (max_dist <= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max > source_move, as coords[curr + n] > coords[curr + n - 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is possibly bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist <= COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.max(max_dist, source_move);
                }
                for (int curr_x = floor_min_x; curr_x < ceil_max_x; ++curr_x) {
                    for (int curr_z = floor_min_z; curr_z < ceil_max_z; ++curr_z) {
                        final int index = curr_z + curr_y*size_z + curr_x*mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        }
    }

    public static double collideZ(final VoxelShape target, final AABB source, final double source_move) {
        final AABB single_aabb = ((CollisionVoxelShape)target).moonrise$getSingleAABBRepresentation();
        if (single_aabb != null) {
            return collideZ(single_aabb, source, source_move);
        }
        // note: this function assumes that for any i in coords that coord[i + 1] - coord[i] > COLLISION_EPSILON is true

        // offsets that should be applied to coords
        final double off_x = ((CollisionVoxelShape)target).moonrise$offsetX();
        final double off_y = ((CollisionVoxelShape)target).moonrise$offsetY();
        final double off_z = ((CollisionVoxelShape)target).moonrise$offsetZ();

        final double[] coords_x = ((CollisionVoxelShape)target).moonrise$rootCoordinatesX();
        final double[] coords_y = ((CollisionVoxelShape)target).moonrise$rootCoordinatesY();
        final double[] coords_z = ((CollisionVoxelShape)target).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data = ((CollisionVoxelShape)target).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        // note: voxel bitset with set index (x, y, z) indicates that
        //       an AABB(coords_x[x], coords_y[y], coords_z[z], coords_x[x + 1], coords_y[y + 1], coords_z[z + 1])
        //       is collidable. this is the fundamental principle of operation for the voxel collision operation


        // note: we should be offsetting coords, but we can also just subtract from source as well - which is
        //       a win in terms of ops / simplicity (see findFloor, allows us to not modify coords for that)
        // note: for intersection, one we find the floor of the min we can use that as the start index
        //       for the next check as source max >= source min
        // note: we can fast check intersection on the two other axis by seeing if the min index is >= size,
        //       as this implies that coords[coords.length - 1] < source min
        //       we can also fast check by seeing if max index is < 0, as this implies that coords[0] > source max

        final int floor_min_x = Math.max(
                0,
                findFloor(coords_x, (source.minX - off_x) + COLLISION_EPSILON, 0, size_x)
        );
        if (floor_min_x >= size_x) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_x = Math.min(
                size_x,
                findFloor(coords_x, (source.maxX - off_x) - COLLISION_EPSILON, floor_min_x, size_x) + 1
        );
        if (floor_min_x >= ceil_max_x) {
            // cannot intersect
            return source_move;
        }

        final int floor_min_y = Math.max(
                0,
                findFloor(coords_y, (source.minY - off_y) + COLLISION_EPSILON, 0, size_y)
        );
        if (floor_min_y >= size_y) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_y = Math.min(
                size_y,
                findFloor(coords_y, (source.maxY - off_y) - COLLISION_EPSILON, floor_min_y, size_y) + 1
        );
        if (floor_min_y >= ceil_max_y) {
            // cannot intersect
            return source_move;
        }

        // index = z + y*size_z + x*(size_z*size_y)

        final long[] bitset = cached_shape_data.voxelSet();

        if (source_move > 0.0) {
            final double source_max = source.maxZ - off_z;
            final int ceil_max_z = findFloor(
                    coords_z, source_max - COLLISION_EPSILON, 0, size_z
            ) + 1; // add one, we are not interested in (coords[i] + COLLISION_EPSILON) < max

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index size on the collision axis for forward movement

            final int mul_x = size_y*size_z;
            for (int curr_z = ceil_max_z; curr_z < size_z; ++curr_z) {
                double max_dist = coords_z[curr_z] - source_max;
                if (max_dist >= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max < source_move, as coords[curr + n] < coords[curr + n + 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist >= -COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.min(max_dist, source_move);
                }
                for (int curr_x = floor_min_x; curr_x < ceil_max_x; ++curr_x) {
                    for (int curr_y = floor_min_y; curr_y < ceil_max_y; ++curr_y) {
                        final int index = curr_z + curr_y*size_z + curr_x*mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        } else {
            final double source_min = source.minZ - off_z;
            final int floor_min_z = findFloor(
                    coords_z, source_min + COLLISION_EPSILON, 0, size_z
            );

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index 0 on the collision axis for backwards movement

            // note: we offset the collision axis by - 1 for the voxel bitset index, but use + 1 for the
            //       coordinate index as the voxelset stores whether the shape is solid for [index, index + 1]
            //       thus, we need to use the voxel index i-1 if we want to check that the face at index i is solid
            final int mul_x = size_y*size_z;
            for (int curr_z = floor_min_z - 1; curr_z >= 0; --curr_z) {
                double max_dist = coords_z[curr_z + 1] - source_min;
                if (max_dist <= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max > source_move, as coords[curr + n] > coords[curr + n - 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is possibly bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist <= COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.max(max_dist, source_move);
                }
                for (int curr_x = floor_min_x; curr_x < ceil_max_x; ++curr_x) {
                    for (int curr_y = floor_min_y; curr_y < ceil_max_y; ++curr_y) {
                        final int index = curr_z + curr_y*size_z + curr_x*mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        }
    }

    // does not use epsilon
    public static boolean strictlyContains(final VoxelShape voxel, final Vec3 point) {
        return strictlyContains(voxel, point.x, point.y, point.z);
    }

    // does not use epsilon
    public static boolean strictlyContains(final VoxelShape voxel, double x, double y, double z) {
        final AABB single_aabb = ((CollisionVoxelShape)voxel).moonrise$getSingleAABBRepresentation();
        if (single_aabb != null) {
            return single_aabb.contains(x, y, z);
        }

        if (voxel.isEmpty()) {
            // bitset is clear, no point in searching
            return false;
        }

        // offset input
        x -= ((CollisionVoxelShape)voxel).moonrise$offsetX();
        y -= ((CollisionVoxelShape)voxel).moonrise$offsetY();
        z -= ((CollisionVoxelShape)voxel).moonrise$offsetZ();

        final double[] coords_x = ((CollisionVoxelShape)voxel).moonrise$rootCoordinatesX();
        final double[] coords_y = ((CollisionVoxelShape)voxel).moonrise$rootCoordinatesY();
        final double[] coords_z = ((CollisionVoxelShape)voxel).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data = ((CollisionVoxelShape)voxel).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        // note: should mirror AABB#contains, which is that for any point X that X >= min and X < max.
        //       specifically, it cannot collide on the max bounds of the shape

        final int index_x = findFloor(coords_x, x, 0, size_x);
        if (index_x < 0 || index_x >= size_x) {
            return false;
        }

        final int index_y = findFloor(coords_y, y, 0, size_y);
        if (index_y < 0 || index_y >= size_y) {
            return false;
        }

        final int index_z = findFloor(coords_z, z, 0, size_z);
        if (index_z < 0 || index_z >= size_z) {
            return false;
        }

        // index = z + y*size_z + x*(size_z*size_y)

        final int index = index_z + index_y*size_z + index_x*(size_z*size_y);

        final long[] bitset = cached_shape_data.voxelSet();

        return (bitset[index >>> 6] & (1L << index)) != 0L;
    }

    private static int makeBitset(final boolean ft, final boolean tf, final boolean tt) {
        // idx ff -> 0
        // idx ft -> 1
        // idx tf -> 2
        // idx tt -> 3
        return ((ft ? 1 : 0) << 1) | ((tf ? 1 : 0) << 2) | ((tt ? 1 : 0) << 3);
    }

    private static BitSetDiscreteVoxelShape merge(final CachedShapeData shapeDataFirst, final CachedShapeData shapeDataSecond,
                                                  final MergedVoxelCoordinateList mergedX, final MergedVoxelCoordinateList mergedY,
                                                  final MergedVoxelCoordinateList mergedZ,
                                                  final int booleanOp) {
        final int sizeX = mergedX.voxels;
        final int sizeY = mergedY.voxels;
        final int sizeZ = mergedZ.voxels;

        final long[] s1Voxels = shapeDataFirst.voxelSet();
        final long[] s2Voxels = shapeDataSecond.voxelSet();

        final int s1Mul1 = shapeDataFirst.sizeZ();
        final int s1Mul2 = s1Mul1 * shapeDataFirst.sizeY();

        final int s2Mul1 = shapeDataSecond.sizeZ();
        final int s2Mul2 = s2Mul1 * shapeDataSecond.sizeY();

        // note: indices may contain -1, but nothing > size
        final BitSetDiscreteVoxelShape ret = new BitSetDiscreteVoxelShape(sizeX, sizeY, sizeZ);

        boolean empty = true;

        int mergedIdx = 0;
        for (int idxX = 0; idxX < sizeX; ++idxX) {
            final int s1x = mergedX.firstIndices[idxX];
            final int s2x = mergedX.secondIndices[idxX];
            boolean setX = false;
            for (int idxY = 0; idxY < sizeY; ++idxY) {
                final int s1y = mergedY.firstIndices[idxY];
                final int s2y = mergedY.secondIndices[idxY];
                boolean setY = false;
                for (int idxZ = 0; idxZ < sizeZ; ++idxZ) {
                    final int s1z = mergedZ.firstIndices[idxZ];
                    final int s2z = mergedZ.secondIndices[idxZ];

                    int idx1;
                    int idx2;

                    final int isS1Full = (s1x | s1y | s1z) < 0 ? 0 : (int)((s1Voxels[(idx1 = s1z + s1y*s1Mul1 + s1x*s1Mul2) >>> 6] >>> idx1) & 1L);
                    final int isS2Full = (s2x | s2y | s2z) < 0 ? 0 : (int)((s2Voxels[(idx2 = s2z + s2y*s2Mul1 + s2x*s2Mul2) >>> 6] >>> idx2) & 1L);

                    // idx ff -> 0
                    // idx ft -> 1
                    // idx tf -> 2
                    // idx tt -> 3

                    final boolean res = (booleanOp & (1 << (isS2Full | (isS1Full << 1)))) != 0;
                    setY |= res;
                    setX |= res;

                    if (res) {
                        empty = false;
                        // inline and optimize fill operation
                        ret.zMin = Math.min(ret.zMin, idxZ);
                        ret.zMax = Math.max(ret.zMax, idxZ + 1);
                        ret.storage.set(mergedIdx);
                    }

                    ++mergedIdx;
                }
                if (setY) {
                    ret.yMin = Math.min(ret.yMin, idxY);
                    ret.yMax = Math.max(ret.yMax, idxY + 1);
                }
            }
            if (setX) {
                ret.xMin = Math.min(ret.xMin, idxX);
                ret.xMax = Math.max(ret.xMax, idxX + 1);
            }
        }

        return empty ? null : ret;
    }

    private static boolean isMergeEmpty(final CachedShapeData shapeDataFirst, final CachedShapeData shapeDataSecond,
                                        final MergedVoxelCoordinateList mergedX, final MergedVoxelCoordinateList mergedY,
                                        final MergedVoxelCoordinateList mergedZ,
                                        final int booleanOp) {
        final int sizeX = mergedX.voxels;
        final int sizeY = mergedY.voxels;
        final int sizeZ = mergedZ.voxels;

        final long[] s1Voxels = shapeDataFirst.voxelSet();
        final long[] s2Voxels = shapeDataSecond.voxelSet();

        final int s1Mul1 = shapeDataFirst.sizeZ();
        final int s1Mul2 = s1Mul1 * shapeDataFirst.sizeY();

        final int s2Mul1 = shapeDataSecond.sizeZ();
        final int s2Mul2 = s2Mul1 * shapeDataSecond.sizeY();

        // note: indices may contain -1, but nothing > size
        for (int idxX = 0; idxX < sizeX; ++idxX) {
            final int s1x = mergedX.firstIndices[idxX];
            final int s2x = mergedX.secondIndices[idxX];
            for (int idxY = 0; idxY < sizeY; ++idxY) {
                final int s1y = mergedY.firstIndices[idxY];
                final int s2y = mergedY.secondIndices[idxY];
                for (int idxZ = 0; idxZ < sizeZ; ++idxZ) {
                    final int s1z = mergedZ.firstIndices[idxZ];
                    final int s2z = mergedZ.secondIndices[idxZ];

                    int idx1;
                    int idx2;

                    final int isS1Full = (s1x | s1y | s1z) < 0 ? 0 : (int)((s1Voxels[(idx1 = s1z + s1y*s1Mul1 + s1x*s1Mul2) >>> 6] >>> idx1) & 1L);
                    final int isS2Full = (s2x | s2y | s2z) < 0 ? 0 : (int)((s2Voxels[(idx2 = s2z + s2y*s2Mul1 + s2x*s2Mul2) >>> 6] >>> idx2) & 1L);

                    // idx ff -> 0
                    // idx ft -> 1
                    // idx tf -> 2
                    // idx tt -> 3

                    final boolean res = (booleanOp & (1 << (isS2Full | (isS1Full << 1)))) != 0;

                    if (res) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public static VoxelShape joinOptimized(final VoxelShape first, final VoxelShape second, final BooleanOp operator) {
        return joinUnoptimized(first, second, operator).optimize();
    }

    public static VoxelShape joinUnoptimized(final VoxelShape first, final VoxelShape second, final BooleanOp operator) {
        final boolean ff = operator.apply(false, false);
        if (ff) {
            // technically, should be an infinite box but that's clearly an error
            throw new UnsupportedOperationException("Ambiguous operator: (false, false) -> true");
        }

        final boolean tt = operator.apply(true, true);

        if (first == second) {
            return tt ? first : Shapes.empty();
        }

        final boolean ft = operator.apply(false, true);
        final boolean tf = operator.apply(true, false);

        if (first.isEmpty()) {
            return ft ? second : Shapes.empty();
        }
        if (second.isEmpty()) {
            return tf ? first : Shapes.empty();
        }

        if (!tt) {
            // try to check for no intersection, since tt = false
            final AABB aabbF = ((CollisionVoxelShape)first).moonrise$getSingleAABBRepresentation();
            final AABB aabbS = ((CollisionVoxelShape)second).moonrise$getSingleAABBRepresentation();

            final boolean intersect;

            final boolean hasAABBF = aabbF != null;
            final boolean hasAABBS = aabbS != null;
            if (hasAABBF | hasAABBS) {
                if (hasAABBF & hasAABBS) {
                    intersect = voxelShapeIntersect(aabbF, aabbS);
                } else if (hasAABBF) {
                    intersect = voxelShapeIntersectNoEmpty(second, aabbF);
                } else {
                    intersect = voxelShapeIntersectNoEmpty(first, aabbS);
                }
            } else {
                // expect cached bounds
                intersect = voxelShapeIntersect(first.bounds(), second.bounds());
            }

            if (!intersect) {
                if (!tf & !ft) {
                    return Shapes.empty();
                }
                if (!tf | !ft) {
                    return tf ? first : second;
                }
            }
        }

        final MergedVoxelCoordinateList mergedX = MergedVoxelCoordinateList.merge(
                ((CollisionVoxelShape)first).moonrise$rootCoordinatesX(), ((CollisionVoxelShape)first).moonrise$offsetX(),
                ((CollisionVoxelShape)second).moonrise$rootCoordinatesX(), ((CollisionVoxelShape)second).moonrise$offsetX(),
                ft, tf
        );
        if (mergedX == MergedVoxelCoordinateList.EMPTY) {
            return Shapes.empty();
        }
        final MergedVoxelCoordinateList mergedY = MergedVoxelCoordinateList.merge(
                ((CollisionVoxelShape)first).moonrise$rootCoordinatesY(), ((CollisionVoxelShape)first).moonrise$offsetY(),
                ((CollisionVoxelShape)second).moonrise$rootCoordinatesY(), ((CollisionVoxelShape)second).moonrise$offsetY(),
                ft, tf
        );
        if (mergedY == MergedVoxelCoordinateList.EMPTY) {
            return Shapes.empty();
        }
        final MergedVoxelCoordinateList mergedZ = MergedVoxelCoordinateList.merge(
                ((CollisionVoxelShape)first).moonrise$rootCoordinatesZ(), ((CollisionVoxelShape)first).moonrise$offsetZ(),
                ((CollisionVoxelShape)second).moonrise$rootCoordinatesZ(), ((CollisionVoxelShape)second).moonrise$offsetZ(),
                ft, tf
        );
        if (mergedZ == MergedVoxelCoordinateList.EMPTY) {
            return Shapes.empty();
        }

        final CachedShapeData shapeDataFirst = ((CollisionVoxelShape)first).moonrise$getCachedVoxelData();
        final CachedShapeData shapeDataSecond = ((CollisionVoxelShape)second).moonrise$getCachedVoxelData();

        final BitSetDiscreteVoxelShape mergedShape = merge(
                shapeDataFirst, shapeDataSecond,
                mergedX, mergedY, mergedZ,
                makeBitset(ft, tf, tt)
        );

        if (mergedShape == null) {
            return Shapes.empty();
        }

        return new ArrayVoxelShape(
                mergedShape, mergedX.wrapCoords(), mergedY.wrapCoords(), mergedZ.wrapCoords()
        );
    }

    public static boolean isJoinNonEmpty(final VoxelShape first, final VoxelShape second, final BooleanOp operator) {
        final boolean ff = operator.apply(false, false);
        if (ff) {
            // technically, should be an infinite box but that's clearly an error
            throw new UnsupportedOperationException("Ambiguous operator: (false, false) -> true");
        }
        final boolean firstEmpty = first.isEmpty();
        final boolean secondEmpty = second.isEmpty();
        if (firstEmpty | secondEmpty) {
            return operator.apply(!firstEmpty, !secondEmpty);
        }

        final boolean tt = operator.apply(true, true);

        if (first == second) {
            return tt;
        }

        final boolean ft = operator.apply(false, true);
        final boolean tf = operator.apply(true, false);

        // try to check intersection
        final AABB aabbF = ((CollisionVoxelShape)first).moonrise$getSingleAABBRepresentation();
        final AABB aabbS = ((CollisionVoxelShape)second).moonrise$getSingleAABBRepresentation();

        final boolean intersect;

        final boolean hasAABBF = aabbF != null;
        final boolean hasAABBS = aabbS != null;
        if (hasAABBF | hasAABBS) {
            if (hasAABBF & hasAABBS) {
                intersect = voxelShapeIntersect(aabbF, aabbS);
            } else if (hasAABBF) {
                intersect = voxelShapeIntersectNoEmpty(second, aabbF);
            } else {
                // hasAABBS -> true
                intersect = voxelShapeIntersectNoEmpty(first, aabbS);
            }

            if (!intersect) {
                // is only non-empty if we take from first or second, as there is no overlap AND both shapes are non-empty
                return tf | ft;
            } else if (tt) {
                // intersect = true && tt = true -> non-empty merged shape
                return true;
            }
        } else {
            // expect cached bounds
            intersect = voxelShapeIntersect(first.bounds(), second.bounds());
            if (!intersect) {
                // is only non-empty if we take from first or second, as there is no intersection
                return tf | ft;
            }
        }

        final MergedVoxelCoordinateList mergedX = MergedVoxelCoordinateList.merge(
                ((CollisionVoxelShape)first).moonrise$rootCoordinatesX(), ((CollisionVoxelShape)first).moonrise$offsetX(),
                ((CollisionVoxelShape)second).moonrise$rootCoordinatesX(), ((CollisionVoxelShape)second).moonrise$offsetX(),
                ft, tf
        );
        if (mergedX == MergedVoxelCoordinateList.EMPTY) {
            return false;
        }
        final MergedVoxelCoordinateList mergedY = MergedVoxelCoordinateList.merge(
                ((CollisionVoxelShape)first).moonrise$rootCoordinatesY(), ((CollisionVoxelShape)first).moonrise$offsetY(),
                ((CollisionVoxelShape)second).moonrise$rootCoordinatesY(), ((CollisionVoxelShape)second).moonrise$offsetY(),
                ft, tf
        );
        if (mergedY == MergedVoxelCoordinateList.EMPTY) {
            return false;
        }
        final MergedVoxelCoordinateList mergedZ = MergedVoxelCoordinateList.merge(
                ((CollisionVoxelShape)first).moonrise$rootCoordinatesZ(), ((CollisionVoxelShape)first).moonrise$offsetZ(),
                ((CollisionVoxelShape)second).moonrise$rootCoordinatesZ(), ((CollisionVoxelShape)second).moonrise$offsetZ(),
                ft, tf
        );
        if (mergedZ == MergedVoxelCoordinateList.EMPTY) {
            return false;
        }

        final CachedShapeData shapeDataFirst = ((CollisionVoxelShape)first).moonrise$getCachedVoxelData();
        final CachedShapeData shapeDataSecond = ((CollisionVoxelShape)second).moonrise$getCachedVoxelData();

        return !isMergeEmpty(
                shapeDataFirst, shapeDataSecond,
                mergedX, mergedY, mergedZ,
                makeBitset(ft, tf, tt)
        );
    }

    private static final class MergedVoxelCoordinateList {

        private static final int[][] SIMPLE_INDICES_CACHE = new int[64][];
        static {
            for (int i = 0; i < SIMPLE_INDICES_CACHE.length; ++i) {
                SIMPLE_INDICES_CACHE[i] = getIndices(i);
            }
        }

        private static final MergedVoxelCoordinateList EMPTY = new MergedVoxelCoordinateList(
                new double[] { 0.0 }, 0.0, new int[0], new int[0], 0
        );

        private static int[] getIndices(final int length) {
            final int[] ret = new int[length];

            for (int i = 1; i < length; ++i) {
                ret[i] = i;
            }

            return ret;
        }

        // indices above voxel size are always set to -1
        public final double[] coordinates;
        public final double coordinateOffset;
        public final int[] firstIndices;
        public final int[] secondIndices;
        public final int voxels;

        private MergedVoxelCoordinateList(final double[] coordinates, final double coordinateOffset,
                                          final int[] firstIndices, final int[] secondIndices, final int voxels) {
            this.coordinates = coordinates;
            this.coordinateOffset = coordinateOffset;
            this.firstIndices = firstIndices;
            this.secondIndices = secondIndices;
            this.voxels = voxels;
        }

        public DoubleList wrapCoords() {
            if (this.coordinateOffset == 0.0) {
                return DoubleArrayList.wrap(this.coordinates, this.voxels + 1);
            }
            return new OffsetDoubleList(DoubleArrayList.wrap(this.coordinates, this.voxels + 1), this.coordinateOffset);
        }

        // assume coordinates.length > 1
        public static MergedVoxelCoordinateList getForSingle(final double[] coordinates, final double offset) {
            final int voxels = coordinates.length - 1;
            final int[] indices = voxels < SIMPLE_INDICES_CACHE.length ? SIMPLE_INDICES_CACHE[voxels] : getIndices(voxels);

            return new MergedVoxelCoordinateList(coordinates, offset, indices, indices, voxels);
        }

        // assume coordinates.length > 1
        public static MergedVoxelCoordinateList merge(final double[] firstCoordinates, final double firstOffset,
                                                      final double[] secondCoordinates, final double secondOffset,
                                                      final boolean ft, final boolean tf) {
            if (firstCoordinates == secondCoordinates && firstOffset == secondOffset) {
                return getForSingle(firstCoordinates, firstOffset);
            }

            final int firstCount = firstCoordinates.length;
            final int secondCount = secondCoordinates.length;

            final int voxelsFirst = firstCount - 1;
            final int voxelsSecond = secondCount - 1;

            final int maxCount = firstCount + secondCount;

            final double[] coordinates = new double[maxCount];
            final int[] firstIndices = new int[maxCount];
            final int[] secondIndices = new int[maxCount];

            final boolean notTF = !tf;
            final boolean notFT = !ft;

            int firstIndex = 0;
            int secondIndex = 0;
            int resultSize = 0;

            // note: operations on NaN are false
            double last = Double.NaN;

            for (;;) {
                final boolean noneLeftFirst = firstIndex >= firstCount;
                final boolean noneLeftSecond = secondIndex >= secondCount;

                if ((noneLeftFirst & noneLeftSecond) | (noneLeftSecond & notTF) | (noneLeftFirst & notFT)) {
                    break;
                }

                final boolean firstZero = firstIndex == 0;
                final boolean secondZero = secondIndex == 0;

                final double select;

                if (noneLeftFirst) {
                    // noneLeftSecond -> false
                    // notFT -> false
                    select = secondCoordinates[secondIndex] + secondOffset;
                    ++secondIndex;
                } else if (noneLeftSecond) {
                    // noneLeftFirst -> false
                    // notTF -> false
                    select = firstCoordinates[firstIndex] + firstOffset;
                    ++firstIndex;
                } else {
                    // noneLeftFirst | noneLeftSecond -> false
                    // notTF -> ??
                    // notFT -> ??
                    final boolean breakFirst = notTF & secondZero;
                    final boolean breakSecond = notFT & firstZero;

                    final double first = firstCoordinates[firstIndex] + firstOffset;
                    final double second = secondCoordinates[secondIndex] + secondOffset;
                    final boolean useFirst = first < (second + COLLISION_EPSILON);
                    final boolean cont = (useFirst & breakFirst) | (!useFirst & breakSecond);

                    select = useFirst ? first : second;
                    firstIndex += useFirst ? 1 : 0;
                    secondIndex += 1 ^ (useFirst ? 1 : 0);

                    if (cont) {
                        continue;
                    }
                }

                int prevFirst = firstIndex - 1;
                prevFirst = prevFirst >= voxelsFirst ? -1 : prevFirst;
                int prevSecond = secondIndex - 1;
                prevSecond = prevSecond >= voxelsSecond ? -1 : prevSecond;

                if (last >= (select - COLLISION_EPSILON)) {
                    // note: any operations on NaN is false
                    firstIndices[resultSize - 1] = prevFirst;
                    secondIndices[resultSize - 1] = prevSecond;
                } else {
                    firstIndices[resultSize] = prevFirst;
                    secondIndices[resultSize] = prevSecond;
                    coordinates[resultSize] = select;

                    ++resultSize;
                    last = select;
                }
            }

            return resultSize <= 1 ? EMPTY : new MergedVoxelCoordinateList(coordinates, 0.0, firstIndices, secondIndices, resultSize - 1);
        }
    }

    public static boolean equals(final DiscreteVoxelShape shape1, final DiscreteVoxelShape shape2) {
        final CachedShapeData cachedShapeData1 = ((CollisionDiscreteVoxelShape)shape1).moonrise$getOrCreateCachedShapeData();
        final CachedShapeData cachedShapeData2 = ((CollisionDiscreteVoxelShape)shape2).moonrise$getOrCreateCachedShapeData();

        final boolean isEmpty1 = cachedShapeData1.isEmpty();
        final boolean isEmpty2 = cachedShapeData2.isEmpty();

        if (isEmpty1 & isEmpty2) {
            return true;
        } else if (isEmpty1 ^ isEmpty2) {
            return false;
        } // else: isEmpty1 = isEmpty2 = false

        if (cachedShapeData1.hasSingleAABB() != cachedShapeData2.hasSingleAABB()) {
            return false;
        }

        if (cachedShapeData1.sizeX() != cachedShapeData2.sizeX()) {
            return false;
        }
        if (cachedShapeData1.sizeY() != cachedShapeData2.sizeY()) {
            return false;
        }
        if (cachedShapeData1.sizeZ() != cachedShapeData2.sizeZ()) {
            return false;
        }

        return Arrays.equals(cachedShapeData1.voxelSet(), cachedShapeData2.voxelSet());
    }

    // useful only for testing
    public static boolean equals(final VoxelShape shape1, final VoxelShape shape2) {
        if (shape1.isEmpty() & shape2.isEmpty()) {
            return true;
        } else if (shape1.isEmpty() ^ shape2.isEmpty()) {
            return false;
        }

        if (!equals(shape1.shape, shape2.shape)) {
            return false;
        }

        return shape1.getCoords(Direction.Axis.X).equals(shape2.getCoords(Direction.Axis.X)) &&
                shape1.getCoords(Direction.Axis.Y).equals(shape2.getCoords(Direction.Axis.Y)) &&
                shape1.getCoords(Direction.Axis.Z).equals(shape2.getCoords(Direction.Axis.Z));
    }

    public static boolean areAnyFull(final DiscreteVoxelShape shape) {
        if (shape.isEmpty()) {
            return false;
        }

        final int sizeX = shape.getXSize();
        final int sizeY = shape.getYSize();
        final int sizeZ = shape.getZSize();

        for (int x = 0; x < sizeX; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeZ; ++z) {
                    if (shape.isFull(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static String shapeMismatch(final DiscreteVoxelShape shape1, final DiscreteVoxelShape shape2) {
        final CachedShapeData cachedShapeData1 = ((CollisionDiscreteVoxelShape)shape1).moonrise$getOrCreateCachedShapeData();
        final CachedShapeData cachedShapeData2 = ((CollisionDiscreteVoxelShape)shape2).moonrise$getOrCreateCachedShapeData();

        final boolean isEmpty1 = cachedShapeData1.isEmpty();
        final boolean isEmpty2 = cachedShapeData2.isEmpty();

        if (isEmpty1 & isEmpty2) {
            return null;
        } else if (isEmpty1 ^ isEmpty2) {
            return null;
        } // else: isEmpty1 = isEmpty2 = false

        if (cachedShapeData1.sizeX() != cachedShapeData2.sizeX()) {
            return "size x: " + cachedShapeData1.sizeX() + " != " + cachedShapeData2.sizeX();
        }
        if (cachedShapeData1.sizeY() != cachedShapeData2.sizeY()) {
            return "size y: " + cachedShapeData1.sizeY() + " != " + cachedShapeData2.sizeY();
        }
        if (cachedShapeData1.sizeZ() != cachedShapeData2.sizeZ()) {
            return "size z: " + cachedShapeData1.sizeZ() + " != " + cachedShapeData2.sizeZ();
        }

        final StringBuilder ret = new StringBuilder();

        final int sizeX = cachedShapeData1.sizeX();;
        final int sizeY = cachedShapeData1.sizeY();
        final int sizeZ = cachedShapeData1.sizeZ();

        boolean first = true;

        for (int x = 0; x < sizeX; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeZ; ++z) {
                    final boolean isFull1 = shape1.isFull(x, y, z);
                    final boolean isFull2 = shape2.isFull(x, y, z);

                    if (isFull1 == isFull2) {
                        continue;
                    }

                    if (first) {
                        first = false;
                    } else {
                        ret.append(", ");
                    }

                    ret.append("(").append(x).append(",").append(y).append(",").append(z)
                        .append("): shape1: ").append(isFull1).append(", shape2: ").append(isFull2);
                }
            }
        }

        return ret.isEmpty() ? null : ret.toString();
    }

    public static AABB offsetX(final AABB box, final double dx) {
        return new AABB(box.minX + dx, box.minY, box.minZ, box.maxX + dx, box.maxY, box.maxZ);
    }

    public static AABB offsetY(final AABB box, final double dy) {
        return new AABB(box.minX, box.minY + dy, box.minZ, box.maxX, box.maxY + dy, box.maxZ);
    }

    public static AABB offsetZ(final AABB box, final double dz) {
        return new AABB(box.minX, box.minY, box.minZ + dz, box.maxX, box.maxY, box.maxZ + dz);
    }

    public static AABB expandRight(final AABB box, final double dx) { // dx > 0.0
        return new AABB(box.minX, box.minY, box.minZ, box.maxX + dx, box.maxY, box.maxZ);
    }

    public static AABB expandLeft(final AABB box, final double dx) { // dx < 0.0
        return new AABB(box.minX - dx, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static AABB expandUpwards(final AABB box, final double dy) { // dy > 0.0
        return new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY + dy, box.maxZ);
    }

    public static AABB expandDownwards(final AABB box, final double dy) { // dy < 0.0
        return new AABB(box.minX, box.minY - dy, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static AABB expandForwards(final AABB box, final double dz) { // dz > 0.0
        return new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ + dz);
    }

    public static AABB expandBackwards(final AABB box, final double dz) { // dz < 0.0
        return new AABB(box.minX, box.minY, box.minZ - dz, box.maxX, box.maxY, box.maxZ);
    }

    public static AABB cutRight(final AABB box, final double dx) { // dx > 0.0
        return new AABB(box.maxX, box.minY, box.minZ, box.maxX + dx, box.maxY, box.maxZ);
    }

    public static AABB cutLeft(final AABB box, final double dx) { // dx < 0.0
        return new AABB(box.minX + dx, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
    }

    public static AABB cutUpwards(final AABB box, final double dy) { // dy > 0.0
        return new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + dy, box.maxZ);
    }

    public static AABB cutDownwards(final AABB box, final double dy) { // dy < 0.0
        return new AABB(box.minX, box.minY + dy, box.minZ, box.maxX, box.minY, box.maxZ);
    }

    public static AABB cutForwards(final AABB box, final double dz) { // dz > 0.0
        return new AABB(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ + dz);
    }

    public static AABB cutBackwards(final AABB box, final double dz) { // dz < 0.0
        return new AABB(box.minX, box.minY, box.minZ + dz, box.maxX, box.maxY, box.minZ);
    }

    public static double performAABBCollisionsX(final AABB currentBoundingBox, double value, final List<AABB> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final AABB target = potentialCollisions.get(i);
            value = collideX(target, currentBoundingBox, value);
        }

        return Math.abs(value) < COLLISION_EPSILON ? 0.0 : value;
    }

    public static double performAABBCollisionsY(final AABB currentBoundingBox, double value, final List<AABB> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final AABB target = potentialCollisions.get(i);
            value = collideY(target, currentBoundingBox, value);
        }

        return Math.abs(value) < COLLISION_EPSILON ? 0.0 : value;
    }

    public static double performAABBCollisionsZ(final AABB currentBoundingBox, double value, final List<AABB> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final AABB target = potentialCollisions.get(i);
            value = collideZ(target, currentBoundingBox, value);
        }

        return Math.abs(value) < COLLISION_EPSILON ? 0.0 : value;
    }

    public static double performVoxelCollisionsX(final AABB currentBoundingBox, double value, final List<VoxelShape> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final VoxelShape target = potentialCollisions.get(i);
            value = collideX(target, currentBoundingBox, value);
        }

        return Math.abs(value) < COLLISION_EPSILON ? 0.0 : value;
    }

    public static double performVoxelCollisionsY(final AABB currentBoundingBox, double value, final List<VoxelShape> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final VoxelShape target = potentialCollisions.get(i);
            value = collideY(target, currentBoundingBox, value);
        }

        return Math.abs(value) < COLLISION_EPSILON ? 0.0 : value;
    }

    public static double performVoxelCollisionsZ(final AABB currentBoundingBox, double value, final List<VoxelShape> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final VoxelShape target = potentialCollisions.get(i);
            value = collideZ(target, currentBoundingBox, value);
        }

        return Math.abs(value) < COLLISION_EPSILON ? 0.0 : value;
    }

    public static Vec3 performVoxelCollisions(final Vec3 moveVector, AABB axisalignedbb, final List<VoxelShape> potentialCollisions) {
        double x = moveVector.x;
        double y = moveVector.y;
        double z = moveVector.z;

        if (y != 0.0) {
            y = performVoxelCollisionsY(axisalignedbb, y, potentialCollisions);
            if (y != 0.0) {
                axisalignedbb = offsetY(axisalignedbb, y);
            }
        }

        final boolean xSmaller = Math.abs(x) < Math.abs(z);

        if (xSmaller && z != 0.0) {
            z = performVoxelCollisionsZ(axisalignedbb, z, potentialCollisions);
            if (z != 0.0) {
                axisalignedbb = offsetZ(axisalignedbb, z);
            }
        }

        if (x != 0.0) {
            x = performVoxelCollisionsX(axisalignedbb, x, potentialCollisions);
            if (!xSmaller && x != 0.0) {
                axisalignedbb = offsetX(axisalignedbb, x);
            }
        }

        if (!xSmaller && z != 0.0) {
            z = performVoxelCollisionsZ(axisalignedbb, z, potentialCollisions);
        }

        return new Vec3(x, y, z);
    }

    public static Vec3 performAABBCollisions(final Vec3 moveVector, AABB axisalignedbb, final List<AABB> potentialCollisions) {
        double x = moveVector.x;
        double y = moveVector.y;
        double z = moveVector.z;

        if (y != 0.0) {
            y = performAABBCollisionsY(axisalignedbb, y, potentialCollisions);
            if (y != 0.0) {
                axisalignedbb = offsetY(axisalignedbb, y);
            }
        }

        final boolean xSmaller = Math.abs(x) < Math.abs(z);

        if (xSmaller && z != 0.0) {
            z = performAABBCollisionsZ(axisalignedbb, z, potentialCollisions);
            if (z != 0.0) {
                axisalignedbb = offsetZ(axisalignedbb, z);
            }
        }

        if (x != 0.0) {
            x = performAABBCollisionsX(axisalignedbb, x, potentialCollisions);
            if (!xSmaller && x != 0.0) {
                axisalignedbb = offsetX(axisalignedbb, x);
            }
        }

        if (!xSmaller && z != 0.0) {
            z = performAABBCollisionsZ(axisalignedbb, z, potentialCollisions);
        }

        return new Vec3(x, y, z);
    }

    public static Vec3 performCollisions(final Vec3 moveVector, AABB axisalignedbb,
                                         final List<VoxelShape> voxels,
                                         final List<AABB> aabbs) {
        if (voxels.isEmpty()) {
            // fast track only AABBs
            return performAABBCollisions(moveVector, axisalignedbb, aabbs);
        }

        double x = moveVector.x;
        double y = moveVector.y;
        double z = moveVector.z;

        if (y != 0.0) {
            y = performAABBCollisionsY(axisalignedbb, y, aabbs);
            y = performVoxelCollisionsY(axisalignedbb, y, voxels);
            if (y != 0.0) {
                axisalignedbb = offsetY(axisalignedbb, y);
            }
        }

        final boolean xSmaller = Math.abs(x) < Math.abs(z);

        if (xSmaller && z != 0.0) {
            z = performAABBCollisionsZ(axisalignedbb, z, aabbs);
            z = performVoxelCollisionsZ(axisalignedbb, z, voxels);
            if (z != 0.0) {
                axisalignedbb = offsetZ(axisalignedbb, z);
            }
        }

        if (x != 0.0) {
            x = performAABBCollisionsX(axisalignedbb, x, aabbs);
            x = performVoxelCollisionsX(axisalignedbb, x, voxels);
            if (!xSmaller && x != 0.0) {
                axisalignedbb = offsetX(axisalignedbb, x);
            }
        }

        if (!xSmaller && z != 0.0) {
            z = performAABBCollisionsZ(axisalignedbb, z, aabbs);
            z = performVoxelCollisionsZ(axisalignedbb, z, voxels);
        }

        return new Vec3(x, y, z);
    }

    public static boolean isCollidingWithBorder(final WorldBorder worldborder, final AABB boundingBox) {
        return isCollidingWithBorder(worldborder, boundingBox.minX, boundingBox.maxX, boundingBox.minZ, boundingBox.maxZ);
    }

    public static boolean isCollidingWithBorder(final WorldBorder worldborder,
                                                final double boxMinX, final double boxMaxX,
                                                final double boxMinZ, final double boxMaxZ) {
        final double borderMinX = Math.floor(worldborder.getMinX()); // -X
        final double borderMaxX = Math.ceil(worldborder.getMaxX()); // +X

        final double borderMinZ = Math.floor(worldborder.getMinZ()); // -Z
        final double borderMaxZ = Math.ceil(worldborder.getMaxZ()); // +Z

        // inverted check for world border enclosing the specified box expanded by -EPSILON
        return (borderMinX - boxMinX) > CollisionUtil.COLLISION_EPSILON || (borderMaxX - boxMaxX) < -CollisionUtil.COLLISION_EPSILON ||
                (borderMinZ - boxMinZ) > CollisionUtil.COLLISION_EPSILON || (borderMaxZ - boxMaxZ) < -CollisionUtil.COLLISION_EPSILON;
    }

    /* Math.max/min specify that any NaN argument results in a NaN return, unlike these functions */
    private static double min(final double x, final double y) {
        return x < y ? x : y;
    }

    private static double max(final double x, final double y) {
        return x > y ? x : y;
    }

    public static final int COLLISION_FLAG_LOAD_CHUNKS = 1 << 0;
    public static final int COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS = 1 << 1;
    public static final int COLLISION_FLAG_CHECK_BORDER = 1 << 2;
    public static final int COLLISION_FLAG_CHECK_ONLY = 1 << 3;

    public static boolean getCollisionsForBlocksOrWorldBorder(final Level world, final Entity entity, final AABB aabb,
                                                              final List<VoxelShape> intoVoxel, final List<AABB> intoAABB,
                                                              final int collisionFlags, final BiPredicate<BlockState, BlockPos> predicate) {
        final boolean checkOnly = (collisionFlags & COLLISION_FLAG_CHECK_ONLY) != 0;
        boolean ret = false;

        if ((collisionFlags & COLLISION_FLAG_CHECK_BORDER) != 0) {
            final WorldBorder worldBorder = world.getWorldBorder();
            if (CollisionUtil.isCollidingWithBorder(worldBorder, aabb) && entity != null && worldBorder.isInsideCloseToBorder(entity, aabb)) {
                if (checkOnly) {
                    return true;
                } else {
                    final VoxelShape borderShape = worldBorder.getCollisionShape();
                    intoVoxel.add(borderShape);
                    ret = true;
                }
            }
        }

        final int minSection = WorldUtil.getMinSection(world);

        final int minBlockX = Mth.floor(aabb.minX - COLLISION_EPSILON) - 1;
        final int maxBlockX = Mth.floor(aabb.maxX + COLLISION_EPSILON) + 1;

        final int minBlockY = Math.max((minSection << 4) - 1, Mth.floor(aabb.minY - COLLISION_EPSILON) - 1);
        final int maxBlockY = Math.min((WorldUtil.getMaxSection(world) << 4) + 16, Mth.floor(aabb.maxY + COLLISION_EPSILON) + 1);

        final int minBlockZ = Mth.floor(aabb.minZ - COLLISION_EPSILON) - 1;
        final int maxBlockZ = Mth.floor(aabb.maxZ + COLLISION_EPSILON) + 1;

        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        final CollisionContext collisionShape = new LazyEntityCollisionContext(entity);

        // special cases:
        if (minBlockY > maxBlockY) {
            // no point in checking
            return ret;
        }

        final int minChunkX = minBlockX >> 4;
        final int maxChunkX = maxBlockX >> 4;

        final int minChunkY = minBlockY >> 4;
        final int maxChunkY = maxBlockY >> 4;

        final int minChunkZ = minBlockZ >> 4;
        final int maxChunkZ = maxBlockZ >> 4;

        final boolean loadChunks = (collisionFlags & COLLISION_FLAG_LOAD_CHUNKS) != 0;
        final ChunkSource chunkSource = world.getChunkSource();

        for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
            for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                final ChunkAccess chunk = chunkSource.getChunk(currChunkX, currChunkZ, ChunkStatus.FULL, loadChunks);

                if (chunk == null) {
                    if ((collisionFlags & COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS) != 0) {
                        if (checkOnly) {
                            return true;
                        } else {
                            intoAABB.add(getBoxForChunk(currChunkX, currChunkZ));
                            ret = true;
                        }
                    }
                    continue;
                }

                final LevelChunkSection[] sections = chunk.getSections();

                // bound y
                for (int currChunkY = minChunkY; currChunkY <= maxChunkY; ++currChunkY) {
                    final int sectionIdx = currChunkY - minSection;
                    if (sectionIdx < 0 || sectionIdx >= sections.length) {
                        continue;
                    }
                    final LevelChunkSection section = sections[sectionIdx];
                    if (section.hasOnlyAir()) {
                        // empty
                        continue;
                    }

                    final boolean hasSpecial = ((BlockCountingChunkSection)section).moonrise$hasSpecialCollidingBlocks();
                    final int sectionAdjust = !hasSpecial ? 1 : 0;

                    final PalettedContainer<BlockState> blocks = section.states;

                    final int minXIterate = currChunkX == minChunkX ? (minBlockX & 15) + sectionAdjust : 0;
                    final int maxXIterate = currChunkX == maxChunkX ? (maxBlockX & 15) - sectionAdjust : 15;
                    final int minZIterate = currChunkZ == minChunkZ ? (minBlockZ & 15) + sectionAdjust : 0;
                    final int maxZIterate = currChunkZ == maxChunkZ ? (maxBlockZ & 15) - sectionAdjust : 15;
                    final int minYIterate = currChunkY == minChunkY ? (minBlockY & 15) + sectionAdjust : 0;
                    final int maxYIterate = currChunkY == maxChunkY ? (maxBlockY & 15) - sectionAdjust : 15;

                    for (int currY = minYIterate; currY <= maxYIterate; ++currY) {
                        final int blockY = currY | (currChunkY << 4);
                        for (int currZ = minZIterate; currZ <= maxZIterate; ++currZ) {
                            final int blockZ = currZ | (currChunkZ << 4);
                            for (int currX = minXIterate; currX <= maxXIterate; ++currX) {
                                final int localBlockIndex = (currX) | (currZ << 4) | ((currY) << 8);
                                final int blockX = currX | (currChunkX << 4);

                                final int edgeCount = hasSpecial ? ((blockX == minBlockX || blockX == maxBlockX) ? 1 : 0) +
                                    ((blockY == minBlockY || blockY == maxBlockY) ? 1 : 0) +
                                    ((blockZ == minBlockZ || blockZ == maxBlockZ) ? 1 : 0) : 0;
                                if (edgeCount == 3) {
                                    continue;
                                }

                                final BlockState blockData = blocks.get(localBlockIndex);

                                if (((CollisionBlockState)blockData).moonrise$emptyContextCollisionShape()) {
                                    continue;
                                }

                                VoxelShape blockCollision = ((CollisionBlockState)blockData).moonrise$getConstantContextCollisionShape();

                                if (edgeCount == 0 || ((edgeCount != 1 || blockData.hasLargeCollisionShape()) && (edgeCount != 2 || blockData.getBlock() == Blocks.MOVING_PISTON))) {
                                    if (blockCollision == null) {
                                        mutablePos.set(blockX, blockY, blockZ);
                                        blockCollision = blockData.getCollisionShape(world, mutablePos, collisionShape);
                                    }

                                    AABB singleAABB = ((CollisionVoxelShape)blockCollision).moonrise$getSingleAABBRepresentation();
                                    if (singleAABB != null) {
                                        singleAABB = singleAABB.move((double)blockX, (double)blockY, (double)blockZ);
                                        if (!voxelShapeIntersect(aabb, singleAABB)) {
                                            continue;
                                        }

                                        if (predicate != null) {
                                            mutablePos.set(blockX, blockY, blockZ);
                                            if (!predicate.test(blockData, mutablePos)) {
                                                continue;
                                            }
                                        }

                                        if (checkOnly) {
                                            return true;
                                        } else {
                                            ret = true;
                                            intoAABB.add(singleAABB);
                                            continue;
                                        }
                                    }

                                    if (blockCollision.isEmpty()) {
                                        continue;
                                    }

                                    final VoxelShape blockCollisionOffset = blockCollision.move((double)blockX, (double)blockY, (double)blockZ);

                                    if (!voxelShapeIntersectNoEmpty(blockCollisionOffset, aabb)) {
                                        continue;
                                    }

                                    if (predicate != null) {
                                        mutablePos.set(blockX, blockY, blockZ);
                                        if (!predicate.test(blockData, mutablePos)) {
                                            continue;
                                        }
                                    }

                                    if (checkOnly) {
                                        return true;
                                    } else {
                                        ret = true;
                                        intoVoxel.add(blockCollisionOffset);
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return ret;
    }

    public static boolean getEntityHardCollisions(final Level world, final Entity entity, AABB aabb,
                                                  final List<AABB> into, final int collisionFlags, final Predicate<Entity> predicate) {
        final boolean checkOnly = (collisionFlags & COLLISION_FLAG_CHECK_ONLY) != 0;

        boolean ret = false;

        // to comply with vanilla intersection rules, expand by -epsilon so that we only get stuff we definitely collide with.
        // Vanilla for hard collisions has this backwards, and they expand by +epsilon but this causes terrible problems
        // specifically with boat collisions.
        aabb = aabb.inflate(-COLLISION_EPSILON, -COLLISION_EPSILON, -COLLISION_EPSILON);
        final List<Entity> entities;
        if (entity != null && ((ChunkSystemEntity)entity).moonrise$isHardColliding()) {
            entities = world.getEntities(entity, aabb, predicate);
        } else {
            entities = ((ChunkSystemEntityGetter)world).moonrise$getHardCollidingEntities(entity, aabb, predicate);
        }

        for (int i = 0, len = entities.size(); i < len; ++i) {
            final Entity otherEntity = entities.get(i);

            if (otherEntity.isSpectator()) {
                continue;
            }

            if ((entity == null && otherEntity.canBeCollidedWith()) || (entity != null && entity.canCollideWith(otherEntity))) {
                if (checkOnly) {
                    return true;
                } else {
                    into.add(otherEntity.getBoundingBox());
                    ret = true;
                }
            }
        }

        return ret;
    }

    public static boolean getCollisions(final Level world, final Entity entity, final AABB aabb,
                                        final List<VoxelShape> intoVoxel, final List<AABB> intoAABB, final int collisionFlags,
                                        final BiPredicate<BlockState, BlockPos> blockPredicate,
                                        final Predicate<Entity> entityPredicate) {
        if ((collisionFlags & COLLISION_FLAG_CHECK_ONLY) != 0) {
            return getCollisionsForBlocksOrWorldBorder(world, entity, aabb, intoVoxel, intoAABB, collisionFlags, blockPredicate)
                || getEntityHardCollisions(world, entity, aabb, intoAABB, collisionFlags, entityPredicate);
        } else {
            return getCollisionsForBlocksOrWorldBorder(world, entity, aabb, intoVoxel, intoAABB, collisionFlags, blockPredicate)
                | getEntityHardCollisions(world, entity, aabb, intoAABB, collisionFlags, entityPredicate);
        }
    }

    public static final class LazyEntityCollisionContext extends EntityCollisionContext {

        private CollisionContext delegate;
        private boolean delegated;

        public LazyEntityCollisionContext(final Entity entity) {
            super(false, 0.0, null, null, entity);
        }

        public boolean isDelegated() {
            final boolean delegated = this.delegated;
            this.delegated = false;
            return delegated;
        }

        public CollisionContext getDelegate() {
            this.delegated = true;
            final Entity entity = this.getEntity();
            return this.delegate == null ? this.delegate = (entity == null ? CollisionContext.empty() : CollisionContext.of(entity)) : this.delegate;
        }

        @Override
        public boolean isDescending() {
            return this.getDelegate().isDescending();
        }

        @Override
        public boolean isAbove(final VoxelShape shape, final BlockPos pos, final boolean defaultValue) {
            return this.getDelegate().isAbove(shape, pos, defaultValue);
        }

        @Override
        public boolean isHoldingItem(final Item item) {
            return this.getDelegate().isHoldingItem(item);
        }

        @Override
        public boolean canStandOnFluid(final FluidState state, final FluidState fluidState) {
            return this.getDelegate().canStandOnFluid(state, fluidState);
        }
    }

    private CollisionUtil() {
        throw new RuntimeException();
    }
}
