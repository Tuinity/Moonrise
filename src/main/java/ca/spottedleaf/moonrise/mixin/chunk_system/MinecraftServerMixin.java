package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerInfo;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.TickTask;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends ReentrantBlockableEventLoop<TickTask> implements ChunkSystemMinecraftServer, ServerInfo, CommandSource, AutoCloseable {

    @Shadow
    public abstract Iterable<ServerLevel> getAllLevels();

    @Shadow
    public abstract boolean saveAllChunks(boolean bl, boolean bl2, boolean bl3);

    @Shadow
    @Final
    private ServerTickRateManager tickRateManager;

    @Shadow
    protected abstract boolean haveTime();

    @Shadow
    @Final
    private static Logger LOGGER;

    public MinecraftServerMixin(String string) {
        super(string);
    }

    @Unique
    private volatile Throwable chunkSystemCrash;

    @Override
    public final void moonrise$setChunkSystemCrash(final Throwable throwable) {
        this.chunkSystemCrash = throwable;
    }

    @Unique
    private static final long CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME = 25L * 1000L; // 25us
    @Unique
    private static final long MAX_CHUNK_EXEC_TIME = 1000L; // 1us
    @Unique
    private static final long TASK_EXECUTION_FAILURE_BACKOFF = 5L * 1000L; // 5us

    @Unique
    private long lastMidTickExecute;
    @Unique
    private long lastMidTickExecuteFailure;

    @Unique
    private boolean tickMidTickTasks() {
        // give all worlds a fair chance at by targeting them all.
        // if we execute too many tasks, that's fine - we have logic to correctly handle overuse of allocated time.
        boolean executed = false;
        for (final ServerLevel world : this.getAllLevels()) {
            long currTime = System.nanoTime();
            if (currTime - ((ChunkSystemServerLevel)world).moonrise$getLastMidTickFailure() <= TASK_EXECUTION_FAILURE_BACKOFF) {
                continue;
            }
            if (!world.getChunkSource().pollTask()) {
                // we need to back off if this fails
                ((ChunkSystemServerLevel)world).moonrise$setLastMidTickFailure(currTime);
            } else {
                executed = true;
            }
        }

        return executed;
    }

    @Override
    public final void moonrise$executeMidTickTasks() {
        final long startTime = System.nanoTime();
        if ((startTime - this.lastMidTickExecute) <= CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME || (startTime - this.lastMidTickExecuteFailure) <= TASK_EXECUTION_FAILURE_BACKOFF) {
            // it's shown to be bad to constantly hit the queue (chunk loads slow to a crawl), even if no tasks are executed.
            // so, backoff to prevent this
            return;
        }

        for (;;) {
            final boolean moreTasks = this.tickMidTickTasks();
            final long currTime = System.nanoTime();
            final long diff = currTime - startTime;

            if (!moreTasks || diff >= MAX_CHUNK_EXEC_TIME) {
                if (!moreTasks) {
                    this.lastMidTickExecuteFailure = currTime;
                }

                // note: negative values reduce the time
                long overuse = diff - MAX_CHUNK_EXEC_TIME;
                if (overuse >= (10L * 1000L * 1000L)) { // 10ms
                    // make sure something like a GC or dumb plugin doesn't screw us over...
                    overuse = 10L * 1000L * 1000L; // 10ms
                }

                final double overuseCount = (double)overuse/(double)MAX_CHUNK_EXEC_TIME;
                final long extraSleep = (long)Math.round(overuseCount*CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME);

                this.lastMidTickExecute = currTime + extraSleep;
                return;
            }
        }
    }

    /**
     * @reason Force execution of tasks for all worlds, so that the first world does not hog the task processing time.
     *         Additionally, perform mid-tick task execution when handling the normal server queue so that chunk tasks
     *         are guaranteed to be processed during tick sleep.
     * @author Spottedleaf
     */
    @Overwrite
    private boolean pollTaskInternal() {
        if (super.pollTask()) {
            this.moonrise$executeMidTickTasks();
            return true;
        }

        if (this.tickRateManager.isSprinting() || this.haveTime()) {
            boolean ret = false;
            for (final ServerLevel world : this.getAllLevels()) {
                if (world.getChunkSource().pollTask()) {
                    ret = true;
                }
            }

            return ret;
        }

        return false;
    }


    /**
     * @reason Force response to chunk system crash
     * @author Spottedleaf
     */
    @Inject(
            method = "runServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void hookChunkSystemCrash(final CallbackInfo ci) {
        final Throwable crash = this.chunkSystemCrash;
        if (crash != null) {
            this.chunkSystemCrash = null;
            throw new RuntimeException("Chunk system crash propagated to tick()", crash);
        }
    }

    /**
     * @reason Make server thread an instance of TickThread for thread checks
     * @author Spottedleaf
     */
    /* TODO NeoForge adds ThreadGroup
    @Redirect(
            method = "spin",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/lang/Runnable;Ljava/lang/String;)Ljava/lang/Thread;"
            )
    )
    private static Thread createTickThread(final Runnable target, final String name) {
        return new TickThread(target, name);
    }
     */

    /**
     * @reason Make server thread an instance of TickThread for thread checks
     * @author Spottedleaf
     */
    @Redirect(
            method = "spin",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;Ljava/lang/String;)Ljava/lang/Thread;"
            )
    )
    private static Thread createTickThreadNeo(final ThreadGroup group, final Runnable task, final String name) {
        return new TickThread(task, name, group);
    }


    /**
     * @reason Close logic is re-written so that we do not wait for tasks to complete and unload everything
     *         but rather we halt all task processing and then save.
     *         The reason this is done is that the server may not be in a state where the chunk system can
     *         complete its tasks, which would prevent the saving of any data. The new close logic will ensure
     *         that if the system is deadlocked that both a full save will occur and that the server will halt.
     * @author Spottedleaf
     */
    @Redirect(
            method = "stopServer",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;anyMatch(Ljava/util/function/Predicate;)Z",
                    ordinal = 0
            )
    )
    private boolean doNotWaitChunkSystemShutdown(final Stream<ServerLevel> instance, final Predicate<? super ServerLevel> predicate) {
        return false;
    }

    /**
     * @reason Mark all ServerLevel instances as being closed so that saveAllChunks can perform a close-save operation.
     *         Additionally, sets force = true, so that the save call is guaranteed to be converted into a close-save call.
     * @author Spottedleaf
     */
    @Redirect(
            method = "stopServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;saveAllChunks(ZZZ)Z"
            )
    )
    private boolean markClosed(final MinecraftServer instance, boolean bl, boolean bl2, boolean bl3) {
        for (final ServerLevel world : this.getAllLevels()) {
            ((ChunkSystemServerLevel)world).moonrise$setMarkedClosing(true);
        }
        // !log, flush, force
        return this.saveAllChunks(false, true, true);
    }

    /**
     * @reason Close is handled above
     * @author Spottedleaf
     */
    @Redirect(
            method = "stopServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;close()V",
                    ordinal = 0
            )
    )
    private void noOpClose(final ServerLevel instance) {}

    /**
     * @reason Halt all executors
     * @author Spottedleaf
     */
    @Inject(
            method = "stopServer",
            at = @At(
                    value = "RETURN"
            )
    )
    private void closeIOThreads(final CallbackInfo ci) {
        LOGGER.info("Waiting for I/O tasks to complete...");
        MoonriseRegionFileIO.flush((MinecraftServer)(Object)this);
        if ((Object)this instanceof DedicatedServer) {
            MoonriseCommon.haltExecutors();
        }
    }
}
