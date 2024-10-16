package ca.spottedleaf.moonrise.mixin.getblock;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Higher priority to apply after Lithium mixin.world.inline_height.WorldMixin
@Mixin(value = Level.class, priority = 1100)
abstract class LevelMixin implements LevelAccessor, AutoCloseable {

    @Unique
    private int minY;

    @Unique
    private int height;

    @Unique
    private int maxY;

    @Unique
    private int minSectionY;

    @Unique
    private int maxSectionY;

    @Unique
    private int sectionsCount;

    /**
     * @reason Init min/max section
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "CTOR_HEAD"
        )
    )
    private void init(final CallbackInfo ci,
                      @Local(ordinal = 0, argsOnly = true) final Holder<DimensionType> dimensionTypeHolder) {
        final DimensionType dimType = dimensionTypeHolder.value();
        this.minY = dimType.minY();
        this.height = dimType.height();
        this.maxY = this.minY + this.height - 1;
        this.minSectionY = this.minY >> 4;
        this.maxSectionY = this.maxY >> 4;
        this.sectionsCount = this.maxSectionY - this.minSectionY + 1;
    }

    @Override
    public int getMinY() {
        return this.minY;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public int getMaxY() {
        return this.maxY;
    }

    @Override
    public int getSectionsCount() {
        return this.sectionsCount;
    }

    @Override
    public int getMinSectionY() {
        return this.minSectionY;
    }

    @Override
    public int getMaxSectionY() {
        return this.maxSectionY;
    }

    @Override
    public boolean isInsideBuildHeight(final int blockY) {
        return blockY >= this.minY && blockY <= this.maxY;
    }

    @Override
    public boolean isOutsideBuildHeight(final BlockPos pos) {
        return this.isOutsideBuildHeight(pos.getY());
    }

    @Override
    public boolean isOutsideBuildHeight(final int blockY) {
        return blockY < this.minY || blockY > this.maxY;
    }

    @Override
    public int getSectionIndex(final int blockY) {
        return (blockY >> 4) - this.minSectionY;
    }

    @Override
    public int getSectionIndexFromSectionY(final int sectionY) {
        return sectionY - this.minSectionY;
    }

    @Override
    public int getSectionYFromSectionIndex(final int sectionIdx) {
        return sectionIdx + this.minSectionY;
    }
}
