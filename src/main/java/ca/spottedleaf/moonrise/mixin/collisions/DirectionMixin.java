package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.util.CollisionDirection;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Direction.class)
public abstract class DirectionMixin implements CollisionDirection {

    @Unique
    private static final int RANDOM_OFFSET = 2017601568;

    @Shadow
    @Final
    private static Direction[] VALUES;

    @Shadow
    @Final
    private Vec3i normal;

    @Shadow
    @Final
    private int oppositeIndex;

    @Shadow
    public static Direction from3DDataValue(int i) {
        return null;
    }

    @Unique
    private Direction opposite;

    @Unique
    private Quaternionf rotation;

    @Unique
    private int id = HashCommon.murmurHash3(((Enum)(Object)this).ordinal() + 1);

    @Unique
    private int stepX;

    @Unique
    private int stepY;

    @Unique
    private int stepZ;

    /**
     * @reason Initialise caches before class init is done.
     * @author Spottedleaf
     */
    @Inject(
            method = "<clinit>",
            at = @At(
                    value = "RETURN"
            )
    )
    private static void initCaches(final CallbackInfo ci) {
        for (final Direction direction : VALUES) {
            ((DirectionMixin)(Object)direction).opposite = from3DDataValue(((DirectionMixin)(Object)direction).oppositeIndex);
            ((DirectionMixin)(Object)direction).rotation = ((DirectionMixin)(Object)direction).getRotationUncached();
            ((DirectionMixin)(Object)direction).id = HashCommon.murmurHash3(direction.ordinal() + RANDOM_OFFSET);
            ((DirectionMixin)(Object)direction).stepX = ((DirectionMixin)(Object)direction).normal.getX();
            ((DirectionMixin)(Object)direction).stepY = ((DirectionMixin)(Object)direction).normal.getY();
            ((DirectionMixin)(Object)direction).stepZ = ((DirectionMixin)(Object)direction).normal.getZ();
        }
    }

    /**
     * @reason Use simple field access
     * @author Spottedleaf
     */
    @Overwrite
    public Direction getOpposite() {
        return this.opposite;
    }

    @Unique
    private Quaternionf getRotationUncached() {
        switch ((Direction)(Object)this) {
            case DOWN: {
                return new Quaternionf().rotationX(3.1415927F);
            }
            case UP: {
                return new Quaternionf();
            }
            case NORTH: {
                return new Quaternionf().rotationXYZ(1.5707964F, 0.0F, 3.1415927F);
            }
            case SOUTH: {
                return new Quaternionf().rotationX(1.5707964F);
            }
            case WEST: {
                return new Quaternionf().rotationXYZ(1.5707964F, 0.0F, 1.5707964F);
            }
            case EAST: {
                return new Quaternionf().rotationXYZ(1.5707964F, 0.0F, -1.5707964F);
            }
            default: {
                throw new IllegalStateException();
            }
        }
    }

    /**
     * @reason Use clone of cache instead of computing the rotation
     * @author Spottedleaf
     */
    @Overwrite
    public Quaternionf getRotation() {
        try {
            return (Quaternionf)this.rotation.clone();
        } catch (final CloneNotSupportedException ex) {
            throw new InternalError(ex);
        }
    }

    /**
     * @reason Avoid extra memory indirection
     * @author Spottedleaf
     */
    @Overwrite
    public int getStepX() {
        return this.stepX;
    }

    /**
     * @reason Avoid extra memory indirection
     * @author Spottedleaf
     */
    @Overwrite
    public int getStepY() {
        return this.stepY;
    }

    /**
     * @reason Avoid extra memory indirection
     * @author Spottedleaf
     */
    @Overwrite
    public int getStepZ() {
        return this.stepZ;
    }

    @Override
    public int uniqueId() {
        return this.id;
    }
}
