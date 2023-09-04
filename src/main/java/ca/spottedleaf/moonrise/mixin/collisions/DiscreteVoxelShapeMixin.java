package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.Arrays;

@Mixin(DiscreteVoxelShape.class)
public abstract class DiscreteVoxelShapeMixin implements CollisionDiscreteVoxelShape {

    // ignore race conditions here: the shape is static, so it doesn't matter
    @Unique
    private CachedShapeData cachedShapeData;

    @Override
    public final CachedShapeData getOrCreateCachedShapeData() {
        if (this.cachedShapeData != null) {
            return this.cachedShapeData;
        }

        final DiscreteVoxelShape discreteVoxelShape = (DiscreteVoxelShape)(Object)this;

        final int sizeX = discreteVoxelShape.getXSize();
        final int sizeY = discreteVoxelShape.getYSize();
        final int sizeZ = discreteVoxelShape.getZSize();

        final int maxIndex = sizeX * sizeY * sizeZ; // exclusive

        final int longsRequired = (maxIndex + (Long.SIZE - 1)) >>> 6;
        long[] voxelSet;

        final boolean isEmpty = discreteVoxelShape.isEmpty();

        if (discreteVoxelShape instanceof BitSetDiscreteVoxelShape bitsetShape) {
            voxelSet = bitsetShape.storage.toLongArray();
            if (voxelSet.length < longsRequired) {
                // happens when the later long values are 0L, so we need to resize
                voxelSet = Arrays.copyOf(voxelSet, longsRequired);
            }
        } else {
            voxelSet = new long[longsRequired];
            if (!isEmpty) {
                final int mulX = sizeZ * sizeY;
                for (int x = 0; x < sizeX; ++x) {
                    for (int y = 0; y < sizeY; ++y) {
                        for (int z = 0; z < sizeZ; ++z) {
                            if (discreteVoxelShape.isFull(x, y, z)) {
                                // index = z + y*size_z + x*(size_z*size_y)
                                final int index = z + y * sizeZ + x * mulX;

                                voxelSet[index >>> 6] |= 1L << index;
                            }
                        }
                    }
                }
            }
        }

        final boolean hasSingleAABB = sizeX == 1 && sizeY == 1 && sizeZ == 1 && !isEmpty && discreteVoxelShape.isFull(0, 0, 0);

        final int minFullX = discreteVoxelShape.firstFull(Direction.Axis.X);
        final int minFullY = discreteVoxelShape.firstFull(Direction.Axis.Y);
        final int minFullZ = discreteVoxelShape.firstFull(Direction.Axis.Z);

        final int maxFullX = discreteVoxelShape.lastFull(Direction.Axis.X);
        final int maxFullY = discreteVoxelShape.lastFull(Direction.Axis.Y);
        final int maxFullZ = discreteVoxelShape.lastFull(Direction.Axis.Z);

        return this.cachedShapeData = new CachedShapeData(
                sizeX, sizeY, sizeZ, voxelSet,
                minFullX, minFullY, minFullZ,
                maxFullX, maxFullY, maxFullZ,
                isEmpty, hasSingleAABB
        );
    }
}
