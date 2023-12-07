package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArrayVoxelShape.class)
public abstract class ArrayVoxelShapeMixin {

    /**
     * @reason Hook into the root constructor to pass along init data to superclass.
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
        ((CollisionVoxelShape)this).moonrise$initCache();
    }
}
