package ca.spottedleaf.moonrise.mixin.getblock;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Higher priority to apply after Lithium mixin.world.inline_height.WorldMixin
@Mixin(value = Level.class, priority = 1100)
abstract class LevelMixin implements LevelAccessor, AutoCloseable {

    @Shadow
    public abstract DimensionType dimensionType();


    @Unique
    private DimensionType dimensionType;

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
        this.dimensionType = dimensionTypeHolder.value();
    }

    @Override
    public int getHeight() {
        return this.dimensionType.height();
    }

    @Override
    public int getMinBuildHeight() {
        return this.dimensionType.minY();
    }

    @Override
    public int getMaxBuildHeight() {
        final DimensionType dimensionType = this.dimensionType;

        return dimensionType.minY() + dimensionType.height();
    }

    @Override
    public int getMinSection() {
        return this.dimensionType.minY() >> 4;
    }

    @Override
    public int getMaxSection() {
        final DimensionType dimensionType = this.dimensionType;

        return (((dimensionType.minY() + dimensionType.height()) - 1) >> 4) + 1;
    }

    @Override
    public boolean isOutsideBuildHeight(final int y) {
        final DimensionType dimensionType = this.dimensionType;

        final int minBuildHeight = dimensionType.minY();
        final int maxBuildHeight = minBuildHeight + dimensionType.height();

        return y < minBuildHeight || y >= maxBuildHeight;
    }

    @Override
    public boolean isOutsideBuildHeight(final BlockPos blockPos) {
        return this.isOutsideBuildHeight(blockPos.getY());
    }

    @Override
    public int getSectionIndex(final int blockY) {
        return (blockY >> 4) - (this.dimensionType.minY() >> 4);
    }

    @Override
    public int getSectionIndexFromSectionY(final int sectionY) {
        return sectionY - (this.dimensionType.minY() >> 4);
    }

    @Override
    public int getSectionYFromSectionIndex(final int sectionIdx) {
        return sectionIdx + (this.dimensionType.minY() >> 4);
    }
}
