package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.SliceShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SliceShape.class)
public abstract class SliceShapeMixin {

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
    private void initState(final VoxelShape parent, final Direction.Axis forAxis, final int forIndex, final CallbackInfo ci) {
        ((CollisionVoxelShape)this).initCache();
    }
}
