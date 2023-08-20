package ca.spottedleaf.moonrise.mixin.util_threading_detector;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import net.minecraft.util.ThreadingDetector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.lang.invoke.VarHandle;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(ThreadingDetector.class)
public abstract class ThreadingDetectorMixin {

    @Shadow
    @Final
    private String name;


    @Unique
    private static final ReentrantLock CANT_USE_NULL_IN_NEW_REDIRECT_MIXIN_WHAT_THE_FUCK_REENTRANTLOCK = new ReentrantLock();

    @Unique
    private static final Semaphore CANT_USE_NULL_IN_NEW_REDIRECT_MIXIN_WHAT_THE_FUCK_SEMAPHORE = new Semaphore(1);

    /**
     * @reason The lock is unused after changes in this mixin
     * @author Spottedleaf
     */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "()Ljava/util/concurrent/locks/ReentrantLock;"
            )
    )
    private static ReentrantLock nullLock() {
        return CANT_USE_NULL_IN_NEW_REDIRECT_MIXIN_WHAT_THE_FUCK_REENTRANTLOCK;
    }

    /**
     * @reason The semaphore is unused after changes in this mixin
     * @author Spottedleaf
     */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "(I)Ljava/util/concurrent/Semaphore;"
            )
    )
    private static Semaphore nullSemaphore(final int p1) {
        return CANT_USE_NULL_IN_NEW_REDIRECT_MIXIN_WHAT_THE_FUCK_SEMAPHORE;
    }

    // why bother with all of that locking crap when a single CAS works?

    // no unique to prevent renames
    private volatile Thread moonriseCurrThread;

    @Unique
    private static final VarHandle CURR_THREAD_HANDLE = ConcurrentUtil.getVarHandle(ThreadingDetector.class, "moonriseCurrThread", Thread.class);

    /**
     * @reason Replace with optimised version
     * @author Spottedleaf
     */
    @Overwrite
    public void checkAndLock() {
        final Thread prev = (Thread)CURR_THREAD_HANDLE.compareAndExchange((ThreadingDetector)(Object)this, (Thread)null, (Thread)Thread.currentThread());
        if (prev != null) {
            throw ThreadingDetector.makeThreadingException(this.name, prev);
        }
    }

    /**
     * @reason Replace with optimised version
     * @author Spottedleaf
     */
    @Overwrite
    public void checkAndUnlock() {
        final Thread expect = Thread.currentThread();

        final Thread prev = (Thread)CURR_THREAD_HANDLE.compareAndExchange((ThreadingDetector)(Object)this, expect, (Thread)null);

        if (prev != expect) {
            throw ThreadingDetector.makeThreadingException(this.name, prev);
        }
    }
}
