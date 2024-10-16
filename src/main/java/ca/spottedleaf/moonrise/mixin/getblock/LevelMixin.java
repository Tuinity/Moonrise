package ca.spottedleaf.moonrise.mixin.getblock;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.getblock.GetBlockLevel;
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
abstract class LevelMixin implements GetBlockLevel, LevelAccessor, AutoCloseable {

    @Unique
    private int minSection;

    @Unique
    private int maxSection;

    @Unique
    private int minBuildHeight;

    @Unique
    private int maxBuildHeight;

    @Override
    public final int moonrise$getMinSection() {
        return this.minSection;
    }

    @Override
    public final int moonrise$getMaxSection() {
        return this.maxSection;
    }

    @Override
    public final int moonrise$getMinBuildHeight() {
        return this.minBuildHeight;
    }

    @Override
    public final int moonrise$getMaxBuildHeight() {
        return this.maxBuildHeight;
    }

    /**
     * @reason Init min/max section
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void init(final CallbackInfo ci, @Local(argsOnly = true) final Holder<DimensionType> dimensionType) {
        this.minSection = WorldUtil.getMinSection(dimensionType.value());
        this.maxSection = WorldUtil.getMaxSection(dimensionType.value());
        this.minBuildHeight = dimensionType.value().minY();
        this.maxBuildHeight = dimensionType.value().minY() + dimensionType.value().height();
    }

    @Override
    public boolean isOutsideBuildHeight(final int y) {
        return y < this.minBuildHeight || y >= this.maxBuildHeight;
    }

    @Override
    public boolean isOutsideBuildHeight(final BlockPos blockPos) {
        return this.isOutsideBuildHeight(blockPos.getY());
    }
}
