package ca.spottedleaf.moonrise.patches.render;

import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import java.util.Arrays;

public final class VisibilityGraph {

    private static final int SECTION_WIDTH = 16;
    private static final int LOG2_SECTION_WIDTH = 4;
    private static final long LOG2_LONG = 6;

    private static final long[] X_SET_FACES = new long[(SECTION_WIDTH*SECTION_WIDTH*SECTION_WIDTH + (Long.SIZE - 1)) >>> LOG2_LONG];

    private static final int[] DIRECTIONS_BITSET_BY_INDEX = new int[SECTION_WIDTH*SECTION_WIDTH*SECTION_WIDTH];
    private static final int ALL_DIRECTIONS_BITSET = 0b111_111;
    private static final int TOTAL_BLOCKS_FACES;
    static {
        int set = 0;
        for (int y = 0; y < SECTION_WIDTH; ++y) {
            for (int z = 0; z < SECTION_WIDTH; ++z) {
                for (int x = 0; x < SECTION_WIDTH; ++x) {
                    if (x != 0 && x != (SECTION_WIDTH - 1) && y != 0 && y != (SECTION_WIDTH - 1) && z != 0 && z != (SECTION_WIDTH - 1)) {
                        continue;
                    }
                    final int idx = x | (z << LOG2_SECTION_WIDTH) | (y << (LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH));
                    final int bitsetIndex = idx >>> LOG2_LONG;

                    X_SET_FACES[bitsetIndex] |= (1L << idx);
                    ++set;

                    int bitset = 0;
                    if (x == 0) {
                        bitset |= (1 << Direction.WEST.ordinal());
                    }
                    if (x == 15) {
                        bitset |= (1 << Direction.EAST.ordinal());
                    }

                    if (y == 0) {
                        bitset |= (1 << Direction.DOWN.ordinal());
                    }
                    if (y == 15) {
                        bitset |= (1 << Direction.UP.ordinal());
                    }

                    if (z == 0) {
                        bitset |= (1 << Direction.NORTH.ordinal());
                    }
                    if (z == 15) {
                        bitset |= (1 << Direction.SOUTH.ordinal());
                    }

                    DIRECTIONS_BITSET_BY_INDEX[idx] = bitset;
                }
            }
        }

        TOTAL_BLOCKS_FACES = set;
    }

    private static final int[] INDEX_ADD_BY_DIRECTION_ORDINAL = new int[Direction.values().length];
    private static final int[] OPPOSITE_BITSET_BY_ORIDINAL = new int[Direction.values().length];
    static {
        for (final Direction direction : Direction.values()) {
            final int x = Math.abs(direction.getStepX());
            final int y = Math.abs(direction.getStepY());
            final int z = Math.abs(direction.getStepZ());

            int value = x | (z << LOG2_SECTION_WIDTH) | (y << (LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH));
            if (direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
                value = -value;
            }
            INDEX_ADD_BY_DIRECTION_ORDINAL[direction.ordinal()] = value;

            OPPOSITE_BITSET_BY_ORIDINAL[direction.ordinal()] = 1 << direction.getOpposite().ordinal();
        }
    }

    // idx = (axis1 & 15) | ((axis2 & 15) << 4) | ((axis3 & 15) << (4+4))

    private static final long BLOCKS_PER_LONG = Long.SIZE / SECTION_WIDTH;

    private static final int BITSET_SIZE = (SECTION_WIDTH*SECTION_WIDTH*SECTION_WIDTH + (Long.SIZE - 1)) >>> LOG2_LONG;

    // x z y
    private final long[] opaque = new long[BITSET_SIZE];

    private static final int TOTAL_DIRECTIONS = 6;

    private int opaqueEdgeCount = 0;

    // lower 12 bits: block index
    // next 6 bits: propagation direction bitset
    private int[] bfsQueue = new int[BITSET_SIZE * 4];

    // assume (x,y,z) in [0,SECTION_WIDTH-1],[0,SECTION_WIDTH-1],[0,SECTION_WIDTH-1]
    // assume that setOpaque for (x,y,z) has not been called since last reset()
    public void setOpaque(final int x, final int y, final int z) {
        final int idx = x | (z << LOG2_SECTION_WIDTH) | (y << (LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH));;

        final int bitsetIdx = idx >>> LOG2_LONG;
        final long bitsetMask = 1L << idx;

        this.opaque[bitsetIdx] |= bitsetMask;

        this.opaqueEdgeCount += Long.bitCount(X_SET_FACES[bitsetIdx] & bitsetMask);
    }

    public void reset() {
        if (this.opaqueEdgeCount != 0L) {
            Arrays.fill(this.opaque, 0L);

            this.opaqueEdgeCount = 0;
        }
    }

    private static final long EXCLUDING_EDGES = -1L ^
            (
                    (1L << (0L * 16L)) |
                    (1L << (1L * 16L)) |
                    (1L << (2L * 16L)) |
                    (1L << (3L * 16L)) |

                    (1L << (15L + 0L * 16L)) |
                    (1L << (15L + 1L * 16L)) |
                    (1L << (15L + 2L * 16L)) |
                    (1L << (15L + 3L * 16L))
            );

    private static final long EXCLUDING_LEFT_EDGES = -1L ^
            (
                    (1L << (0L * 16L)) |
                    (1L << (1L * 16L)) |
                    (1L << (2L * 16L)) |
                    (1L << (3L * 16L))
            );

    private static final long EXCLUDING_RIGHT_EDGES = -1L ^
            (
                    (1L << (15L + 0L * 16L)) |
                    (1L << (15L + 1L * 16L)) |
                    (1L << (15L + 2L * 16L)) |
                    (1L << (15L + 3L * 16L))
            );

    private static long maskMoveNeighbours(long from, long neighbour) {
        from = ~from;
        neighbour = ~neighbour;

        // from/neighbour is a bitset where 1 is transparent

        final long leftMask = (EXCLUDING_LEFT_EDGES << 1) & neighbour;
        final long rightMask = (EXCLUDING_RIGHT_EDGES >>> 1) & neighbour;

        long ret = from & neighbour;
        for (int i = 0; i < (16 - 1); ++i) {
            final long left = (ret << 1) & leftMask;
            final long right = (ret >>> 1) & rightMask;

            ret = left | right;
        }

        return ~ret;
    }

    private final int[] resizeBFSQueue() {
        return this.bfsQueue = Arrays.copyOf(this.bfsQueue, this.bfsQueue.length * 2);
    }

    public VisibilitySet findFaces() {
        final VisibilitySet ret = new VisibilitySet();
        if (this.opaqueEdgeCount < (SECTION_WIDTH*SECTION_WIDTH)) {
            // impossible for one face to be fully opaque
            ret.setAll(true);
            return ret;
        }
        if (this.opaqueEdgeCount >= (TOTAL_BLOCKS_FACES - 1)) {
            // impossible for there to be any visibility from one edge to another
            ret.setAll(false);
            return ret;
        }

        final long[] opaque = this.opaque;
        int[] queue = this.bfsQueue;
        for (int faceBitsetIndex = 0; faceBitsetIndex < 64; ++faceBitsetIndex) {
            final long faceMask = X_SET_FACES[faceBitsetIndex];
            for (;;) {
                final long value = opaque[faceBitsetIndex];
                final long valueMask = ~value & faceMask; // bitset of transparent blocks on the edge
                if (valueMask == 0L) {
                    break;
                }

                int setIndex = Long.numberOfTrailingZeros(valueMask);
                // mark visited
                opaque[faceBitsetIndex] = value | (1L << setIndex);

                setIndex |= (faceBitsetIndex << 6);

                int directionBitset = DIRECTIONS_BITSET_BY_INDEX[setIndex];

                int bfsIndex = 0;
                int queued = 1;

                queue[0] = setIndex | (directionBitset << (LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH));

                while (bfsIndex < queued) {
                    final int queuedValue = queue[bfsIndex++];
                    // retrieve bits NOT set in the bitset, as this is where we want to propagate (AND it prevents us from propagating out of bounds)
                    int negatedBitset = (~queuedValue & (ALL_DIRECTIONS_BITSET << (LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH))) >>> (LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH);

                    final int queuedIndex = queuedValue & (SECTION_WIDTH*SECTION_WIDTH*SECTION_WIDTH - 1);

                    // propagate to neighbours
                    // improve branch prediction by using bitCount compared to while (bitset != 0)
                    for (int i = 0, len = Integer.bitCount(negatedBitset); i < len; ++i) {
                        final int directionOrdinal = Integer.numberOfTrailingZeros(negatedBitset);
                        final int add = INDEX_ADD_BY_DIRECTION_ORDINAL[directionOrdinal];

                        negatedBitset ^= -negatedBitset & negatedBitset; // remove trailing bit

                        final int neighbourIndex = add + queuedIndex;

                        final long bitsetValue = opaque[neighbourIndex >>> LOG2_LONG];
                        final int directionBitsetForNeighbour = DIRECTIONS_BITSET_BY_INDEX[neighbourIndex];
                        final long neighbourMask = 1L << neighbourIndex;

                        final long newBitsetValue = bitsetValue | neighbourMask;
                        if (newBitsetValue == bitsetValue) {
                            continue;
                        }

                        final int opposite = OPPOSITE_BITSET_BY_ORIDINAL[directionOrdinal];

                        directionBitset |= directionBitsetForNeighbour;

                        opaque[neighbourIndex >>> LOG2_LONG] = newBitsetValue;
                        if (queued >= queue.length) {
                            queue = this.resizeBFSQueue();
                        }
                        queue[queued++] = neighbourIndex | ((directionBitsetForNeighbour | opposite) << (LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH+LOG2_SECTION_WIDTH));
                    }
                }

                int iter1 = directionBitset;
                for (int i = 0, ilen = Integer.bitCount(directionBitset); i < ilen; ++i) {
                    final int first = Integer.numberOfTrailingZeros(iter1);
                    iter1 ^= (-iter1 & iter1);

                    int iter2 = directionBitset;
                    for (int k = 0, klen = Integer.bitCount(directionBitset); k < klen; ++k) {
                        final int second = Integer.numberOfTrailingZeros(iter2);
                        iter2 ^= (-iter2 & iter2);

                        ret.data.set(first + (second * VisibilitySet.FACINGS));
                        ret.data.set(second + (first * VisibilitySet.FACINGS));
                    }
                }
            }
        }

        return ret;
    }
}
