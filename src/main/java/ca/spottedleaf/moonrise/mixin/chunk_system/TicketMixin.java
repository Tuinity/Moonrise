package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicket;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketType;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.Comparator;

@Mixin(Ticket.class)
abstract class TicketMixin<T> implements ChunkSystemTicket<T>, Comparable<Ticket> {

    @Shadow
    @Final
    private TicketType type;

    @Shadow
    @Final
    private int ticketLevel;

    @Shadow
    private long ticksLeft;


    @Unique
    private T identifier;

    @Override
    public final long moonrise$getRemoveDelay() {
        return this.ticksLeft;
    }

    @Override
    public final void moonrise$setRemoveDelay(final long removeDelay) {
        this.ticksLeft = removeDelay;
    }

    @Override
    public final T moonrise$getIdentifier() {
        return this.identifier;
    }

    @Override
    public final void moonrise$setIdentifier(final T identifier) {
        if ((identifier == null) != (((ChunkSystemTicketType<T>)(Object)this.type).moonrise$getIdentifierComparator() == null)) {
            throw new IllegalStateException("Nullability of identifier should match nullability of comparator");
        }
        this.identifier = identifier;
    }

    /**
     * @reason Change debug to include remove identifier
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public String toString() {
        return "Ticket[" + this.type + " " + this.ticketLevel + " (" + this.identifier + ")] to die in " + this.ticksLeft;
    }

    @Override
    public final int compareTo(final Ticket ticket) {
        final int levelCompare = Integer.compare(this.ticketLevel, ((TicketMixin<?>)(Object)ticket).ticketLevel);
        if (levelCompare != 0) {
            return levelCompare;
        }

        final int typeCompare = Long.compare(
            ((ChunkSystemTicketType<T>)(Object)this.type).moonrise$getId(),
            ((ChunkSystemTicketType<?>)(Object)((TicketMixin<?>)(Object)ticket).type).moonrise$getId()
        );
        if (typeCompare != 0) {
            return typeCompare;
        }

        final Comparator<T> comparator = ((ChunkSystemTicketType<T>)(Object)this.type).moonrise$getIdentifierComparator();
        return comparator == null ? 0 : comparator.compare(this.identifier, ((TicketMixin<T>)(Object)ticket).identifier);
    }
}
