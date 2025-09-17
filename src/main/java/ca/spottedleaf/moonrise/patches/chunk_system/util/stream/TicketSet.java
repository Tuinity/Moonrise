package ca.spottedleaf.moonrise.patches.chunk_system.util.stream;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicket;
import net.minecraft.server.level.Ticket;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TicketSet extends AbstractSet<Ticket> {

    private Ticket[] tickets;
    private int size;

    public TicketSet(final int initialCapacity) {
        this.tickets = new Ticket[initialCapacity];
    }

    private TicketSet(final Ticket[] tickets, final int size) {
        this.tickets = tickets;
        this.size = size;
    }

    // returns ~<insertion index> when not found, which is always < 0
    private static int binarySearch(final Ticket[] tickets, int start, int end, final Ticket key) {
        Objects.checkFromToIndex(start, end, tickets.length);

        --end; // make inclusive

        while (start <= end) {
            // note: required to use right shift in case of overflow
            final int middle = (start + end) >>> 1;

            final Ticket ticket = tickets[middle];

            final int cmp = ((ChunkSystemTicket)ticket).compareTo(key);

            if (cmp < 0) {
                start = middle + 1;
            } else if (cmp > 0) {
                end = middle - 1;
            } else {
                return middle;
            }
        }

        // not found
        // start >= 0 -> ~start < 0
        return ~start;
    }

    private void expand() {
        int newSize = this.tickets.length + (this.tickets.length >> 1);
        if (newSize < 0) {
            newSize = Integer.MAX_VALUE;
            if (newSize == this.tickets.length) {
                throw new IllegalStateException("Cannot service more than Integer.MAX_VALUE elements!");
            }
        }
        newSize = Math.max(4, newSize);
        this.tickets = Arrays.copyOf(this.tickets, newSize);
    }

    private void checkCapacity(final int size) {
        if (size <= this.tickets.length) {
            return;
        }

        this.expand();
    }

    private void addAt(final int index, final Ticket ticket) {
        this.checkCapacity(this.size + 1);

        if (index != this.size) {
            // make room
            System.arraycopy(this.tickets, index, this.tickets, index + 1, this.size - index);
        }

        ++this.size;
        this.tickets[index] = ticket;
    }

    private void removeAt(final int index) {
        if (index != --this.size) {
            System.arraycopy(this.tickets, index + 1, this.tickets, index, this.size - index);
        }

        this.tickets[this.size] = null;
    }

    public TicketSet copy() {
        final int size = this.size;
        return new TicketSet(Arrays.copyOf(this.tickets, size), size);
    }

    public Ticket[] copyBackingArray() {
        return this.tickets.clone();
    }

    public Ticket replace(final Ticket object) {
        final int index = binarySearch(this.tickets, 0, this.size, object);
        if (index >= 0) {
            final Ticket old = this.tickets[index];
            this.tickets[index] = object;
            return old;
        }

        this.addAt(~index, object);
        return object;
    }

    public Ticket removeAndGet(final Ticket object) {
        final int idx = binarySearch(this.tickets, 0, this.size, object);

        if (idx < 0) {
            return null;
        }

        final Ticket ret = this.tickets[idx];
        this.removeAt(idx);
        return ret;
    }

    public Ticket first() {
        Objects.checkIndex(0, this.size);
        return this.tickets[0];
    }

    public Ticket last() {
        Objects.checkIndex(this.size - 1, this.size);
        return this.tickets[this.size - 1];
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        return this.size == 0;
    }

    @Override
    public boolean add(final Ticket ticket) {
        final int index = binarySearch(this.tickets, 0, this.size, ticket);

        if (index >= 0) {
            return false;
        }

        this.addAt(~index, ticket);
        return true;
    }

    @Override
    public boolean remove(final Object value) {
        final int idx = binarySearch(this.tickets, 0, this.size, (Ticket)value);

        if (idx < 0) {
            return false;
        }

        this.removeAt(idx);
        return true;
    }

    public int expireAndRemoveInto(final Ticket[] array) {
        Objects.checkFromIndexSize(0, this.size, array.length);

        int removed = 0;
        int i = 0;
        final int len = this.size;
        final Ticket[] tickets = this.tickets;

        for (;;) {
            if (i >= len) {
                return removed;
            }

            final Ticket ticket = tickets[i++];
            if (!ChunkHolderManager.tickTicket(ticket)) {
                continue;
            }
            array[removed++] = ticket;
            break;
        }

        // we only want to write back to backingArray if we really need to

        int lastIndex = i - 1; // this is where new elements are shifted to

        for (; i < len; ++i) {
            final Ticket curr = tickets[i];
            if (!ChunkHolderManager.tickTicket(curr)) { // if test throws we're screwed
                tickets[lastIndex++] = curr;
            } else {
                array[removed++] = curr;
            }
        }

        // cleanup end
        Arrays.fill(tickets, lastIndex, len, null);
        this.size = lastIndex;
        return removed;
    }

    @Override
    public Iterator<Ticket> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<Ticket> {

        private static final int REMOVED = -1;

        private int index;
        private int lastReturned = REMOVED;

        @Override
        public boolean hasNext() {
            return this.index < TicketSet.this.size;
        }

        @Override
        public Ticket next() {
            if (this.index >= TicketSet.this.size) {
                throw new NoSuchElementException();
            }

            return TicketSet.this.tickets[this.lastReturned = this.index++];
        }

        @Override
        public void remove() {
            final int lastReturned = this.lastReturned;
            if (lastReturned == REMOVED) {
                throw new IllegalStateException();
            }
            this.lastReturned = REMOVED;
            this.index = lastReturned;
            TicketSet.this.removeAt(lastReturned);
        }

        @Override
        public void forEachRemaining(final Consumer<? super Ticket> action) {
            Objects.requireNonNull(action);

            while (this.hasNext()) {
                action.accept(this.next());
            }
        }
    }
}
