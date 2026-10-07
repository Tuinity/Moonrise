package ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import java.util.ArrayDeque;

public final class ThreadLocalFixedDensityBufferCache {

    private final ArrayDeque<ThreadLocalDensityBuffer> buffersCached = new ArrayDeque<>();
    private final ArrayDeque<ThreadLocalDensityBuffer> buffersInUse = new ArrayDeque<>();

    private boolean inUse;

    public ThreadLocalFixedDensityBufferCache acquire() {
        if (this.inUse) {
            throw new IllegalStateException();
        }
        this.inUse = true;
        return this;
    }

    public void release() {
        if (!this.inUse) {
            throw new IllegalStateException();
        }
        this.inUse = false;
        this.finished();
    }

    public DensityBuffer allocate(final int size) {
        if (!this.inUse) {
            throw new IllegalStateException();
        }
        // note: we expect size (and count) to be constant per run, so this terrible strategy actually works

        if (size < 0) {
            throw new IllegalArgumentException();
        }

        ThreadLocalDensityBuffer buffer = this.buffersCached.pollFirst();

        if (buffer != null && buffer.capacity() >= size) {
            buffer.adjustSize(size);
            buffer.fill(0.0f);
            this.buffersInUse.addLast(buffer);
            return buffer;
        }

        buffer = new ThreadLocalDensityBuffer(size + Math.max(1, size >> 1));
        buffer.adjustSize(size);
        this.buffersInUse.addLast(buffer);
        return buffer;
    }

    public void finished() {
        ThreadLocalDensityBuffer buffer;
        while ((buffer = this.buffersInUse.pollFirst()) != null) {
            this.buffersCached.addFirst(buffer);
        }
    }

    private static final class ThreadLocalDensityBuffer extends DensityBuffer {

        ThreadLocalDensityBuffer(final int size) {
            super(size);
        }

        void adjustSize(final int size) {
            this.size = size;
        }
    }
}
