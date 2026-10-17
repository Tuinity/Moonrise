package ca.spottedleaf.moonrise.mixin.chunk_system;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(StrongholdStructure.class)
abstract class StrongholdStructureMixin {

    @Unique
    private static final Object GENERATION_LOCK = new Object();

    /**
     * @reason StrongholdPieces keeps its piece generation state in static fields, so concurrent
     *         generation (STRUCTURE_STARTS is parallel capable) corrupts the generated strongholds.
     * @author jpenilla
     */
    @WrapMethod(
        method = "generatePieces"
    )
    private static void lockGeneration(final StructurePiecesBuilder builder, final Structure.GenerationContext context,
                                       final Operation<Void> original) {
        synchronized (GENERATION_LOCK) {
            original.call(builder, context);
        }
    }
}
