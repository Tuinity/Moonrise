package ca.spottedleaf.moonrise.mixin.getblock;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.getblock.GetBlockLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
        return this.minSection == Integer.MAX_VALUE ? WorldUtil.getMinSection(this) : this.minSection;
    }

    @Override
    public final int moonrise$getMaxSection() {
        return this.maxSection == Integer.MAX_VALUE ? WorldUtil.getMaxSection(this) : this.maxSection;
    }

    @Override
    public final int moonrise$getMinBuildHeight() {
        return this.minBuildHeight == Integer.MAX_VALUE ? this.getMinBuildHeight() : this.minBuildHeight;
    }

    @Override
    public final int moonrise$getMaxBuildHeight() {
        return this.maxBuildHeight == Integer.MAX_VALUE ? this.getMaxBuildHeight() : this.maxBuildHeight;
    }

    /**
     * @reason Init min/max section
     * @author Spottedleaf
     */
    @SuppressWarnings({"ConstantValue", "rawtypes"})
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void init(final CallbackInfo ci) {
        if ((Class) this.getClass() == ServerLevel.class || (Class) this.getClass() == ClientLevel.class) {
            this.minSection = WorldUtil.getMinSection(this);
            this.maxSection = WorldUtil.getMaxSection(this);
            this.minBuildHeight = this.getMinBuildHeight();
            this.maxBuildHeight = this.getMaxBuildHeight();
        } else {
            // Can't always cache for mods extending Level
            this.minSection = Integer.MAX_VALUE;
            this.maxSection = Integer.MAX_VALUE;
            this.minBuildHeight = Integer.MAX_VALUE;
            this.maxBuildHeight = Integer.MAX_VALUE;
        }
    }

    @Override
    public boolean isOutsideBuildHeight(final int y) {
        return y < this.moonrise$getMinBuildHeight() || y >= this.moonrise$getMaxBuildHeight();
    }

    @Override
    public boolean isOutsideBuildHeight(final BlockPos blockPos) {
        return this.isOutsideBuildHeight(blockPos.getY());
    }
}
