package ca.spottedleaf.moonrise.patches.chunk_tick_iteration;

public final class ChunkTickConstants {

    public static final int PLAYER_SPAWN_TRACK_RANGE = 8;
    // the smallest distance on x/z is at 45 degrees, we need to subtract 0.5 since this is calculated from chunk center and not chunk perimeter
    // note: vanilla does not subtract 0.5 but the result is (luckily!) the same
    public static final int NARROW_SPAWN_TRACK_RANGE = (int)Math.floor(((double)PLAYER_SPAWN_TRACK_RANGE / Math.sqrt(2.0)) - 0.5);
    static {
        if (NARROW_SPAWN_TRACK_RANGE < 0) {
            throw new IllegalStateException();
        }
    }

}
