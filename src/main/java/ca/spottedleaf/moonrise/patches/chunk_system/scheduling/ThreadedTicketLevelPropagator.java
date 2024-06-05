package ca.spottedleaf.moonrise.patches.chunk_system.scheduling;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.lock.ReentrantAreaLock;
import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkProgressionTask;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ByteMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import java.lang.invoke.VarHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

public abstract class ThreadedTicketLevelPropagator {

    // sections are 64 in length
    public static final int SECTION_SHIFT = 6;
    public static final int SECTION_SIZE = 1 << SECTION_SHIFT;
    private static final int LEVEL_BITS = SECTION_SHIFT;
    private static final int LEVEL_COUNT = 1 << LEVEL_BITS;
    private static final int MIN_SOURCE_LEVEL = 1;
    // we limit the max source to 62 because the de-propagation code _must_ attempt to de-propagate
    // a 1 level to 0; and if a source was 63 then it may cross more than 2 sections in de-propagation
    private static final int MAX_SOURCE_LEVEL = 62;

    private static int getMaxSchedulingRadius() {
        return 2 * ChunkTaskScheduler.getMaxAccessRadius();
    }

    private final UpdateQueue updateQueue;
    private final ConcurrentLong2ReferenceChainedHashTable<Section> sections;

    public ThreadedTicketLevelPropagator() {
        this.updateQueue = new UpdateQueue();
        this.sections = new ConcurrentLong2ReferenceChainedHashTable<>();
    }

    // must hold ticket lock for:
    // (posX & ~(SECTION_SIZE - 1), posZ & ~(SECTION_SIZE - 1)) to (posX | (SECTION_SIZE - 1), posZ | (SECTION_SIZE - 1))
    public void setSource(final int posX, final int posZ, final int to) {
        if (to < 1 || to > MAX_SOURCE_LEVEL) {
            throw new IllegalArgumentException("Source: " + to);
        }

        final int sectionX = posX >> SECTION_SHIFT;
        final int sectionZ = posZ >> SECTION_SHIFT;

        final long coordinate = CoordinateUtils.getChunkKey(sectionX, sectionZ);
        Section section = this.sections.get(coordinate);
        if (section == null) {
            if (null != this.sections.putIfAbsent(coordinate, section = new Section(sectionX, sectionZ))) {
                throw new IllegalStateException("Race condition while creating new section");
            }
        }

        final int localIdx = (posX & (SECTION_SIZE - 1)) | ((posZ & (SECTION_SIZE - 1)) << SECTION_SHIFT);
        final short sLocalIdx = (short)localIdx;

        final short sourceAndLevel = section.levels[localIdx];
        final int currentSource = (sourceAndLevel >>> 8) & 0xFF;

        if (currentSource == to) {
            // nothing to do
            // make sure to kill the current update, if any
            section.queuedSources.replace(sLocalIdx, (byte)to);
            return;
        }

        if (section.queuedSources.put(sLocalIdx, (byte)to) == Section.NO_QUEUED_UPDATE && section.queuedSources.size() == 1) {
            this.queueSectionUpdate(section);
        }
    }

    // must hold ticket lock for:
    // (posX & ~(SECTION_SIZE - 1), posZ & ~(SECTION_SIZE - 1)) to (posX | (SECTION_SIZE - 1), posZ | (SECTION_SIZE - 1))
    public void removeSource(final int posX, final int posZ) {
        final int sectionX = posX >> SECTION_SHIFT;
        final int sectionZ = posZ >> SECTION_SHIFT;

        final long coordinate = CoordinateUtils.getChunkKey(sectionX, sectionZ);
        final Section section = this.sections.get(coordinate);

        if (section == null) {
            return;
        }

        final int localIdx = (posX & (SECTION_SIZE - 1)) | ((posZ & (SECTION_SIZE - 1)) << SECTION_SHIFT);
        final short sLocalIdx = (short)localIdx;

        final int currentSource = (section.levels[localIdx] >>> 8) & 0xFF;

        if (currentSource == 0) {
            // we use replace here so that we do not possibly multi-queue a section for an update
            section.queuedSources.replace(sLocalIdx, (byte)0);
            return;
        }

        if (section.queuedSources.put(sLocalIdx, (byte)0) == Section.NO_QUEUED_UPDATE && section.queuedSources.size() == 1) {
            this.queueSectionUpdate(section);
        }
    }

    private void queueSectionUpdate(final Section section) {
        this.updateQueue.append(new UpdateQueue.UpdateQueueNode(section, null));
    }

    public boolean hasPendingUpdates() {
        return !this.updateQueue.isEmpty();
    }

    // holds ticket lock for every chunk section represented by any position in the key set
    // updates is modifiable and passed to processSchedulingUpdates after this call
    protected abstract void processLevelUpdates(final Long2ByteLinkedOpenHashMap updates);

    // holds ticket lock for every chunk section represented by any position in the key set
    // holds scheduling lock in max access radius for every position held by the ticket lock
    // updates is cleared after this call
    protected abstract void processSchedulingUpdates(final Long2ByteLinkedOpenHashMap updates, final List<ChunkProgressionTask> scheduledTasks,
                                                     final List<NewChunkHolder> changedFullStatus);

    // must hold ticket lock for every position in the sections in one radius around sectionX,sectionZ
    public boolean performUpdate(final int sectionX, final int sectionZ, final ReentrantAreaLock schedulingLock,
                                 final List<ChunkProgressionTask> scheduledTasks, final List<NewChunkHolder> changedFullStatus) {
        if (!this.hasPendingUpdates()) {
            return false;
        }

        final long coordinate = CoordinateUtils.getChunkKey(sectionX, sectionZ);
        final Section section = this.sections.get(coordinate);

        if (section == null || section.queuedSources.isEmpty()) {
            // no section or no updates
            return false;
        }

        final Propagator propagator = Propagator.acquirePropagator();
        final boolean ret = this.performUpdate(section, null, propagator,
            null, schedulingLock, scheduledTasks, changedFullStatus
        );
        Propagator.returnPropagator(propagator);
        return ret;
    }

    private boolean performUpdate(final Section section, final UpdateQueue.UpdateQueueNode node, final Propagator propagator,
                                  final ReentrantAreaLock ticketLock, final ReentrantAreaLock schedulingLock,
                                  final List<ChunkProgressionTask> scheduledTasks, final List<NewChunkHolder> changedFullStatus) {
        final int sectionX = section.sectionX;
        final int sectionZ = section.sectionZ;

        final int rad1MinX = (sectionX - 1) << SECTION_SHIFT;
        final int rad1MinZ = (sectionZ - 1) << SECTION_SHIFT;
        final int rad1MaxX = ((sectionX + 1) << SECTION_SHIFT) | (SECTION_SIZE - 1);
        final int rad1MaxZ = ((sectionZ + 1) << SECTION_SHIFT) | (SECTION_SIZE - 1);

        // set up encode offset first as we need to queue level changes _before_
        propagator.setupEncodeOffset(sectionX, sectionZ);

        final int coordinateOffset = propagator.coordinateOffset;

        final ReentrantAreaLock.Node ticketNode = ticketLock == null ? null : ticketLock.lock(rad1MinX, rad1MinZ, rad1MaxX, rad1MaxZ);
        final boolean ret;
        try {
            // first, check if this update was stolen
            if (section != this.sections.get(CoordinateUtils.getChunkKey(sectionX, sectionZ))) {
                // occurs when a stolen update deletes this section
                // it is possible that another update is scheduled, but that one will have the correct section
                if (node != null) {
                    this.updateQueue.remove(node);
                }
                return false;
            }

            final int oldSourceSize = section.sources.size();

            // process pending sources
            for (final Iterator<Short2ByteMap.Entry> iterator = section.queuedSources.short2ByteEntrySet().fastIterator(); iterator.hasNext();) {
                final Short2ByteMap.Entry entry = iterator.next();
                final int pos = (int)entry.getShortKey();
                final int posX = (pos & (SECTION_SIZE - 1)) | (sectionX << SECTION_SHIFT);
                final int posZ = ((pos >> SECTION_SHIFT) & (SECTION_SIZE - 1)) | (sectionZ << SECTION_SHIFT);
                final int newSource = (int)entry.getByteValue();

                final short currentEncoded = section.levels[pos];
                final int currLevel = currentEncoded & 0xFF;
                final int prevSource = (currentEncoded >>> 8) & 0xFF;

                if (prevSource == newSource) {
                    // nothing changed
                    continue;
                }

                if ((prevSource < currLevel && newSource <= currLevel) || newSource == currLevel) {
                    // just update the source, don't need to propagate change
                    section.levels[pos] = (short)(currLevel | (newSource << 8));
                    // level is unchanged, don't add to changed positions
                } else {
                    // set current level and current source to new source
                    section.levels[pos] = (short)(newSource | (newSource << 8));
                    // must add to updated positions in case this is final
                    propagator.updatedPositions.put(CoordinateUtils.getChunkKey(posX, posZ), (byte)newSource);
                    if (newSource != 0) {
                        // queue increase with new source level
                        propagator.appendToIncreaseQueue(
                            ((long)(posX + (posZ << Propagator.COORDINATE_BITS) + coordinateOffset) & ((1L << (Propagator.COORDINATE_BITS + Propagator.COORDINATE_BITS)) - 1)) |
                                ((newSource & (LEVEL_COUNT - 1L)) << (Propagator.COORDINATE_BITS + Propagator.COORDINATE_BITS)) |
                                (Propagator.ALL_DIRECTIONS_BITSET << (Propagator.COORDINATE_BITS + Propagator.COORDINATE_BITS + LEVEL_BITS))
                        );
                    }
                    // queue decrease with previous level
                    if (newSource < currLevel) {
                        propagator.appendToDecreaseQueue(
                            ((long)(posX + (posZ << Propagator.COORDINATE_BITS) + coordinateOffset) & ((1L << (Propagator.COORDINATE_BITS + Propagator.COORDINATE_BITS)) - 1)) |
                                ((currLevel & (LEVEL_COUNT - 1L)) << (Propagator.COORDINATE_BITS + Propagator.COORDINATE_BITS)) |
                                (Propagator.ALL_DIRECTIONS_BITSET << (Propagator.COORDINATE_BITS + Propagator.COORDINATE_BITS + LEVEL_BITS))
                        );
                    }
                }

                if (newSource == 0) {
                    // prevSource != newSource, so we are removing this source
                    section.sources.remove((short)pos);
                } else if (prevSource == 0) {
                    // prevSource != newSource, so we are adding this source
                    section.sources.add((short)pos);
                }
            }

            section.queuedSources.clear();

            final int newSourceSize = section.sources.size();

            if (oldSourceSize == 0 && newSourceSize != 0) {
                // need to make sure the sections in 1 radius are initialised
                for (int dz = -1; dz <= 1; ++dz) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        if ((dx | dz) == 0) {
                            continue;
                        }
                        final int offX = dx + sectionX;
                        final int offZ = dz + sectionZ;
                        final long coordinate = CoordinateUtils.getChunkKey(offX, offZ);
                        final Section neighbour = this.sections.computeIfAbsent(coordinate, (final long keyInMap) -> {
                            return new Section(CoordinateUtils.getChunkX(keyInMap), CoordinateUtils.getChunkZ(keyInMap));
                        });

                        // increase ref count
                        ++neighbour.oneRadNeighboursWithSources;
                        if (neighbour.oneRadNeighboursWithSources <= 0 || neighbour.oneRadNeighboursWithSources > 8) {
                            throw new IllegalStateException(Integer.toString(neighbour.oneRadNeighboursWithSources));
                        }
                    }
                }
            }

            if (propagator.hasUpdates()) {
                propagator.setupCaches(this, sectionX, sectionZ, 1);
                propagator.performDecrease();
                // don't need try-finally, as any exception will cause the propagator to not be returned
                propagator.destroyCaches();
            }

            if (newSourceSize == 0) {
                final boolean decrementRef = oldSourceSize != 0;
                // check for section de-init
                for (int dz = -1; dz <= 1; ++dz) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        final int offX = dx + sectionX;
                        final int offZ = dz + sectionZ;
                        final long coordinate = CoordinateUtils.getChunkKey(offX, offZ);
                        final Section neighbour = this.sections.get(coordinate);

                        if (neighbour == null) {
                            if (oldSourceSize == 0 && (dx | dz) != 0) {
                                // since we don't have sources, this section is allowed to be null
                                continue;
                            }
                            throw new IllegalStateException("??");
                        }

                        if (decrementRef && (dx | dz) != 0) {
                            // decrease ref count, but only for neighbours
                            --neighbour.oneRadNeighboursWithSources;
                        }

                        // we need to check the current section for de-init as well
                        if (neighbour.oneRadNeighboursWithSources == 0) {
                            if (neighbour.queuedSources.isEmpty() && neighbour.sources.isEmpty()) {
                                // need to de-init
                                this.sections.remove(coordinate);
                            } // else: neighbour is queued for an update, and it will de-init itself
                        } else if (neighbour.oneRadNeighboursWithSources < 0 || neighbour.oneRadNeighboursWithSources > 8) {
                            throw new IllegalStateException(Integer.toString(neighbour.oneRadNeighboursWithSources));
                        }
                    }
                }
            }


            ret = !propagator.updatedPositions.isEmpty();

            if (ret) {
                this.processLevelUpdates(propagator.updatedPositions);

                if (!propagator.updatedPositions.isEmpty()) {
                    // now we can actually update the ticket levels in the chunk holders
                    final int maxScheduleRadius = getMaxSchedulingRadius();

                    // allow the chunkholders to process ticket level updates without needing to acquire the schedule lock every time
                    final ReentrantAreaLock.Node schedulingNode = schedulingLock.lock(
                        rad1MinX - maxScheduleRadius, rad1MinZ - maxScheduleRadius,
                        rad1MaxX + maxScheduleRadius, rad1MaxZ + maxScheduleRadius
                    );
                    try {
                        this.processSchedulingUpdates(propagator.updatedPositions, scheduledTasks, changedFullStatus);
                    } finally {
                        schedulingLock.unlock(schedulingNode);
                    }
                }

                propagator.updatedPositions.clear();
            }
        } finally {
            if (ticketLock != null) {
                ticketLock.unlock(ticketNode);
            }
        }

        // finished
        if (node != null) {
            this.updateQueue.remove(node);
        }

        return ret;
    }

    public boolean performUpdates(final ReentrantAreaLock ticketLock, final ReentrantAreaLock schedulingLock,
                                  final List<ChunkProgressionTask> scheduledTasks, final List<NewChunkHolder> changedFullStatus) {
        if (this.updateQueue.isEmpty()) {
            return false;
        }

        final long maxOrder = this.updateQueue.getLastOrder();

        boolean updated = false;
        Propagator propagator = null;

        for (;;) {
            final UpdateQueue.UpdateQueueNode toUpdate = this.updateQueue.acquireNextOrWait(maxOrder);
            if (toUpdate == null) {
                if (!this.updateQueue.hasRemainingUpdates(maxOrder)) {
                    if (propagator != null) {
                        Propagator.returnPropagator(propagator);
                    }
                    return updated;
                }

                continue;
            }

            if (propagator == null) {
                propagator = Propagator.acquirePropagator();
            }

            updated |= this.performUpdate(toUpdate.section, toUpdate, propagator, ticketLock, schedulingLock, scheduledTasks, changedFullStatus);
        }
    }

    // Similar implementation of concurrent FIFO queue (See MTQ in ConcurrentUtil) which has an additional node pointer
    // for the last update node being handled
    private static final class UpdateQueue {

        private volatile UpdateQueueNode head;
        private volatile UpdateQueueNode tail;

        private static final VarHandle HEAD_HANDLE = ConcurrentUtil.getVarHandle(UpdateQueue.class, "head", UpdateQueueNode.class);
        private static final VarHandle TAIL_HANDLE = ConcurrentUtil.getVarHandle(UpdateQueue.class, "tail", UpdateQueueNode.class);

        /* head */

        private final void setHeadPlain(final UpdateQueueNode newHead) {
            HEAD_HANDLE.set(this, newHead);
        }

        private final void setHeadOpaque(final UpdateQueueNode newHead) {
            HEAD_HANDLE.setOpaque(this, newHead);
        }

        private final UpdateQueueNode getHeadPlain() {
            return (UpdateQueueNode)HEAD_HANDLE.get(this);
        }

        private final UpdateQueueNode getHeadOpaque() {
            return (UpdateQueueNode)HEAD_HANDLE.getOpaque(this);
        }

        private final UpdateQueueNode getHeadAcquire() {
            return (UpdateQueueNode)HEAD_HANDLE.getAcquire(this);
        }

        /* tail */

        private final void setTailPlain(final UpdateQueueNode newTail) {
            TAIL_HANDLE.set(this, newTail);
        }

        private final void setTailOpaque(final UpdateQueueNode newTail) {
            TAIL_HANDLE.setOpaque(this, newTail);
        }

        private final UpdateQueueNode getTailPlain() {
            return (UpdateQueueNode)TAIL_HANDLE.get(this);
        }

        private final UpdateQueueNode getTailOpaque() {
            return (UpdateQueueNode)TAIL_HANDLE.getOpaque(this);
        }

        public UpdateQueue() {
            final UpdateQueueNode dummy = new UpdateQueueNode(null, null);
            dummy.order = -1L;
            dummy.preventAdds();

            this.setHeadPlain(dummy);
            this.setTailPlain(dummy);
        }

        public boolean isEmpty() {
            return this.peek() == null;
        }

        public boolean hasRemainingUpdates(final long maxUpdate) {
            final UpdateQueueNode node = this.peek();
            return node != null && node.order <= maxUpdate;
        }

        public long getLastOrder() {
            for (UpdateQueueNode tail = this.getTailOpaque(), curr = tail;;) {
                final UpdateQueueNode next = curr.getNextVolatile();
                if (next == null) {
                    // try to update stale tail
                    if (this.getTailOpaque() == tail && curr != tail) {
                        this.setTailOpaque(curr);
                    }
                    return curr.order;
                }
                curr = next;
            }
        }

        private static void await(final UpdateQueueNode node) {
            final Thread currThread = Thread.currentThread();
            // we do not use add-blocking because we use the nullability of the section to block
            // remove() does not begin to poll from the wait queue until the section is null'd,
            // and so provided we check the nullability before parking there is no ordering of these operations
            // such that remove() finishes polling from the wait queue while section is not null
            node.add(currThread);

            // wait until completed
            while (node.getSectionVolatile() != null) {
                LockSupport.park();
            }
        }

        public UpdateQueueNode acquireNextOrWait(final long maxOrder) {
            final List<UpdateQueueNode> blocking = new ArrayList<>();

            node_search:
            for (UpdateQueueNode curr = this.peek(); curr != null && curr.order <= maxOrder; curr = curr.getNextVolatile()) {
                if (curr.getSectionVolatile() == null) {
                    continue;
                }

                if (curr.getUpdatingVolatile()) {
                    blocking.add(curr);
                    continue;
                }

                for (int i = 0, len = blocking.size(); i < len; ++i) {
                    final UpdateQueueNode node = blocking.get(i);

                    if (node.intersects(curr)) {
                        continue node_search;
                    }
                }

                if (curr.getAndSetUpdatingVolatile(true)) {
                    blocking.add(curr);
                    continue;
                }

                return curr;
            }

            if (!blocking.isEmpty()) {
                await(blocking.get(0));
            }

            return null;
        }

        public UpdateQueueNode peek() {
            for (UpdateQueueNode head = this.getHeadOpaque(), curr = head;;) {
                final UpdateQueueNode next = curr.getNextVolatile();
                final Section element = curr.getSectionVolatile(); /* Likely in sync */

                if (element != null) {
                    if (this.getHeadOpaque() == head && curr != head) {
                        this.setHeadOpaque(curr);
                    }
                    return curr;
                }

                if (next == null) {
                    if (this.getHeadOpaque() == head && curr != head) {
                        this.setHeadOpaque(curr);
                    }
                    return null;
                }
                curr = next;
            }
        }

        public void remove(final UpdateQueueNode node) {
            // mark as removed
            node.setSectionVolatile(null);

            // use peek to advance head
            this.peek();

            // unpark any waiters / block the wait queue
            Thread unpark;
            while ((unpark = node.poll()) != null) {
                LockSupport.unpark(unpark);
            }
        }

        public void append(final UpdateQueueNode node) {
            int failures = 0;

            for (UpdateQueueNode currTail = this.getTailOpaque(), curr = currTail;;) {
                /* It has been experimentally shown that placing the read before the backoff results in significantly greater performance */
                /* It is likely due to a cache miss caused by another write to the next field */
                final UpdateQueueNode next = curr.getNextVolatile();

                for (int i = 0; i < failures; ++i) {
                    ConcurrentUtil.backoff();
                }

                if (next == null) {
                    node.order = curr.order + 1L;
                    final UpdateQueueNode compared = curr.compareExchangeNextVolatile(null, node);

                    if (compared == null) {
                        /* Added */
                        /* Avoid CASing on tail more than we need to */
                        /* CAS to avoid setting an out-of-date tail */
                        if (this.getTailOpaque() == currTail) {
                            this.setTailOpaque(node);
                        }
                        return;
                    }

                    ++failures;
                    curr = compared;
                    continue;
                }

                if (curr == currTail) {
                    /* Tail is likely not up-to-date */
                    curr = next;
                } else {
                    /* Try to update to tail */
                    if (currTail == (currTail = this.getTailOpaque())) {
                        curr = next;
                    } else {
                        curr = currTail;
                    }
                }
            }
        }

        // each node also represents a set of waiters, represented by the MTQ
        // if the queue is add-blocked, then the update is complete
        private static final class UpdateQueueNode extends MultiThreadedQueue<Thread> {
            private final int sectionX;
            private final int sectionZ;

            private long order;
            private volatile Section section;
            private volatile UpdateQueueNode next;
            private volatile boolean updating;

            private static final VarHandle SECTION_HANDLE = ConcurrentUtil.getVarHandle(UpdateQueueNode.class, "section", Section.class);
            private static final VarHandle NEXT_HANDLE = ConcurrentUtil.getVarHandle(UpdateQueueNode.class, "next", UpdateQueueNode.class);
            private static final VarHandle UPDATING_HANDLE = ConcurrentUtil.getVarHandle(UpdateQueueNode.class, "updating", boolean.class);

            public UpdateQueueNode(final Section section, final UpdateQueueNode next) {
                if (section == null) {
                    this.sectionX = this.sectionZ = 0;
                } else {
                    this.sectionX = section.sectionX;
                    this.sectionZ = section.sectionZ;
                }

                SECTION_HANDLE.set(this, section);
                NEXT_HANDLE.set(this, next);
            }

            public boolean intersects(final UpdateQueueNode other) {
                final int dist = Math.max(Math.abs(this.sectionX - other.sectionX), Math.abs(this.sectionZ - other.sectionZ));

                // intersection radius is ticket update radius (1) + scheduling radius
                return dist <= (1 + ((getMaxSchedulingRadius() + (SECTION_SIZE - 1)) >> SECTION_SHIFT));
            }

            /* section */

            private final Section getSectionPlain() {
                return (Section)SECTION_HANDLE.get(this);
            }

            private final Section getSectionVolatile() {
                return (Section)SECTION_HANDLE.getVolatile(this);
            }

            private final void setSectionPlain(final Section update) {
                SECTION_HANDLE.set(this, update);
            }

            private final void setSectionOpaque(final Section update) {
                SECTION_HANDLE.setOpaque(this, update);
            }

            private final void setSectionVolatile(final Section update) {
                SECTION_HANDLE.setVolatile(this, update);
            }

            private final Section getAndSetSectionVolatile(final Section update) {
                return (Section)SECTION_HANDLE.getAndSet(this, update);
            }

            private final Section compareExchangeSectionVolatile(final Section expect, final Section update) {
                return (Section)SECTION_HANDLE.compareAndExchange(this, expect, update);
            }

            /* next */

            private final UpdateQueueNode getNextPlain() {
                return (UpdateQueueNode)NEXT_HANDLE.get(this);
            }

            private final UpdateQueueNode getNextOpaque() {
                return (UpdateQueueNode)NEXT_HANDLE.getOpaque(this);
            }

            private final UpdateQueueNode getNextAcquire() {
                return (UpdateQueueNode)NEXT_HANDLE.getAcquire(this);
            }

            private final UpdateQueueNode getNextVolatile() {
                return (UpdateQueueNode)NEXT_HANDLE.getVolatile(this);
            }

            private final void setNextPlain(final UpdateQueueNode next) {
                NEXT_HANDLE.set(this, next);
            }

            private final void setNextVolatile(final UpdateQueueNode next) {
                NEXT_HANDLE.setVolatile(this, next);
            }

            private final UpdateQueueNode compareExchangeNextVolatile(final UpdateQueueNode expect, final UpdateQueueNode set) {
                return (UpdateQueueNode)NEXT_HANDLE.compareAndExchange(this, expect, set);
            }

            /* updating */

            private final boolean getUpdatingVolatile() {
                return (boolean)UPDATING_HANDLE.getVolatile(this);
            }

            private final boolean getAndSetUpdatingVolatile(final boolean value) {
                return (boolean)UPDATING_HANDLE.getAndSet(this, value);
            }
        }
    }

    private static final class Section {

        // upper 8 bits: sources, lower 8 bits: level
        // if we REALLY wanted to get crazy, we could make the increase propagator use MethodHandles#byteArrayViewVarHandle
        // to read and write the lower 8 bits of this array directly rather than reading, updating the bits, then writing back.
        private final short[] levels = new short[SECTION_SIZE * SECTION_SIZE];
        // set of local positions that represent sources
        private final ShortOpenHashSet sources = new ShortOpenHashSet();
        // map of local index to new source level
        // the source level _cannot_ be updated in the backing storage immediately since the update
        private static final byte NO_QUEUED_UPDATE = (byte)-1;
        private final Short2ByteLinkedOpenHashMap queuedSources = new Short2ByteLinkedOpenHashMap();
        {
            this.queuedSources.defaultReturnValue(NO_QUEUED_UPDATE);
        }
        private int oneRadNeighboursWithSources = 0;

        public final int sectionX;
        public final int sectionZ;

        public Section(final int sectionX, final int sectionZ) {
            this.sectionX = sectionX;
            this.sectionZ = sectionZ;
        }

        public boolean isZero() {
            for (final short val : this.levels) {
                if (val != 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            final StringBuilder ret = new StringBuilder();

            for (int x = 0; x < SECTION_SIZE; ++x) {
                ret.append("levels x=").append(x).append("\n");
                for (int z = 0; z < SECTION_SIZE; ++z) {
                    final short v = this.levels[x | (z << SECTION_SHIFT)];
                    ret.append(v & 0xFF).append(".");
                }
                ret.append("\n");
                ret.append("sources x=").append(x).append("\n");
                for (int z = 0; z < SECTION_SIZE; ++z) {
                    final short v = this.levels[x | (z << SECTION_SHIFT)];
                    ret.append((v >>> 8) & 0xFF).append(".");
                }
                ret.append("\n\n");
            }

            return ret.toString();
        }
    }


    private static final class Propagator {

        private static final ArrayDeque<Propagator> CACHED_PROPAGATORS = new ArrayDeque<>();
        private static final int MAX_PROPAGATORS = Runtime.getRuntime().availableProcessors() * 2;

        private static Propagator acquirePropagator() {
            synchronized (CACHED_PROPAGATORS) {
                final Propagator ret = CACHED_PROPAGATORS.pollFirst();
                if (ret != null) {
                    return ret;
                }
            }
            return new Propagator();
        }

        private static void returnPropagator(final Propagator propagator) {
            synchronized (CACHED_PROPAGATORS) {
                if (CACHED_PROPAGATORS.size() < MAX_PROPAGATORS) {
                    CACHED_PROPAGATORS.add(propagator);
                }
            }
        }

        private static final int SECTION_RADIUS = 2;
        private static final int SECTION_CACHE_WIDTH = 2 * SECTION_RADIUS + 1;
        // minimum number of bits to represent [0, SECTION_SIZE * SECTION_CACHE_WIDTH)
        private static final int COORDINATE_BITS = 9;
        private static final int COORDINATE_SIZE = 1 << COORDINATE_BITS;
        static {
            if ((SECTION_SIZE * SECTION_CACHE_WIDTH) > (1 << COORDINATE_BITS)) {
                throw new IllegalStateException("Adjust COORDINATE_BITS");
            }
        }
        // index = x + (z * SECTION_CACHE_WIDTH)
        // (this requires x >= 0 and z >= 0)
        private final Section[] sections = new Section[SECTION_CACHE_WIDTH * SECTION_CACHE_WIDTH];

        private int encodeOffsetX;
        private int encodeOffsetZ;

        private int coordinateOffset;

        private int encodeSectionOffsetX;
        private int encodeSectionOffsetZ;

        private int sectionIndexOffset;

        public final boolean hasUpdates() {
            return this.decreaseQueueInitialLength != 0 || this.increaseQueueInitialLength != 0;
        }

        private final void setupEncodeOffset(final int centerSectionX, final int centerSectionZ) {
            final int maxCoordinate = (SECTION_RADIUS * SECTION_SIZE - 1);
            // must have that encoded >= 0
            // coordinates can range from [-maxCoordinate + centerSection*SECTION_SIZE, maxCoordinate + centerSection*SECTION_SIZE]
            // we want a range of [0, maxCoordinate*2]
            // so, 0 = -maxCoordinate + centerSection*SECTION_SIZE + offset
            this.encodeOffsetX = maxCoordinate - (centerSectionX << SECTION_SHIFT);
            this.encodeOffsetZ = maxCoordinate - (centerSectionZ << SECTION_SHIFT);

            // encoded coordinates range from [0, SECTION_SIZE * SECTION_CACHE_WIDTH)
            // coordinate index = (x + encodeOffsetX) + ((z + encodeOffsetZ) << COORDINATE_BITS)
            this.coordinateOffset = this.encodeOffsetX + (this.encodeOffsetZ << COORDINATE_BITS);

            // need encoded values to be >= 0
            // so, 0 = (-SECTION_RADIUS + centerSectionX) + encodeOffset
            this.encodeSectionOffsetX = SECTION_RADIUS - centerSectionX;
            this.encodeSectionOffsetZ = SECTION_RADIUS - centerSectionZ;

            // section index = (secX + encodeSectionOffsetX) + ((secZ + encodeSectionOffsetZ) * SECTION_CACHE_WIDTH)
            this.sectionIndexOffset = this.encodeSectionOffsetX + (this.encodeSectionOffsetZ * SECTION_CACHE_WIDTH);
        }

        // must hold ticket lock for (centerSectionX,centerSectionZ) in radius rad
        // must call setupEncodeOffset
        private final void setupCaches(final ThreadedTicketLevelPropagator propagator,
                                         final int centerSectionX, final int centerSectionZ,
                                         final int rad) {
            for (int dz = -rad; dz <= rad; ++dz) {
                for (int dx = -rad; dx <= rad; ++dx) {
                    final int sectionX = centerSectionX + dx;
                    final int sectionZ = centerSectionZ + dz;
                    final long coordinate = CoordinateUtils.getChunkKey(sectionX, sectionZ);
                    final Section section = propagator.sections.get(coordinate);

                    if (section == null) {
                        throw new IllegalStateException("Section at " + coordinate + " should not be null");
                    }

                    this.setSectionInCache(sectionX, sectionZ, section);
                }
            }
        }

        private final void setSectionInCache(final int sectionX, final int sectionZ, final Section section) {
            this.sections[sectionX + SECTION_CACHE_WIDTH*sectionZ + this.sectionIndexOffset] = section;
        }

        private final Section getSection(final int sectionX, final int sectionZ) {
            return this.sections[sectionX + SECTION_CACHE_WIDTH*sectionZ + this.sectionIndexOffset];
        }

        private final int getLevel(final int posX, final int posZ) {
            final Section section = this.sections[(posX >> SECTION_SHIFT) + SECTION_CACHE_WIDTH*(posZ >> SECTION_SHIFT) + this.sectionIndexOffset];
            if (section != null) {
                return (int)section.levels[(posX & (SECTION_SIZE - 1)) | ((posZ & (SECTION_SIZE - 1)) << SECTION_SHIFT)] & 0xFF;
            }

            return 0;
        }

        private final void setLevel(final int posX, final int posZ, final int to) {
            final Section section = this.sections[(posX >> SECTION_SHIFT) + SECTION_CACHE_WIDTH*(posZ >> SECTION_SHIFT) + this.sectionIndexOffset];
            if (section != null) {
                final int index = (posX & (SECTION_SIZE - 1)) | ((posZ & (SECTION_SIZE - 1)) << SECTION_SHIFT);
                final short level = section.levels[index];
                section.levels[index] = (short)((level & ~0xFF) | (to & 0xFF));
                this.updatedPositions.put(CoordinateUtils.getChunkKey(posX, posZ), (byte)to);
            }
        }

        private final void destroyCaches() {
            Arrays.fill(this.sections, null);
        }

        // contains:
        // lower (COORDINATE_BITS(9) + COORDINATE_BITS(9) = 18) bits encoded position: (x | (z << COORDINATE_BITS))
        // next LEVEL_BITS (6) bits: propagated level [0, 63]
        // propagation directions bitset (16 bits):
        private static final long ALL_DIRECTIONS_BITSET = (
                // z = -1
                (1L << ((1 - 1) | ((1 - 1) << 2))) |
                (1L << ((1 + 0) | ((1 - 1) << 2))) |
                (1L << ((1 + 1) | ((1 - 1) << 2))) |

                // z = 0
                (1L << ((1 - 1) | ((1 + 0) << 2))) |
                //(1L << ((1 + 0) | ((1 + 0) << 2))) | // exclude (0,0)
                (1L << ((1 + 1) | ((1 + 0) << 2))) |

                // z = 1
                (1L << ((1 - 1) | ((1 + 1) << 2))) |
                (1L << ((1 + 0) | ((1 + 1) << 2))) |
                (1L << ((1 + 1) | ((1 + 1) << 2)))
        );

        private void ex(int bitset) {
            for (int i = 0, len = Integer.bitCount(bitset); i < len; ++i) {
                final int set = Integer.numberOfTrailingZeros(bitset);
                final int tailingBit = (-bitset) & bitset;
                // XOR to remove the trailing bit
                bitset ^= tailingBit;

                // the encoded value set is (x_val) | (z_val << 2), totaling 4 bits
                // thus, the bitset is 16 bits wide where each one represents a direction to propagate and the
                // index of the set bit is the encoded value
                // the encoded coordinate has 3 valid states:
                // 0b00 (0) -> -1
                // 0b01 (1) -> 0
                // 0b10 (2) -> 1
                // the decode operation then is val - 1, and the encode operation is val + 1
                final int xOff = (set & 3) - 1;
                final int zOff = ((set >>> 2) & 3) - 1;
                System.out.println("Encoded: (" + xOff + "," + zOff + ")");
            }
        }

        private void ch(long bs, int shift) {
            int bitset = (int)(bs >>> shift);
            for (int i = 0, len = Integer.bitCount(bitset); i < len; ++i) {
                final int set = Integer.numberOfTrailingZeros(bitset);
                final int tailingBit = (-bitset) & bitset;
                // XOR to remove the trailing bit
                bitset ^= tailingBit;

                // the encoded value set is (x_val) | (z_val << 2), totaling 4 bits
                // thus, the bitset is 16 bits wide where each one represents a direction to propagate and the
                // index of the set bit is the encoded value
                // the encoded coordinate has 3 valid states:
                // 0b00 (0) -> -1
                // 0b01 (1) -> 0
                // 0b10 (2) -> 1
                // the decode operation then is val - 1, and the encode operation is val + 1
                final int xOff = (set & 3) - 1;
                final int zOff = ((set >>> 2) & 3) - 1;
                if (Math.abs(xOff) > 1 || Math.abs(zOff) > 1 || (xOff | zOff) == 0) {
                    throw new IllegalStateException();
                }
            }
        }

        // whether the increase propagator needs to write the propagated level to the position, used to avoid cascading
        // updates for sources
        private static final long FLAG_WRITE_LEVEL = Long.MIN_VALUE >>> 1;
        // whether the propagation needs to check if its current level is equal to the expected level
        // used only in increase propagation
        private static final long FLAG_RECHECK_LEVEL = Long.MIN_VALUE >>> 0;

        private long[] increaseQueue = new long[SECTION_SIZE * SECTION_SIZE * 2];
        private int increaseQueueInitialLength;
        private long[] decreaseQueue = new long[SECTION_SIZE * SECTION_SIZE * 2];
        private int decreaseQueueInitialLength;

        private final Long2ByteLinkedOpenHashMap updatedPositions = new Long2ByteLinkedOpenHashMap();

        private final long[] resizeIncreaseQueue() {
            return this.increaseQueue = Arrays.copyOf(this.increaseQueue, this.increaseQueue.length * 2);
        }

        private final long[] resizeDecreaseQueue() {
            return this.decreaseQueue = Arrays.copyOf(this.decreaseQueue, this.decreaseQueue.length * 2);
        }

        private final void appendToIncreaseQueue(final long value) {
            final int idx = this.increaseQueueInitialLength++;
            long[] queue = this.increaseQueue;
            if (idx >= queue.length) {
                queue = this.resizeIncreaseQueue();
                queue[idx] = value;
                return;
            } else {
                queue[idx] = value;
                return;
            }
        }

        private final void appendToDecreaseQueue(final long value) {
            final int idx = this.decreaseQueueInitialLength++;
            long[] queue = this.decreaseQueue;
            if (idx >= queue.length) {
                queue = this.resizeDecreaseQueue();
                queue[idx] = value;
                return;
            } else {
                queue[idx] = value;
                return;
            }
        }

        private final void performIncrease() {
            long[] queue = this.increaseQueue;
            int queueReadIndex = 0;
            int queueLength = this.increaseQueueInitialLength;
            this.increaseQueueInitialLength = 0;
            final int decodeOffsetX = -this.encodeOffsetX;
            final int decodeOffsetZ = -this.encodeOffsetZ;
            final int encodeOffset = this.coordinateOffset;
            final int sectionOffset = this.sectionIndexOffset;

            final Long2ByteLinkedOpenHashMap updatedPositions = this.updatedPositions;

            while (queueReadIndex < queueLength) {
                final long queueValue = queue[queueReadIndex++];

                final int posX = ((int)queueValue & (COORDINATE_SIZE - 1)) + decodeOffsetX;
                final int posZ = (((int)queueValue >>> COORDINATE_BITS) & (COORDINATE_SIZE - 1)) + decodeOffsetZ;
                final int propagatedLevel = ((int)queueValue >>> (COORDINATE_BITS + COORDINATE_BITS)) & (LEVEL_COUNT - 1);
                // note: the above code requires coordinate bits * 2 < 32
                // bitset is 16 bits
                int propagateDirectionBitset = (int)(queueValue >>> (COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)) & ((1 << 16) - 1);

                if ((queueValue & FLAG_RECHECK_LEVEL) != 0L) {
                    if (this.getLevel(posX, posZ) != propagatedLevel) {
                        // not at the level we expect, so something changed.
                        continue;
                    }
                } else if ((queueValue & FLAG_WRITE_LEVEL) != 0L) {
                    // these are used to restore sources after a propagation decrease
                    this.setLevel(posX, posZ, propagatedLevel);
                }

                // this bitset represents the values that we have not propagated to
                // this bitset lets us determine what directions the neighbours we set should propagate to, in most cases
                // significantly reducing the total number of ops
                // since we propagate in a 1 radius, we need a 2 radius bitset to hold all possible values we would possibly need
                // but if we use only 5x5 bits, then we need to use div/mod to retrieve coordinates from the bitset, so instead
                // we use an 8x8 bitset and luckily that can be fit into only one long value (64 bits)
                // to make things easy, we use positions [0, 4] in the bitset, with current position being 2
                // index = x | (z << 3)

                // to start, we eliminate everything 1 radius from the current position as the previous propagator
                // must guarantee that either we propagate everything in 1 radius or we partially propagate for 1 radius
                // but the rest not propagated are already handled
                long currentPropagation = ~(
                        // z = -1
                        (1L << ((2 - 1) | ((2 - 1) << 3))) |
                        (1L << ((2 + 0) | ((2 - 1) << 3))) |
                        (1L << ((2 + 1) | ((2 - 1) << 3))) |

                        // z = 0
                        (1L << ((2 - 1) | ((2 + 0) << 3))) |
                        (1L << ((2 + 0) | ((2 + 0) << 3))) |
                        (1L << ((2 + 1) | ((2 + 0) << 3))) |

                        // z = 1
                        (1L << ((2 - 1) | ((2 + 1) << 3))) |
                        (1L << ((2 + 0) | ((2 + 1) << 3))) |
                        (1L << ((2 + 1) | ((2 + 1) << 3)))
                );

                final int toPropagate = propagatedLevel - 1;

                // we could use while (propagateDirectionBitset != 0), but it's not a predictable branch. By counting
                // the bits, the cpu loop predictor should perfectly predict the loop.
                for (int l = 0, len = Integer.bitCount(propagateDirectionBitset); l < len; ++l) {
                    final int set = Integer.numberOfTrailingZeros(propagateDirectionBitset);
                    final int tailingBit = (-propagateDirectionBitset) & propagateDirectionBitset;
                    propagateDirectionBitset ^= tailingBit;

                    // pDecode is from [0, 2], and 1 must be subtracted to fully decode the offset
                    // it has been split to save some cycles via parallelism
                    final int pDecodeX = (set & 3);
                    final int pDecodeZ = ((set >>> 2) & 3);

                    // re-ordered -1 on the position decode into pos - 1 to occur in parallel with determining pDecodeX
                    final int offX = (posX - 1) + pDecodeX;
                    final int offZ = (posZ - 1) + pDecodeZ;

                    final int sectionIndex = (offX >> SECTION_SHIFT) + ((offZ >> SECTION_SHIFT) * SECTION_CACHE_WIDTH) + sectionOffset;
                    final int localIndex = (offX & (SECTION_SIZE - 1)) | ((offZ & (SECTION_SIZE - 1)) << SECTION_SHIFT);

                    // to retrieve a set of bits from a long value: (n_bitmask << (nstartidx)) & bitset
                    // bitset idx = x | (z << 3)

                    // read three bits, so we need 7L
                    // note that generally: off - pos = (pos - 1) + pDecode - pos = pDecode - 1
                    // nstartidx1 = x rel -1 for z rel -1
                    //            = (offX - posX - 1 + 2) | ((offZ - posZ - 1 + 2) << 3)
                    //            = (pDecodeX - 1 - 1 + 2) | ((pDecodeZ - 1 - 1 + 2) << 3)
                    //            = pDecodeX | (pDecodeZ << 3) = start
                    final int start = pDecodeX | (pDecodeZ << 3);
                    final long bitsetLine1 = currentPropagation & (7L << (start));

                    // nstartidx2 = x rel -1 for z rel 0 = line after line1, so we can just add 8 (row length of bitset)
                    final long bitsetLine2 = currentPropagation & (7L << (start + 8));

                    // nstartidx2 = x rel -1 for z rel 0 = line after line2, so we can just add 8 (row length of bitset)
                    final long bitsetLine3 = currentPropagation & (7L << (start + (8 + 8)));

                    // remove ("take") lines from bitset
                    currentPropagation ^= (bitsetLine1 | bitsetLine2 | bitsetLine3);

                    // now try to propagate
                    final Section section = this.sections[sectionIndex];

                    // lower 8 bits are current level, next upper 7 bits are source level, next 1 bit is updated source flag
                    final short currentStoredLevel = section.levels[localIndex];
                    final int currentLevel = currentStoredLevel & 0xFF;

                    if (currentLevel >= toPropagate) {
                        continue; // already at the level we want
                    }

                    // update level
                    section.levels[localIndex] = (short)((currentStoredLevel & ~0xFF) | (toPropagate & 0xFF));
                    updatedPositions.putAndMoveToLast(CoordinateUtils.getChunkKey(offX, offZ), (byte)toPropagate);

                    // queue next
                    if (toPropagate > 1) {
                        // now combine into one bitset to pass to child
                        // the child bitset is 4x4, so we just shift each line by 4
                        // add the propagation bitset offset to each line to make it easy to OR it into the propagation queue value
                        final long childPropagation =
                                ((bitsetLine1 >>> (start)) << (COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)) | // z = -1
                                ((bitsetLine2 >>> (start + 8)) << (4 + COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)) | // z = 0
                                ((bitsetLine3 >>> (start + (8 + 8))) << (4 + 4 + COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)); // z = 1

                        // don't queue update if toPropagate cannot propagate anything to neighbours
                        // (for increase, propagating 0 to neighbours is useless)
                        if (queueLength >= queue.length) {
                            queue = this.resizeIncreaseQueue();
                        }
                        queue[queueLength++] =
                                ((long)(offX + (offZ << COORDINATE_BITS) + encodeOffset) & ((1L << (COORDINATE_BITS + COORDINATE_BITS)) - 1)) |
                                ((toPropagate & (LEVEL_COUNT - 1L)) << (COORDINATE_BITS + COORDINATE_BITS)) |
                                childPropagation; //(ALL_DIRECTIONS_BITSET << (COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS));
                        continue;
                    }
                    continue;
                }
            }
        }

        private final void performDecrease() {
            long[] queue = this.decreaseQueue;
            long[] increaseQueue = this.increaseQueue;
            int queueReadIndex = 0;
            int queueLength = this.decreaseQueueInitialLength;
            this.decreaseQueueInitialLength = 0;
            int increaseQueueLength = this.increaseQueueInitialLength;
            final int decodeOffsetX = -this.encodeOffsetX;
            final int decodeOffsetZ = -this.encodeOffsetZ;
            final int encodeOffset = this.coordinateOffset;
            final int sectionOffset = this.sectionIndexOffset;

            final Long2ByteLinkedOpenHashMap updatedPositions = this.updatedPositions;

            while (queueReadIndex < queueLength) {
                final long queueValue = queue[queueReadIndex++];

                final int posX = ((int)queueValue & (COORDINATE_SIZE - 1)) + decodeOffsetX;
                final int posZ = (((int)queueValue >>> COORDINATE_BITS) & (COORDINATE_SIZE - 1)) + decodeOffsetZ;
                final int propagatedLevel = ((int)queueValue >>> (COORDINATE_BITS + COORDINATE_BITS)) & (LEVEL_COUNT - 1);
                // note: the above code requires coordinate bits * 2 < 32
                // bitset is 16 bits
                int propagateDirectionBitset = (int)(queueValue >>> (COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)) & ((1 << 16) - 1);

                // this bitset represents the values that we have not propagated to
                // this bitset lets us determine what directions the neighbours we set should propagate to, in most cases
                // significantly reducing the total number of ops
                // since we propagate in a 1 radius, we need a 2 radius bitset to hold all possible values we would possibly need
                // but if we use only 5x5 bits, then we need to use div/mod to retrieve coordinates from the bitset, so instead
                // we use an 8x8 bitset and luckily that can be fit into only one long value (64 bits)
                // to make things easy, we use positions [0, 4] in the bitset, with current position being 2
                // index = x | (z << 3)

                // to start, we eliminate everything 1 radius from the current position as the previous propagator
                // must guarantee that either we propagate everything in 1 radius or we partially propagate for 1 radius
                // but the rest not propagated are already handled
                long currentPropagation = ~(
                        // z = -1
                        (1L << ((2 - 1) | ((2 - 1) << 3))) |
                        (1L << ((2 + 0) | ((2 - 1) << 3))) |
                        (1L << ((2 + 1) | ((2 - 1) << 3))) |

                        // z = 0
                        (1L << ((2 - 1) | ((2 + 0) << 3))) |
                        (1L << ((2 + 0) | ((2 + 0) << 3))) |
                        (1L << ((2 + 1) | ((2 + 0) << 3))) |

                        // z = 1
                        (1L << ((2 - 1) | ((2 + 1) << 3))) |
                        (1L << ((2 + 0) | ((2 + 1) << 3))) |
                        (1L << ((2 + 1) | ((2 + 1) << 3)))
                );

                final int toPropagate = propagatedLevel - 1;

                // we could use while (propagateDirectionBitset != 0), but it's not a predictable branch. By counting
                // the bits, the cpu loop predictor should perfectly predict the loop.
                for (int l = 0, len = Integer.bitCount(propagateDirectionBitset); l < len; ++l) {
                    final int set = Integer.numberOfTrailingZeros(propagateDirectionBitset);
                    final int tailingBit = (-propagateDirectionBitset) & propagateDirectionBitset;
                    propagateDirectionBitset ^= tailingBit;


                    // pDecode is from [0, 2], and 1 must be subtracted to fully decode the offset
                    // it has been split to save some cycles via parallelism
                    final int pDecodeX = (set & 3);
                    final int pDecodeZ = ((set >>> 2) & 3);

                    // re-ordered -1 on the position decode into pos - 1 to occur in parallel with determining pDecodeX
                    final int offX = (posX - 1) + pDecodeX;
                    final int offZ = (posZ - 1) + pDecodeZ;

                    final int sectionIndex = (offX >> SECTION_SHIFT) + ((offZ >> SECTION_SHIFT) * SECTION_CACHE_WIDTH) + sectionOffset;
                    final int localIndex = (offX & (SECTION_SIZE - 1)) | ((offZ & (SECTION_SIZE - 1)) << SECTION_SHIFT);

                    // to retrieve a set of bits from a long value: (n_bitmask << (nstartidx)) & bitset
                    // bitset idx = x | (z << 3)

                    // read three bits, so we need 7L
                    // note that generally: off - pos = (pos - 1) + pDecode - pos = pDecode - 1
                    // nstartidx1 = x rel -1 for z rel -1
                    //            = (offX - posX - 1 + 2) | ((offZ - posZ - 1 + 2) << 3)
                    //            = (pDecodeX - 1 - 1 + 2) | ((pDecodeZ - 1 - 1 + 2) << 3)
                    //            = pDecodeX | (pDecodeZ << 3) = start
                    final int start = pDecodeX | (pDecodeZ << 3);
                    final long bitsetLine1 = currentPropagation & (7L << (start));

                    // nstartidx2 = x rel -1 for z rel 0 = line after line1, so we can just add 8 (row length of bitset)
                    final long bitsetLine2 = currentPropagation & (7L << (start + 8));

                    // nstartidx2 = x rel -1 for z rel 0 = line after line2, so we can just add 8 (row length of bitset)
                    final long bitsetLine3 = currentPropagation & (7L << (start + (8 + 8)));

                    // now try to propagate
                    final Section section = this.sections[sectionIndex];

                    // lower 8 bits are current level, next upper 7 bits are source level, next 1 bit is updated source flag
                    final short currentStoredLevel = section.levels[localIndex];
                    final int currentLevel = currentStoredLevel & 0xFF;
                    final int sourceLevel = (currentStoredLevel >>> 8) & 0xFF;

                    if (currentLevel == 0) {
                        continue; // already at the level we want
                    }

                    if (currentLevel > toPropagate) {
                        // it looks like another source propagated here, so re-propagate it
                        if (increaseQueueLength >= increaseQueue.length) {
                            increaseQueue = this.resizeIncreaseQueue();
                        }
                        increaseQueue[increaseQueueLength++] =
                                ((long)(offX + (offZ << COORDINATE_BITS) + encodeOffset) & ((1L << (COORDINATE_BITS + COORDINATE_BITS)) - 1)) |
                                ((currentLevel & (LEVEL_COUNT - 1L)) << (COORDINATE_BITS + COORDINATE_BITS)) |
                                (FLAG_RECHECK_LEVEL | (ALL_DIRECTIONS_BITSET << (COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)));
                        continue;
                    }

                    // remove ("take") lines from bitset
                    // can't do this during decrease, TODO WHY?
                    //currentPropagation ^= (bitsetLine1 | bitsetLine2 | bitsetLine3);

                    // update level
                    section.levels[localIndex] = (short)((currentStoredLevel & ~0xFF));
                    updatedPositions.putAndMoveToLast(CoordinateUtils.getChunkKey(offX, offZ), (byte)0);

                    if (sourceLevel != 0) {
                        // re-propagate source
                        // note: do not set recheck level, or else the propagation will fail
                        if (increaseQueueLength >= increaseQueue.length) {
                            increaseQueue = this.resizeIncreaseQueue();
                        }
                        increaseQueue[increaseQueueLength++] =
                                ((long)(offX + (offZ << COORDINATE_BITS) + encodeOffset) & ((1L << (COORDINATE_BITS + COORDINATE_BITS)) - 1)) |
                                ((sourceLevel & (LEVEL_COUNT - 1L)) << (COORDINATE_BITS + COORDINATE_BITS)) |
                                (FLAG_WRITE_LEVEL | (ALL_DIRECTIONS_BITSET << (COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)));
                    }

                    // queue next
                    // note: targetLevel > 0 here, since toPropagate >= currentLevel and currentLevel > 0
                    // now combine into one bitset to pass to child
                    // the child bitset is 4x4, so we just shift each line by 4
                    // add the propagation bitset offset to each line to make it easy to OR it into the propagation queue value
                    final long childPropagation =
                            ((bitsetLine1 >>> (start)) << (COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)) | // z = -1
                            ((bitsetLine2 >>> (start + 8)) << (4 + COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)) | // z = 0
                            ((bitsetLine3 >>> (start + (8 + 8))) << (4 + 4 + COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)); // z = 1

                    // don't queue update if toPropagate cannot propagate anything to neighbours
                    // (for increase, propagating 0 to neighbours is useless)
                    if (queueLength >= queue.length) {
                        queue = this.resizeDecreaseQueue();
                    }
                    queue[queueLength++] =
                            ((long)(offX + (offZ << COORDINATE_BITS) + encodeOffset) & ((1L << (COORDINATE_BITS + COORDINATE_BITS)) - 1)) |
                            ((toPropagate & (LEVEL_COUNT - 1L)) << (COORDINATE_BITS + COORDINATE_BITS)) |
                            (ALL_DIRECTIONS_BITSET << (COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS)); //childPropagation;
                    continue;
                }
            }

            // propagate sources we clobbered
            this.increaseQueueInitialLength = increaseQueueLength;
            this.performIncrease();
        }
    }

    /*
    private static final java.util.Random random = new java.util.Random(4L);
    private static final List<io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void>> walkers =
        new java.util.ArrayList<>();
    static final int PLAYERS = 0;
    static final int RAD_BLOCKS = 10000;
    static final int RAD = RAD_BLOCKS >> 4;
    static final int RAD_BIG_BLOCKS = 100_000;
    static final int RAD_BIG = RAD_BIG_BLOCKS >> 4;
    static final int VD = 4;
    static final int BIG_PLAYERS = 50;
    static final double WALK_CHANCE = 0.10;
    static final double TP_CHANCE = 0.01;
    static final int TP_BACK_PLAYERS = 200;
    static final double TP_BACK_CHANCE = 0.25;
    static final double TP_STEAL_CHANCE = 0.25;
    private static final List<io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void>> tpBack =
        new java.util.ArrayList<>();

    public static void main(final String[] args) {
        final ReentrantAreaLock ticketLock = new ReentrantAreaLock(SECTION_SHIFT);
        final ReentrantAreaLock schedulingLock = new ReentrantAreaLock(SECTION_SHIFT);
        final Long2ByteLinkedOpenHashMap levelMap = new Long2ByteLinkedOpenHashMap();
        final Long2ByteLinkedOpenHashMap refMap = new Long2ByteLinkedOpenHashMap();
        final io.papermc.paper.util.misc.Delayed8WayDistancePropagator2D ref = new io.papermc.paper.util.misc.Delayed8WayDistancePropagator2D((final long coordinate, final byte oldLevel, final byte newLevel) -> {
            if (newLevel == 0) {
                refMap.remove(coordinate);
            } else {
                refMap.put(coordinate, newLevel);
            }
        });
        final ThreadedTicketLevelPropagator propagator = new ThreadedTicketLevelPropagator() {
            @Override
            protected void processLevelUpdates(Long2ByteLinkedOpenHashMap updates) {
                for (final long key : updates.keySet()) {
                    final byte val = updates.get(key);
                    if (val == 0) {
                        levelMap.remove(key);
                    } else {
                        levelMap.put(key, val);
                    }
                }
            }

            @Override
            protected void processSchedulingUpdates(Long2ByteLinkedOpenHashMap updates, List<ChunkProgressionTask> scheduledTasks, List<NewChunkHolder> changedFullStatus) {}
        };

        for (;;) {
            if (walkers.isEmpty() && tpBack.isEmpty()) {
                for (int i = 0; i < PLAYERS; ++i) {
                    int rad = i < BIG_PLAYERS ? RAD_BIG : RAD;
                    int posX = random.nextInt(-rad, rad + 1);
                    int posZ = random.nextInt(-rad, rad + 1);

                    io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void> map = new io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<>(null) {
                        @Override
                        protected void addCallback(Void parameter, int chunkX, int chunkZ) {
                            int src = 45 - 31 + 1;
                            ref.setSource(chunkX, chunkZ, src);
                            propagator.setSource(chunkX, chunkZ, src);
                        }

                        @Override
                        protected void removeCallback(Void parameter, int chunkX, int chunkZ) {
                            ref.removeSource(chunkX, chunkZ);
                            propagator.removeSource(chunkX, chunkZ);
                        }
                    };

                    map.add(posX, posZ, VD);

                    walkers.add(map);
                }
                for (int i = 0; i < TP_BACK_PLAYERS; ++i) {
                    int rad = RAD_BIG;
                    int posX = random.nextInt(-rad, rad + 1);
                    int posZ = random.nextInt(-rad, rad + 1);

                    io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void> map = new io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<>(null) {
                        @Override
                        protected void addCallback(Void parameter, int chunkX, int chunkZ) {
                            int src = 45 - 31 + 1;
                            ref.setSource(chunkX, chunkZ, src);
                            propagator.setSource(chunkX, chunkZ, src);
                        }

                        @Override
                        protected void removeCallback(Void parameter, int chunkX, int chunkZ) {
                            ref.removeSource(chunkX, chunkZ);
                            propagator.removeSource(chunkX, chunkZ);
                        }
                    };

                    map.add(posX, posZ, random.nextInt(1, 63));

                    tpBack.add(map);
                }
            } else {
                for (int i = 0; i < PLAYERS; ++i) {
                    if (random.nextDouble() > WALK_CHANCE) {
                        continue;
                    }

                    io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void> map = walkers.get(i);

                    int updateX = random.nextInt(-1, 2);
                    int updateZ = random.nextInt(-1, 2);

                    map.update(map.lastChunkX + updateX, map.lastChunkZ + updateZ, VD);
                }

                for (int i = 0; i < PLAYERS; ++i) {
                    if (random.nextDouble() > TP_CHANCE) {
                        continue;
                    }

                    int rad = i < BIG_PLAYERS ? RAD_BIG : RAD;
                    int posX = random.nextInt(-rad, rad + 1);
                    int posZ = random.nextInt(-rad, rad + 1);

                    io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void> map = walkers.get(i);

                    map.update(posX, posZ, VD);
                }

                for (int i = 0; i < TP_BACK_PLAYERS; ++i) {
                    if (random.nextDouble() > TP_BACK_CHANCE) {
                        continue;
                    }

                    io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void> map = tpBack.get(i);

                    map.update(-map.lastChunkX, -map.lastChunkZ, random.nextInt(1, 63));

                    if (random.nextDouble() > TP_STEAL_CHANCE) {
                        propagator.performUpdate(
                            map.lastChunkX >> SECTION_SHIFT, map.lastChunkZ >> SECTION_SHIFT, schedulingLock, null, null
                        );
                        propagator.performUpdate(
                            (-map.lastChunkX >> SECTION_SHIFT), (-map.lastChunkZ >> SECTION_SHIFT), schedulingLock, null, null
                        );
                    }
                }
            }

            ref.propagateUpdates();
            propagator.performUpdates(ticketLock, schedulingLock, null, null);

            if (!refMap.equals(levelMap)) {
                throw new IllegalStateException("Error!");
            }
        }
    }
     */
}
