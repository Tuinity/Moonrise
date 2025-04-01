package ca.spottedleaf.moonrise.patches.chunk_system.ticket;

import net.minecraft.server.level.TicketType;
import java.util.Comparator;

public interface ChunkSystemTicketType<T> {

    public static final long COUNTER_TYPE_FORCED                   = 0L;
    // used only by neoforge
    public static final long COUNTER_TYPER_NATURAL_SPAWNING_FORCED = 1L;

    public static <T> TicketType create(final String name, final Comparator<T> identifierComparator) {
        return create(name, identifierComparator, 0L);
    }

    public static <T> TicketType create(final String name, final Comparator<T> identifierComparator, final long timeout) {
        // note: cannot persist unless registered
        final TicketType ret = new TicketType(timeout, false, TicketType.TicketUse.LOADING_AND_SIMULATION);

        ((ChunkSystemTicketType<T>)(Object)ret).moonrise$setIdentifierComparator(identifierComparator);

        return ret;
    }

    public long moonrise$getId();

    public Comparator<T> moonrise$getIdentifierComparator();

    public void moonrise$setIdentifierComparator(final Comparator<T> comparator);

    public long[] moonrise$getCounterTypes();

}
