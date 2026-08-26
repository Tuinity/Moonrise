package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.VoxelShapeInternPool;
import ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.Arrays;

@Mixin(DiscreteVoxelShape.class)
abstract class DiscreteVoxelShapeMixin implements CollisionDiscreteVoxelShape {

    // Benign race: the geometry is immutable, so concurrent initialisation produces equivalent data.
    @Unique
    private CachedShapeData cachedShapeData;

    @Override
    public final CachedShapeData moonrise$getOrCreateCachedShapeData() {
        final CachedShapeData cached = this.cachedShapeData;
        if (cached != null) {
            return cached;
        }
        return this.moonrise$createCachedShapeData(true);
    }

    @Override
    public final CachedShapeData moonrise$getOrCreateCachedShapeData(final boolean internRetainedGeometry) {
        final CachedShapeData cached = this.cachedShapeData;
        if (cached != null) {
            if (!internRetainedGeometry) {
                return cached;
            }
            // A transient shape may later become retained. Promote its cached data to the
            // shared pool once.
            return this.cachedShapeData = VoxelShapeInternPool.internShapeData(cached);
        }
        return this.moonrise$createCachedShapeData(internRetainedGeometry);
    }

    @Unique
    private CachedShapeData moonrise$createCachedShapeData(final boolean internRetainedGeometry) {
        final DiscreteVoxelShape discreteVoxelShape = (DiscreteVoxelShape)(Object)this;

        final int sizeX = discreteVoxelShape.getXSize();
        final int sizeY = discreteVoxelShape.getYSize();
        final int sizeZ = discreteVoxelShape.getZSize();
        final int voxelCount = sizeX * sizeY * sizeZ;
        final int longsRequired = (voxelCount + (Long.SIZE - 1)) >>> 6;

        final boolean isEmpty = discreteVoxelShape.isEmpty();
        final long[] voxelSet;

        if (discreteVoxelShape instanceof BitSetDiscreteVoxelShape bitsetShape) {
            final long[] stored = bitsetShape.storage.toLongArray();
            // BitSet#toLongArray omits trailing zero words.
            voxelSet = stored.length < longsRequired ? Arrays.copyOf(stored, longsRequired) : stored;
        } else {
            voxelSet = new long[longsRequired];
            if (!isEmpty) {
                final int mulX = sizeZ * sizeY;
                for (int x = 0; x < sizeX; ++x) {
                    for (int y = 0; y < sizeY; ++y) {
                        for (int z = 0; z < sizeZ; ++z) {
                            if (discreteVoxelShape.isFull(x, y, z)) {
                                // index = z + y*sizeZ + x*(sizeZ*sizeY)
                                final int index = z + y * sizeZ + x * mulX;
                                voxelSet[index >>> 6] |= 1L << index;
                            }
                        }
                    }
                }
            }
        }

        final boolean hasSingleAABB = sizeX == 1 && sizeY == 1 && sizeZ == 1 && !isEmpty && (voxelSet[0] & 1L) != 0L;

        final int minFullX = discreteVoxelShape.firstFull(Direction.Axis.X);
        final int minFullY = discreteVoxelShape.firstFull(Direction.Axis.Y);
        final int minFullZ = discreteVoxelShape.firstFull(Direction.Axis.Z);

        final int maxFullX = discreteVoxelShape.lastFull(Direction.Axis.X);
        final int maxFullY = discreteVoxelShape.lastFull(Direction.Axis.Y);
        final int maxFullZ = discreteVoxelShape.lastFull(Direction.Axis.Z);

        final CachedShapeData data = new CachedShapeData(
                sizeX, sizeY, sizeZ, voxelSet,
                minFullX, minFullY, minFullZ,
                maxFullX, maxFullY, maxFullZ,
                isEmpty, hasSingleAABB
        );

        final CachedShapeData result = internRetainedGeometry ? VoxelShapeInternPool.internShapeData(data) : data;
        return this.cachedShapeData = result;
    }
}
