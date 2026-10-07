package ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc;

import ca.spottedleaf.common.time.Schedule;
import ca.spottedleaf.concurrentutil.list.COWArrayList;
import ca.spottedleaf.concurrentutil.map.concurrent.ints.ConcurrentChainedInt2ObjectHashTable;
import ca.spottedleaf.moonrise.common.PlatformHooks;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.slf4j.Logger;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

// extend DensityBufferPool so that we can be used in place of it

/**
 * The thread local allocator is used for a couple of reasons:
 * <ol>
 *     <li>
 *         Vanilla limits the number of pools to 16. This means for a thread count > 16, the allocation rate will explode
 *         due to each additional thread not being able to re-use buffers.
 *     </li>
 *     <li>
 *         Vanilla does not properly return all buffers to the pool. This forces those buffers to be re-allocated.
 *     </li>
 * </ol>
 */
public final class ThreadLocalDensityBufferArena extends DensityBufferPool {

    private static final ThreadLocal<ThreadLocalDensityBufferArena> INSTANCE = ThreadLocal.withInitial(() -> {
        final ThreadLocalDensityBufferArena ret = new ThreadLocalDensityBufferArena();
        GarbageCollector.INSTANCE.register(ret, Thread.currentThread());
        return ret;
    });

    private static final int SMALL_THRESHOLD = 4;
    private static final int ROUND_SHIFT = 4;
    private static final int ROUND_THRESHOLD = 1 << ROUND_SHIFT;

    private final ConcurrentChainedInt2ObjectHashTable<ConcurrentLinkedDeque<ScopedDensityBuffer>> buffersByBucketSize = new ConcurrentChainedInt2ObjectHashTable<>();
    private final ReferenceLinkedOpenHashSet<ScopedDensityBuffer> notYetReturned = new ReferenceLinkedOpenHashSet<>();

    public ThreadLocalDensityBufferArena() {
        super(GarbageCollector.MAX_AGE_TICKS);
    }

    public static ThreadLocalDensityBufferArena acquire() {
        final ThreadLocalDensityBufferArena ret = INSTANCE.get();
        INSTANCE.set(null);
        return ret;
    }

    public static void release(final ThreadLocalDensityBufferArena pool) {
        if (pool == null) {
            return;
        }
        INSTANCE.set(pool);
        pool.releaseAllUnreturned();
    }

    private ScopedDensityBuffer acquireForBucket(final int bucket) {
        final ConcurrentLinkedDeque<ScopedDensityBuffer> buffers = this.buffersByBucketSize.get(bucket);
        return buffers == null ? null : buffers.pollFirst();
    }

    private void releaseIntoBucket(final ScopedDensityBuffer buffer, final int bucket) {
        this.buffersByBucketSize.computeIfAbsent(bucket, (final int keyInMap) -> {
            return new ConcurrentLinkedDeque<>();
        }).addFirst(buffer);
    }

    @Override
    public ScopedDensityBuffer acquire(final int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }

        final int requestedCap;
        if (size <= SMALL_THRESHOLD) {
            final ScopedDensityBuffer ret = this.acquireForBucket(SMALL_THRESHOLD);
            if (ret != null) {
                this.notYetReturned.add(ret);
                ret.restore(size);
                return ret;
            }
            requestedCap = SMALL_THRESHOLD;
        } else {
            final int rounded = ((size + (ROUND_THRESHOLD - 1)) >> ROUND_SHIFT) << ROUND_SHIFT;
            final ScopedDensityBuffer ret = this.acquireForBucket(rounded);
            if (ret != null) {
                this.notYetReturned.add(ret);
                ret.restore(size);
                return ret;
            }
            requestedCap = rounded;
        }

        final ScopedDensityBuffer ret = new ScopedDensityBuffer(this, requestedCap, size);
        this.notYetReturned.add(ret);
        return ret;
    }

    @Override
    public void release(final ScopedDensityBuffer buffer) {
        this.notYetReturned.remove(buffer);
        this.releaseIntoBucket(buffer, buffer.capacity());
    }

    // Note: This is specifically to reclaim buffers not returned by SamplerContext CacheCells
    public void releaseAllUnreturned() {
        while (!this.notYetReturned.isEmpty()) {
            this.notYetReturned.first().close();
        }
    }

    @Override
    public void garbageCollect() {
        for (final ConcurrentChainedInt2ObjectHashTable.TableEntry<ConcurrentLinkedDeque<ScopedDensityBuffer>> entry : this.buffersByBucketSize) {
            for (final Iterator<ScopedDensityBuffer> iterator = entry.getValue().iterator(); iterator.hasNext();) {
                final ScopedDensityBuffer buffer = iterator.next();
                // note: races on the age field are benign
                if (buffer.incrementAge() > this.maxAge) {
                    // note: race condition here where the value is polled does not matter
                    iterator.remove();
                }
            }
        }
    }

    @Override
    public void clear() {
        this.buffersByBucketSize.clear();
        this.notYetReturned.clear();
    }

    @Override
    public boolean isEmpty() {
        return this.buffersByBucketSize.isEmpty();
    }

    private static final class GarbageCollector extends Thread {

        private static final GarbageCollector INSTANCE = new GarbageCollector();
        private static final Logger LOGGER = LogUtils.getLogger();
        static {
            INSTANCE.setName(PlatformHooks.get().getBrand() + " DensityBuffer garbage collector");
            INSTANCE.setDaemon(true);
            INSTANCE.setUncaughtExceptionHandler((final Thread thread, final Throwable throwable) -> {
                LOGGER.error("Uncaught exception in thread " + thread.getName() + ": ", throwable);
            });
            INSTANCE.start();
        }

        private static record RegisteredPool(ThreadLocalDensityBufferArena arena, Thread forThread) {}

        private static final long TICK_INTERVAL = TimeUnit.MILLISECONDS.toNanos(500L);
        private static final int MAX_AGE_TICKS = (int)((TimeUnit.SECONDS.toNanos(5L) + (TICK_INTERVAL - 1)) / TICK_INTERVAL);

        private final COWArrayList<RegisteredPool> registered = new COWArrayList<>(RegisteredPool.class);
        private final Schedule schedule = new Schedule(System.nanoTime());

        public void register(final ThreadLocalDensityBufferArena arena, final Thread forThread) {
            this.registered.add(new RegisteredPool(arena, forThread));
        }

        private void tick() {
            for (final RegisteredPool pool : this.registered.getArray()) {
                if (!pool.forThread.isAlive()) {
                    this.registered.remove(pool);
                    continue;
                }
                pool.arena.garbageCollect();
            }
        }

        @Override
        public void run() {
            for (;;) {
                final long deadline = this.schedule.getDeadline(TICK_INTERVAL);
                long toWait;
                while ((toWait = (deadline - System.nanoTime())) > 0L) {
                    Thread.interrupted();
                    LockSupport.parkNanos(toWait);
                }
                // use zero catchup
                final long ticksAhead = Math.max(1L, this.schedule.getPeriodsAhead(TICK_INTERVAL, System.nanoTime()));
                this.schedule.advanceBy(ticksAhead, TICK_INTERVAL);

                this.tick();
            }
        }
    }
}
