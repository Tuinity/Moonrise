package ca.spottedleaf.moonrise.patches.structure;

import com.google.common.collect.Lists;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-thread generation state for {@link StrongholdPieces}.
 * <p>
 * Vanilla keeps the mutable generation state ({@code currentPieces},
 * {@code totalWeight}, {@code imposedPiece}, and {@code PieceWeight.placeCount})
 * in static fields, which is only safe because vanilla executes structure
 * generation serially. Moonrise generates structure starts in parallel, so
 * concurrent strongholds would corrupt each other's weight state, causing
 * abnormal piece layouts.
 * <p>
 * This class moves that state into a {@link ThreadLocal}. That is equivalent to
 * the vanilla serial semantics because structure generation is synchronous
 * pure computation: a single {@code generatePieces} call runs from
 * {@code resetPieces()} to a complete piece tree with no yielding point (no
 * I/O, locks or blocking), so a thread can never interleave two structures and
 * the per-thread state is always owned by exactly one structure. Every
 * {@code StrongholdPieces} generation call begins with {@code resetPieces()},
 * which re-initialises the thread's state.
 * <p>
 * Note that this correctness depends on structure generation remaining
 * synchronous in vanilla. If it ever gained a yielding point, all of vanilla's
 * shared static structure state (not just these two classes) would race, and
 * the whole structure system would need to be rewritten upstream; this fix
 * would be replaced by that rewrite.
 * <p>
 * The {@link StrongholdPieces.PieceWeight} objects are still shared (the
 * {@code STRONGHOLD_PIECE_WEIGHTS} array elements), but they are treated as
 * immutable templates: per-structure {@code placeCount} is tracked in an
 * {@link IdentityHashMap} keyed by the shared weight object, so structures on
 * different threads never conflict.
 */
public final class StrongholdState {

    public static final ThreadLocal<StrongholdState> LOCAL = ThreadLocal.withInitial(StrongholdState::new);

    public final List<StrongholdPieces.PieceWeight> currentPieces = Lists.newArrayList();
    public final Map<StrongholdPieces.PieceWeight, Integer> placeCounts = new IdentityHashMap<>();
    public Class<? extends StrongholdPieces.StrongholdPiece> imposedPiece;
    public int totalWeight;

    /**
     * Re-initialises this state to match what vanilla's {@code resetPieces()}
     * did: a fresh {@code currentPieces} list containing every weight, all
     * {@code placeCount} values cleared, and {@code imposedPiece} reset.
     */
    public void reset(final StrongholdPieces.PieceWeight[] weights) {
        this.currentPieces.clear();
        for (final StrongholdPieces.PieceWeight piece : weights) {
            this.currentPieces.add(piece);
        }
        this.placeCounts.clear();
        this.imposedPiece = null;
        this.totalWeight = 0;
    }
}
