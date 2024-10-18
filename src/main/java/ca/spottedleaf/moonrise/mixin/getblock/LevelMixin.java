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
    private int height;

    @Unique
    private int minBuildHeight;

    @Unique
    private int maxBuildHeight;

    @Unique
    private int minSection;

    @Unique
    private int maxSection;

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
        this.height = dimType.height();
        this.minBuildHeight = dimType.minY();
        this.maxBuildHeight = this.minBuildHeight + this.height;
        this.minSection = this.minBuildHeight >> 4;
        this.maxSection = ((this.maxBuildHeight - 1) >> 4) + 1;
        this.sectionsCount = this.maxSection - this.minSection;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public int getMinBuildHeight() {
        return this.minBuildHeight;
    }

    @Override
    public int getMaxBuildHeight() {
        return this.maxBuildHeight;
    }

    @Override
    public int getMinSection() {
        return this.minSection;
    }

    @Override
    public int getMaxSection() {
        return this.maxSection;
    }

    @Override
    public boolean isOutsideBuildHeight(final int y) {
        return y < this.minBuildHeight || y >= this.maxBuildHeight;
    }

    @Override
    public boolean isOutsideBuildHeight(final BlockPos blockPos) {
        return this.isOutsideBuildHeight(blockPos.getY());
    }

    @Override
    public int getSectionIndex(final int blockY) {
        return (blockY >> 4) - this.minSection;
    }

    @Override
    public int getSectionIndexFromSectionY(final int sectionY) {
        return sectionY - this.minSection;
    }

    @Override
    public int getSectionYFromSectionIndex(final int sectionIdx) {
        return sectionIdx + this.minSection;
    }

    @Override
    public int getSectionsCount() {
        return this.sectionsCount;
    }
}
