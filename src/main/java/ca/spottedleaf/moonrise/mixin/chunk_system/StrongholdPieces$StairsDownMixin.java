package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.structure.StrongholdState;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the static {@code imposedPiece} write in {@code StairsDown.addChildren()}
 * to the per-thread {@link StrongholdState}, preventing cross-structure
 * contamination during parallel generation.
 * <p>
 * {@code StairsDown} (the direct superclass of {@code StartPiece}) forces generation
 * of a {@code FiveCrossing} as the next piece after the start room by setting
 * {@code imposedPiece = FiveCrossing.class}. This write must go to the ThreadLocal
 * so that concurrent strongholds do not interfere.
 *
 * @see StrongholdPiecesMixin
 */
@Mixin(StrongholdPieces.StairsDown.class)
abstract class StrongholdPieces$StairsDownMixin {

    /**
     * @reason Per-thread state; write to ThreadLocal instead of the shared
     *         static field.
     * @author lisolaris
     */
    @Redirect(
        method = "addChildren",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces;imposedPiece:Ljava/lang/Class;",
            opcode = Opcodes.PUTSTATIC
        )
    )
    private void redirectPutImposedPieceStairsDown(final Class<?> value) {
        @SuppressWarnings("unchecked")
        final Class<? extends StrongholdPieces.StrongholdPiece> cast =
            (Class<? extends StrongholdPieces.StrongholdPiece>) value;
        StrongholdState.LOCAL.get().imposedPiece = cast;
    }
}
