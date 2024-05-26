package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicket;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Ticket.class)
public abstract class TicketMixin<T> implements ChunkSystemTicket<T>, Comparable<Ticket<?>> {

    @Shadow
    @Final
    private TicketType<T> type;

    @Shadow
    @Final
    private int ticketLevel;

    @Shadow
    @Final
    public T key;


    @Unique
    private long removeDelay;

    @Override
    public final long moonrise$getRemoveDelay() {
        return this.removeDelay;
    }

    @Override
    public final void moonrise$setRemoveDelay(final long removeDelay) {
        this.removeDelay = removeDelay;
    }

    /**
     * @reason Change debug to include remove delay
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public String toString() {
        return "Ticket[" + this.type + " " + this.ticketLevel + " (" + this.key + ")] to die in " + this.removeDelay;
    }

    /**
     * @reason Remove old chunk system hook
     * @author Spottedleaf
     */
    @Overwrite
    public void setCreatedTick(final long tickCreated) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Remove old chunk system hook
     * @author Spottedleaf
     */
    @Overwrite
    public boolean timedOut(final long currentTick) {
        throw new UnsupportedOperationException();
    }
}
