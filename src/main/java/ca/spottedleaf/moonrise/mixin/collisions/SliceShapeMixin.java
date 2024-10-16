package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import net.minecraft.world.phys.shapes.SliceShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SliceShape.class)
abstract class SliceShapeMixin {

    /**
     * @reason Hook into the root constructor to pass along init data to superclass.
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initState(final CallbackInfo ci) {
        ((CollisionVoxelShape)this).moonrise$initCache();
    }
}
