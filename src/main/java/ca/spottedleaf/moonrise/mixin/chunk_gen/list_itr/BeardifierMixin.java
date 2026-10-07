package ca.spottedleaf.moonrise.mixin.chunk_gen.list_itr;

import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(Beardifier.class)
abstract class BeardifierMixin {

    @Shadow
    private static float getBeardContribution(final int dx, final int dy, final int dz, final int yToGround) {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    @Shadow
    private static float getBuryContribution(final float dx, final float dy, final float dz) {
        throw new UnsupportedOperationException("Implemented via mixin");
    }


    @Unique
    private Beardifier.Rigid[] piecesArray;

    @Unique
    private JigsawJunction[] junctionsArray;

    /**
     * @reason Initialise arrays for iteration
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void initArrays(final List<Beardifier.Rigid> pieces, final List<JigsawJunction> junctions,
                            final BoundingBox affectedBox, final CallbackInfo ci) {
        this.piecesArray = pieces.toArray(new Beardifier.Rigid[0]);
        this.junctionsArray = junctions.toArray(new JigsawJunction[0]);
    }

    /**
     * @reason Avoid indirection through the list iterator and use arrays
     * @author Spottedleaf
     */
    @Overwrite
    private float sampleValueUnchecked(final int blockX, final int blockY, final int blockZ) {
        float noiseValue = 0.0F;

        for (final Beardifier.Rigid rigid : this.piecesArray) {
            final BoundingBox box = rigid.box();
            final int groundLevelDelta = rigid.groundLevelDelta();
            final int dx = Math.max(0, Math.max(box.minX() - blockX, blockX - box.maxX()));
            final int dz = Math.max(0, Math.max(box.minZ() - blockZ, blockZ - box.maxZ()));
            final int groundY = box.minY() + groundLevelDelta;
            final int dyToGround = blockY - groundY;

            switch (rigid.terrainAdjustment()) {
                case NONE: {
                    continue;
                }
                case BURY: {
                    noiseValue += getBuryContribution((float)dx, (float)dyToGround / 2.0f, (float)dz);
                    continue;
                }
                case BEARD_THIN: {
                    noiseValue += getBeardContribution(dx, dyToGround, dz, dyToGround) * 0.8F;
                    continue;
                }
                case BEARD_BOX: {
                    final int dy = Math.max(0, Math.max(groundY - blockY, blockY - box.maxY()));
                    noiseValue += getBeardContribution(dx, dy, dz, dyToGround) * 0.8F;
                    continue;
                }
                case ENCAPSULATE: {
                    final int dy = Math.max(0, Math.max(box.minY() - blockY, blockY - box.maxY()));
                    noiseValue += getBuryContribution((float)dx / 2.0f, (float)dy / 2.0f, (float)dz / 2.0f) * 0.8F;
                    continue;
                }

                default: {
                    throw new IllegalStateException();
                }
            }
        }

        for (final JigsawJunction junction : this.junctionsArray) {
            final int dx = blockX - junction.getSourceX();
            final int dy = blockY - junction.getSourceGroundY();
            final int dz = blockZ - junction.getSourceZ();
            noiseValue += getBeardContribution(dx, dy, dz, dy) * 0.4F;
        }

        return noiseValue;
    }
}
