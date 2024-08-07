package ca.spottedleaf.moonrise.mixin.util_threading_detector;

import net.minecraft.util.ThreadingDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(ThreadingDetector.class)
abstract class ThreadingDetectorMixin {

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

    /**
     * @reason Remove
     * @author Spottedleaf
     */
    @Overwrite
    public void checkAndLock() {}

    /**
     * @reason Remove
     * @author Spottedleaf
     */
    @Overwrite
    public void checkAndUnlock() {}
}
