package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerInfo;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Iterator;
import java.util.function.Function;
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

    public MinecraftServerMixin(String string) {
        super(string);
    }

    @Unique
    private volatile Throwable chunkSystemCrash;

    @Override
    public final void moonrise$setChunkSystemCrash(final Throwable throwable) {
        this.chunkSystemCrash = throwable;
    }

    /**
     * @reason Force execution of tasks for all worlds, so that the first world does not hog all of the task processing
     * @author Spottedleaf
     */
    @Overwrite
    private boolean pollTaskInternal() {
        if (super.pollTask()) {
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
     * @reason Initialise chunk system threads hook
     * @author Spottedleaf
     */
    @Inject(
            method = "spin",
            at = @At(
                    value = "HEAD"
            )
    )
    private static <S> void initHook(Function<Thread, S> function, CallbackInfoReturnable<S> cir) {
        // TODO better place?
        ChunkTaskScheduler.init();
    }

    /**
     * @reason Make server thread an instance of TickThread for thread checks
     * @author Spottedleaf
     */
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
     * @reason Halt regionfile threads after everything is closed
     * @author Spottedleaf
     */
    @Inject(
            method = "stopServer",
            at = @At(
                    value = "RETURN"
            )
    )
    private void closeIOThreads(final CallbackInfo ci) {
        // TODO reinit code needs to be put somewhere
        RegionFileIOThread.deinit();
    }
}
