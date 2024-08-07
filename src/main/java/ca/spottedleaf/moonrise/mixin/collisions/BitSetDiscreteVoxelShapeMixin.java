package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.FlatBitsetUtil;
import ca.spottedleaf.moonrise.common.util.MixinWorkarounds;
import ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.List;

@Mixin(BitSetDiscreteVoxelShape.class)
abstract class BitSetDiscreteVoxelShapeMixin extends DiscreteVoxelShape {

    @Unique
    private static final boolean DEBUG_ALL_BOXES = false;

    protected BitSetDiscreteVoxelShapeMixin(int i, int j, int k) {
        super(i, j, k);
    }

    @Unique
    private static void forAllBoxesVanilla(DiscreteVoxelShape discreteVoxelShape, DiscreteVoxelShape.IntLineConsumer intLineConsumer, boolean bl) {
        BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(discreteVoxelShape);

        for(int i = 0; i < bitSetDiscreteVoxelShape.getYSize(); ++i) {
            for(int j = 0; j < bitSetDiscreteVoxelShape.getXSize(); ++j) {
                int k = -1;

                for(int l = 0; l <= bitSetDiscreteVoxelShape.getZSize(); ++l) {
                    if (bitSetDiscreteVoxelShape.isFullWide(j, i, l)) {
                        if (bl) {
                            if (k == -1) {
                                k = l;
                            }
                        } else {
                            intLineConsumer.consume(j, i, l, j + 1, i + 1, l + 1);
                        }
                    } else if (k != -1) {
                        int m = j;
                        int n = i;
                        bitSetDiscreteVoxelShape.clearZStrip(k, l, j, i);

                        while(bitSetDiscreteVoxelShape.isZStripFull(k, l, m + 1, i)) {
                            bitSetDiscreteVoxelShape.clearZStrip(k, l, m + 1, i);
                            ++m;
                        }

                        while(bitSetDiscreteVoxelShape.isXZRectangleFull(j, m + 1, k, l, n + 1)) {
                            for(int o = j; o <= m; ++o) {
                                bitSetDiscreteVoxelShape.clearZStrip(k, l, o, n + 1);
                            }

                            ++n;
                        }

                        intLineConsumer.consume(j, i, k, m + 1, n + 1, l);
                        k = -1;
                    }
                }
            }
        }

    }

    @Unique
    private static void chkForAll(final DiscreteVoxelShape shape, final boolean mergeAdjacent) {
        record Range(int sx, int sy, int sz, int ex, int ey, int ez) {}

        if (new Throwable().getStackTrace()[2].getMethodName().contains("chkForAll")) {
            return;
        }

        final List<Range> vanillaRanges = new ArrayList<>();
        final List<Range> moonriseRanges = new ArrayList<>();

        forAllBoxesVanilla(shape, (x1, y1, z1, x2, y2, z2) -> {
            vanillaRanges.add(new Range(x1, y1, z1, x2, y2, z2));
        }, mergeAdjacent);

        forAllBoxes(shape, (x1, y1, z1, x2, y2, z2) -> {
            moonriseRanges.add(new Range(x1, y1, z1, x2, y2, z2));
        }, mergeAdjacent);

        if (!vanillaRanges.equals(moonriseRanges)) {
            forAllBoxesVanilla(shape, (x1, y1, z1, x2, y2, z2) -> {}, mergeAdjacent);
            forAllBoxes(shape, (x1, y1, z1, x2, y2, z2) -> {}, mergeAdjacent);

            throw new IllegalStateException();
        }
    }

    /**
     * @reason avoid creating temporary shape, interact directly with the bitset
     * @author Spottedleaf
     */
    @Overwrite
    public static void forAllBoxes(final DiscreteVoxelShape shape, final DiscreteVoxelShape.IntLineConsumer consumer, final boolean mergeAdjacent) {
        if (DEBUG_ALL_BOXES) {
            chkForAll(shape, mergeAdjacent);
        }
        // called with the shape of a VoxelShape, so we can expect the cache to exist
        final CachedShapeData cache = ((CollisionDiscreteVoxelShape)shape).moonrise$getOrCreateCachedShapeData();

        final int sizeX = cache.sizeX();
        final int sizeY = cache.sizeY();
        final int sizeZ = cache.sizeZ();

        int indexX;
        int indexY = 0;
        int indexZ;

        int incY = sizeZ;
        int incX = sizeZ*sizeY;

        long[] bitset = cache.voxelSet();

        // index = z + y*size_z + x*(size_z*size_y)

        if (!mergeAdjacent) {
            // due to the odd selection of loop order (which does affect behavior, unfortunately) we can't simply
            // increment an index in the Z loop, and have to perform this trash (keeping track of 3 counters) to avoid
            // the multiplication
            for (int y = 0; y < sizeY; ++y, indexY += incY) {
                indexX = indexY;
                for (int x = 0; x < sizeX; ++x, indexX += incX) {
                    indexZ = indexX;
                    for (int z = 0; z < sizeZ; ++z, ++indexZ) {
                        if ((bitset[indexZ >>> 6] & (1L << indexZ)) != 0L) {
                            consumer.consume(x, y, z, x + 1, y + 1, z + 1);
                        }
                    }
                }
            }
        } else {
            // same notes about loop order as the above
            // this branch is actually important to optimise, as it affects uncached toAabbs() (which affects optimize())

            // only clone when we may write to it
            bitset = MixinWorkarounds.clone(bitset);

            for (int y = 0; y < sizeY; ++y, indexY += incY) {
                indexX = indexY;
                for (int x = 0; x < sizeX; ++x, indexX += incX) {
                    for (int zIdx = indexX, endIndex = indexX + sizeZ; zIdx < endIndex;) {
                        final int firstSetZ = FlatBitsetUtil.firstSet(bitset, zIdx, endIndex);

                        if (firstSetZ == -1) {
                            break;
                        }

                        int lastSetZ = FlatBitsetUtil.firstClear(bitset, firstSetZ, endIndex);
                        if (lastSetZ == -1) {
                            lastSetZ = endIndex;
                        }

                        FlatBitsetUtil.clearRange(bitset, firstSetZ, lastSetZ);

                        // try to merge neighbouring on the X axis
                        int endX = x + 1; // exclusive
                        for (int neighbourIdxStart = firstSetZ + incX, neighbourIdxEnd = lastSetZ + incX;
                             endX < sizeX && FlatBitsetUtil.isRangeSet(bitset, neighbourIdxStart, neighbourIdxEnd);
                             neighbourIdxStart += incX, neighbourIdxEnd += incX) {

                            ++endX;
                            FlatBitsetUtil.clearRange(bitset, neighbourIdxStart, neighbourIdxEnd);
                        }

                        // try to merge neighbouring on the Y axis

                        int endY; // exclusive
                        int firstSetZY, lastSetZY;
                        y_merge:
                        for (endY = y + 1, firstSetZY = firstSetZ + incY, lastSetZY = lastSetZ + incY; endY < sizeY;
                             firstSetZY += incY, lastSetZY += incY) {

                            // test the whole XZ range
                            for (int testX = x, start = firstSetZY, end = lastSetZY; testX < endX;
                                 ++testX, start += incX, end += incX) {
                                if (!FlatBitsetUtil.isRangeSet(bitset, start, end)) {
                                    break y_merge;
                                }
                            }

                            ++endY;

                            // passed, so we can clear it
                            for (int testX = x, start = firstSetZY, end = lastSetZY; testX < endX;
                                 ++testX, start += incX, end += incX) {
                                FlatBitsetUtil.clearRange(bitset, start, end);
                            }
                        }

                        consumer.consume(x, y, firstSetZ - indexX, endX, endY, lastSetZ - indexX);
                        zIdx = lastSetZ;
                    }
                }
            }
        }
    }
}
