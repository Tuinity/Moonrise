package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.VoxelShapeInternPool;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionArrayVoxelShape;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArrayVoxelShape.class)
abstract class ArrayVoxelShapeMixin implements CollisionArrayVoxelShape {

    @Shadow(aliases = "xPoints")
    @Final
    @Mutable
    private DoubleList xs;

    @Shadow(aliases = "yPoints")
    @Final
    @Mutable
    private DoubleList ys;

    @Shadow(aliases = "zPoints")
    @Final
    @Mutable
    private DoubleList zs;

    /**
     * @reason Initialise collision caches without interning transient geometry.
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;Lit/unimi/dsi/fastutil/doubles/DoubleList;Lit/unimi/dsi/fastutil/doubles/DoubleList;Lit/unimi/dsi/fastutil/doubles/DoubleList;)V",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initState(final DiscreteVoxelShape discreteVoxelShape,
                           final DoubleList xList, final DoubleList yList, final DoubleList zList,
                           final CallbackInfo ci) {
        // Generic construction can be runtime/transient (entity AABBs, joins, moves).
        // Retained interning is performed later only if a BlockState cache keeps this shape.
        ((CollisionVoxelShape)this).moonrise$initCache();
    }

    @Override
    public final void moonrise$internRetainedCoordinates() {
        this.xs = VoxelShapeInternPool.internCoordinateList(this.xs);
        this.ys = VoxelShapeInternPool.internCoordinateList(this.ys);
        this.zs = VoxelShapeInternPool.internCoordinateList(this.zs);
    }
}
