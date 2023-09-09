package ca.spottedleaf.moonrise.mixin.collisions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockPos.class)
public abstract class BlockPosMixin extends Vec3i {

    public BlockPosMixin(int i, int j, int k) {
        super(i, j, k);
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos above() {
        return new BlockPos(this.getX(), this.getY() + 1, this.getZ());
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos above(final int distance) {
        return distance == 0 ? (BlockPos)(Object)this : new BlockPos(this.getX(), this.getY() + distance, this.getZ());
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos below() {
        return new BlockPos(this.getX(), this.getY() - 1, this.getZ());
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos below(final int distance) {
        return distance == 0 ? (BlockPos)(Object)this : new BlockPos(this.getX(), this.getY() - distance, this.getZ());
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos north() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() - 1);
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos north(final int distance) {
        return distance == 0 ? (BlockPos)(Object)this : new BlockPos(this.getX(), this.getY(), this.getZ() - distance);
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos south() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() + 1);
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos south(final int distance) {
        return distance == 0 ? (BlockPos)(Object)this : new BlockPos(this.getX(), this.getY(), this.getZ() + distance);
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos west() {
        return new BlockPos(this.getX() - 1, this.getY(), this.getZ());
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos west(final int distance) {
        return distance == 0 ? (BlockPos)(Object)this : new BlockPos(this.getX() - distance, this.getY(), this.getZ());
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos east() {
        return new BlockPos(this.getX() + 1, this.getY(), this.getZ());
    }

    /**
     * @reason https://bugs.mojang.com/browse/MC-136025
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public BlockPos east(final int distance) {
        return distance == 0 ? (BlockPos)(Object)this : new BlockPos(this.getX() + distance, this.getY(), this.getZ());
    }
}
