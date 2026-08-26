package ca.spottedleaf.moonrise.mixin.chunk_system;

import net.minecraft.world.level.levelgen.structure.structures.NetherFortressPieces;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

/**
 * Structure starts can be generated in parallel across different chunks. The
 * static {@code BRIDGE_PIECE_WEIGHTS}/{@code CASTLE_PIECE_WEIGHTS} arrays
 * contain mutable {@code PieceWeight} objects whose {@code placeCount} field
 * is reset and mutated during generation. Two nether fortresses generating
 * concurrently on different threads would corrupt each other's weight state,
 * causing premature termination (BridgeEndFiller) and drastically smaller
 * fortresses than vanilla.
 * <p>
 * Fix: every StartPiece gets its own copy of each PieceWeight, and the vanilla
 * {@code placeCount = 0} reset is discarded, so the static arrays are never
 * mutated after class loading.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.NetherFortressPieces$StartPiece")
abstract class NetherFortressPieces$StartPieceMixin {

    /**
     * @reason Replace shared weight objects with per-StartPiece copies so that
     *         parallel structure generation cannot corrupt the static arrays.
     * @author lisolaris
     */
    @Redirect(
        method = "<init>(Lnet/minecraft/util/RandomSource;II)V",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z"
        )
    )
    private boolean redirectWeightAdd(final List<NetherFortressPieces.PieceWeight> list, final Object obj) {
        // The target matches every List.add call in this constructor. Today there
        // are exactly two (the bridge/castle weight loops, both adding PieceWeight),
        // but guard against future non-weight additions instead of relying on that.
        if (!(obj instanceof NetherFortressPieces.PieceWeight piece)) {
            return ((List) list).add(obj); // raw: element type is erased at runtime; only reached for non-weight additions
        }
        return list.add(new NetherFortressPieces.PieceWeight(
            piece.pieceClass, piece.weight, piece.maxPlaceCount, piece.allowInRow
        ));
    }

    /**
     * @reason Discard the vanilla {@code piece.placeCount = 0} reset: the copies
     *         added by {@link #redirectWeightAdd} start at 0, and the shared
     *         template objects must never be mutated (they are read-only after
     *         class initialisation).
     * @author lisolaris
     */
    @Redirect(
        method = "<init>(Lnet/minecraft/util/RandomSource;II)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces$PieceWeight;placeCount:I",
            opcode = Opcodes.PUTFIELD
        )
    )
    private void redirectResetPlaceCount(final NetherFortressPieces.PieceWeight owner, final int value) {
        // no-op
    }
}
