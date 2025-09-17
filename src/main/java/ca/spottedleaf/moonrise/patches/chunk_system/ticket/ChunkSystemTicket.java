package ca.spottedleaf.moonrise.patches.chunk_system.ticket;

import net.minecraft.server.level.Ticket;

public interface ChunkSystemTicket<T> extends Comparable<Ticket> {

    public long moonrise$getRemoveDelay();

    public void moonrise$setRemoveDelay(final long removeDelay);

    public T moonrise$getIdentifier();

    public void moonrise$setIdentifier(final T identifier);

}
