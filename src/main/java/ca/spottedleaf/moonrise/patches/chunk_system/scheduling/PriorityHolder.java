package ca.spottedleaf.moonrise.patches.chunk_system.scheduling;

import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import java.lang.invoke.VarHandle;

public abstract class PriorityHolder {

    protected volatile int priority;
    protected static final VarHandle PRIORITY_HANDLE = ConcurrentUtil.getVarHandle(PriorityHolder.class, "priority", int.class);

    protected static final int PRIORITY_SCHEDULED = Integer.MIN_VALUE >>> 0;
    protected static final int PRIORITY_EXECUTED  = Integer.MIN_VALUE >>> 1;

    protected final int getPriorityVolatile() {
        return (int)PRIORITY_HANDLE.getVolatile((PriorityHolder)this);
    }

    protected final int compareAndExchangePriorityVolatile(final int expect, final int update) {
        return (int)PRIORITY_HANDLE.compareAndExchange((PriorityHolder)this, (int)expect, (int)update);
    }

    protected final int getAndOrPriorityVolatile(final int val) {
        return (int)PRIORITY_HANDLE.getAndBitwiseOr((PriorityHolder)this, (int)val);
    }

    protected final void setPriorityPlain(final int val) {
        PRIORITY_HANDLE.set((PriorityHolder)this, (int)val);
    }

    protected PriorityHolder(final PrioritisedExecutor.Priority priority) {
        if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }
        this.setPriorityPlain(priority.priority);
    }

    // used only for debug json
    public boolean isScheduled() {
        return (this.getPriorityVolatile() & PRIORITY_SCHEDULED) != 0;
    }

    // returns false if cancelled
    public boolean markExecuting() {
        return (this.getAndOrPriorityVolatile(PRIORITY_EXECUTED) & PRIORITY_EXECUTED) == 0;
    }

    public boolean isMarkedExecuted() {
        return (this.getPriorityVolatile() & PRIORITY_EXECUTED) != 0;
    }

    public void cancel() {
        if ((this.getAndOrPriorityVolatile(PRIORITY_EXECUTED) & PRIORITY_EXECUTED) != 0) {
            // cancelled already
            return;
        }
        this.cancelScheduled();
    }

    public void schedule() {
        int priority = this.getPriorityVolatile();

        if ((priority & PRIORITY_SCHEDULED) != 0) {
            throw new IllegalStateException("schedule() called twice");
        }

        if ((priority & PRIORITY_EXECUTED) != 0) {
            // cancelled
            return;
        }

        this.scheduleTask(PrioritisedExecutor.Priority.getPriority(priority));

        int failures = 0;
        for (;;) {
            if (priority == (priority = this.compareAndExchangePriorityVolatile(priority, priority | PRIORITY_SCHEDULED))) {
                return;
            }

            if ((priority & PRIORITY_SCHEDULED) != 0) {
                throw new IllegalStateException("schedule() called twice");
            }

            if ((priority & PRIORITY_EXECUTED) != 0) {
                // cancelled or executed
                return;
            }

            this.setPriorityScheduled(PrioritisedExecutor.Priority.getPriority(priority));

            ++failures;
            for (int i = 0; i < failures; ++i) {
                ConcurrentUtil.backoff();
            }
        }
    }

    public final PrioritisedExecutor.Priority getPriority() {
        final int ret = this.getPriorityVolatile();
        if ((ret & PRIORITY_EXECUTED) != 0) {
            return PrioritisedExecutor.Priority.COMPLETING;
        }
        if ((ret & PRIORITY_SCHEDULED) != 0) {
            return this.getScheduledPriority();
        }
        return PrioritisedExecutor.Priority.getPriority(ret);
    }

    public final void lowerPriority(final PrioritisedExecutor.Priority priority) {
        if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }

        int failures = 0;
        for (int curr = this.getPriorityVolatile();;) {
            if ((curr & PRIORITY_EXECUTED) != 0) {
                return;
            }

            if ((curr & PRIORITY_SCHEDULED) != 0) {
                this.lowerPriorityScheduled(priority);
                return;
            }

            if (!priority.isLowerPriority(curr)) {
                return;
            }

            if (curr == (curr = this.compareAndExchangePriorityVolatile(curr, priority.priority))) {
                return;
            }

            // failed, retry

            ++failures;
            for (int i = 0; i < failures; ++i) {
                ConcurrentUtil.backoff();
            }
        }
    }

    public final void setPriority(final PrioritisedExecutor.Priority priority) {
        if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }

        int failures = 0;
        for (int curr = this.getPriorityVolatile();;) {
            if ((curr & PRIORITY_EXECUTED) != 0) {
                return;
            }

            if ((curr & PRIORITY_SCHEDULED) != 0) {
                this.setPriorityScheduled(priority);
                return;
            }

            if (curr == (curr = this.compareAndExchangePriorityVolatile(curr, priority.priority))) {
                return;
            }

            // failed, retry

            ++failures;
            for (int i = 0; i < failures; ++i) {
                ConcurrentUtil.backoff();
            }
        }
    }

    public final void raisePriority(final PrioritisedExecutor.Priority priority) {
        if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }

        int failures = 0;
        for (int curr = this.getPriorityVolatile();;) {
            if ((curr & PRIORITY_EXECUTED) != 0) {
                return;
            }

            if ((curr & PRIORITY_SCHEDULED) != 0) {
                this.raisePriorityScheduled(priority);
                return;
            }

            if (!priority.isHigherPriority(curr)) {
                return;
            }

            if (curr == (curr = this.compareAndExchangePriorityVolatile(curr, priority.priority))) {
                return;
            }

            // failed, retry

            ++failures;
            for (int i = 0; i < failures; ++i) {
                ConcurrentUtil.backoff();
            }
        }
    }

    protected abstract void cancelScheduled();

    protected abstract PrioritisedExecutor.Priority getScheduledPriority();

    protected abstract void scheduleTask(final PrioritisedExecutor.Priority priority);

    protected abstract void lowerPriorityScheduled(final PrioritisedExecutor.Priority priority);

    protected abstract void setPriorityScheduled(final PrioritisedExecutor.Priority priority);

    protected abstract void raisePriorityScheduled(final PrioritisedExecutor.Priority priority);
}
