package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.structure.StrongholdState;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

/**
 * Fixes a race condition when multiple strongholds are generated in parallel
 * via Moonrise's parallel STRUCTURE_STARTS scheduling.
 * <p>
 * Vanilla stores mutable generation state ({@code currentPieces}, {@code totalWeight},
 * {@code imposedPiece}, and {@code PieceWeight.placeCount}) in static fields.
 * Two strongholds generating concurrently corrupt each other's state, causing
 * abnormal piece layouts (missing portal rooms, wrong shapes, generation loops).
 * <p>
 * Fix: move all mutable per-generation state into a per-thread
 * {@link StrongholdState}, so that each worker thread sees its own isolated copy.
 * This preserves vanilla semantics exactly because structure generation is
 * synchronous pure computation (no yielding points), so a thread can never
 * interleave two structures; vanilla's serial {@code ConsecutiveExecutor} is
 * therefore equivalent to per-thread isolation.
 * <p>
 * Note: {@code PieceWeight} objects are still shared (the STRONGHOLD_PIECE_WEIGHTS
 * array elements), but they are treated as immutable templates. Per-structure
 * {@code placeCount} is tracked in an {@code IdentityHashMap} keyed by the shared
 * weight object, so different structures on different threads never conflict.
 */
@Mixin(StrongholdPieces.class)
abstract class StrongholdPiecesMixin {

    // -- Shadow: read-only access to the static template array -----------------

    @Shadow
    private static StrongholdPieces.PieceWeight[] STRONGHOLD_PIECE_WEIGHTS;

    // -- ThreadLocal per-generation state --------------------------------------
    //
    // The per-thread state lives in StrongholdState (ca.spottedleaf.moonrise
    // .patches.structure), a plain helper class shared by this mixin and
    // StrongholdPieces$StairsDownMixin. It must not live in this mixin: mixin
    // classes may not declare non-private static fields, and the transformer
    // rewrites references to mixin members against the target class of the
    // referencing mixin, so @Unique members cannot be shared across mixins that
    // target different classes.

    // -- resetPieces(): initialise ThreadLocal instead of static fields --------

    /**
     * @reason Per-thread state; initialise the ThreadLocal copy instead of
     *         resetting shared static state.
     * @author lisolaris
     */
    @Overwrite
    public static void resetPieces() {
        StrongholdState.LOCAL.get().reset(STRONGHOLD_PIECE_WEIGHTS);
    }

    // -- updatePieceWeight(): read from ThreadLocal ----------------------------

    /**
     * @reason Reads weight totals from the ThreadLocal state instead of the
     *         former static fields.
     * @author lisolaris
     */
    @Overwrite
    private static boolean updatePieceWeight() {
        final StrongholdState state = StrongholdState.LOCAL.get();
        boolean hasAnyPieces = false;
        state.totalWeight = 0;

        for (final StrongholdPieces.PieceWeight piece : state.currentPieces) {
            if (piece.maxPlaceCount > 0 && state.placeCounts.getOrDefault(piece, 0) < piece.maxPlaceCount) {
                hasAnyPieces = true;
            }
            state.totalWeight += piece.weight;
        }

        return hasAnyPieces;
    }

    // -- generatePieceFromSmallDoor(): redirect all static/field accesses ------

    /**
     * Redirect {@code GETSTATIC currentPieces} → ThreadLocal list.
     */
    @Redirect(
        method = "generatePieceFromSmallDoor",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces;currentPieces:Ljava/util/List;",
            opcode = Opcodes.GETSTATIC
        )
    )
    private static List<StrongholdPieces.PieceWeight> redirectGetCurrentPieces() {
        return StrongholdState.LOCAL.get().currentPieces;
    }

    /**
     * Redirect {@code GETSTATIC totalWeight} → ThreadLocal value.
     */
    @Redirect(
        method = "generatePieceFromSmallDoor",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces;totalWeight:I",
            opcode = Opcodes.GETSTATIC
        )
    )
    private static int redirectGetTotalWeight() {
        return StrongholdState.LOCAL.get().totalWeight;
    }

    /**
     * Redirect {@code GETSTATIC imposedPiece} → ThreadLocal value.
     */
    @Redirect(
        method = "generatePieceFromSmallDoor",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces;imposedPiece:Ljava/lang/Class;",
            opcode = Opcodes.GETSTATIC
        )
    )
    private static Class<? extends StrongholdPieces.StrongholdPiece> redirectGetImposedPiece() {
        return StrongholdState.LOCAL.get().imposedPiece;
    }

    /**
     * Redirect {@code PUTSTATIC imposedPiece} → ThreadLocal assignment.
     */
    @Redirect(
        method = "generatePieceFromSmallDoor",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces;imposedPiece:Ljava/lang/Class;",
            opcode = Opcodes.PUTSTATIC
        )
    )
    private static void redirectPutImposedPieceGen(final Class<?> value) {
        @SuppressWarnings("unchecked")
        final Class<? extends StrongholdPieces.StrongholdPiece> cast =
            (Class<? extends StrongholdPieces.StrongholdPiece>) value;
        StrongholdState.LOCAL.get().imposedPiece = cast;
    }

    /**
     * Redirect {@code GETFIELD PieceWeight.placeCount} → ThreadLocal map.
     */
    @Redirect(
        method = "generatePieceFromSmallDoor",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces$PieceWeight;placeCount:I",
            opcode = Opcodes.GETFIELD
        )
    )
    private static int redirectGetPlaceCount(final StrongholdPieces.PieceWeight owner) {
        return StrongholdState.LOCAL.get().placeCounts.getOrDefault(owner, 0);
    }

    /**
     * Redirect {@code PUTFIELD PieceWeight.placeCount} → ThreadLocal map write.
     */
    @Redirect(
        method = "generatePieceFromSmallDoor",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces$PieceWeight;placeCount:I",
            opcode = Opcodes.PUTFIELD
        )
    )
    private static void redirectPutPlaceCount(final StrongholdPieces.PieceWeight owner, final int value) {
        StrongholdState.LOCAL.get().placeCounts.put(owner, value);
    }

    /**
     * Redirect {@code INVOKEVIRTUAL PieceWeight.doPlace(I)Z} so that
     * {@code placeCount} is read from the ThreadLocal map instead of the
     * (now-unused) instance field. Handles the anonymous-subclass depth
     * constraints for {@code Library} ({@code depth > 4}) and
     * {@code PortalRoom} ({@code depth > 5}).
     */
    @Redirect(
        method = "generatePieceFromSmallDoor",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces$PieceWeight;doPlace(I)Z"
        )
    )
    private static boolean redirectDoPlace(final StrongholdPieces.PieceWeight owner, final int depth) {
        final int placeCount = StrongholdState.LOCAL.get().placeCounts.getOrDefault(owner, 0);
        final boolean base = owner.maxPlaceCount == 0 || placeCount < owner.maxPlaceCount;

        // Handle anonymous-subclass depth constraints (the STRONGHOLD_PIECE_WEIGHTS
        // entries for Library and PortalRoom are anonymous subclasses whose
        // overrides are bypassed by this redirect).
        if (owner.pieceClass == StrongholdPieces.Library.class) {
            return base && depth > 4;
        }
        if (owner.pieceClass == StrongholdPieces.PortalRoom.class) {
            return base && depth > 5;
        }
        return base;
    }

    /**
     * Redirect {@code INVOKEVIRTUAL PieceWeight.isValid()Z} → read
     * {@code placeCount} from the ThreadLocal map.
     */
    @Redirect(
        method = "generatePieceFromSmallDoor",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces$PieceWeight;isValid()Z"
        )
    )
    private static boolean redirectIsValid(final StrongholdPieces.PieceWeight owner) {
        final int placeCount = StrongholdState.LOCAL.get().placeCounts.getOrDefault(owner, 0);
        return owner.maxPlaceCount == 0 || placeCount < owner.maxPlaceCount;
    }
}
