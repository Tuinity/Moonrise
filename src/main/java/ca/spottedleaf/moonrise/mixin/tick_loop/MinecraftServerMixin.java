package ca.spottedleaf.moonrise.mixin.tick_loop;

import ca.spottedleaf.moonrise.common.config.moonrise.MoonriseConfig;
import ca.spottedleaf.moonrise.common.util.ConfigHolder;
import ca.spottedleaf.moonrise.common.util.Schedule;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer;
import ca.spottedleaf.moonrise.patches.tick_loop.TickLoopBlockableEventLoop;
import ca.spottedleaf.moonrise.patches.tick_loop.TickLoopPacketProcessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Util;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.PacketProcessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerInfo;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin extends ReentrantBlockableEventLoop<TickTask> implements ServerInfo, CommandSource, ChunkIOErrorReporter {

    @Shadow
    @Final
    private PacketProcessor packetProcessor;

    @Shadow
    private long nextTickTimeNanos;

    @Shadow
    public abstract Iterable<ServerLevel> getAllLevels();

    @Shadow
    @Final
    private ServerTickRateManager tickRateManager;

    @Shadow
    public abstract boolean isPaused();

    @Shadow
    private long lastOverloadWarningNanos;

    @Shadow
    protected abstract void startMeasuringTaskExecutionTime();

    @Shadow
    protected abstract void finishMeasuringTaskExecutionTime();

    @Shadow
    private boolean waitingForNextTick;

    public MinecraftServerMixin(final String name) {
        super(name);
    }

    // firstPeriod is set on init
    @Unique
    private final Schedule tickSchedule = new Schedule(0L);

    /**
     * @reason Init the tickSchedule so that it syncs with the nextTickTimeNanos field.
     * @author Spottedleaf
     */
    @Inject(
        method = "runServer",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/server/MinecraftServer;nextTickTimeNanos:J",
            opcode = Opcodes.PUTFIELD,
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void initTickSchedule(final CallbackInfo ci) {
        final long interval;
        if (this.isPaused() || !this.tickRateManager.isSprinting()) {
            interval = this.tickRateManager.nanosecondsPerTick();
        } else {
            interval = 0L;
        }
        this.tickSchedule.setNextPeriod(this.nextTickTimeNanos, interval);
    }

    /**
     * @reason Force execution into single branch for us to handle updating tickSchedule
     * @author Spottedleaf
     */
    @Redirect(
        method = "runServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;isPaused()Z",
            ordinal = 0
        )
    )
    private boolean shortSprintBranch(final MinecraftServer instance) {
        return true; // -> !true -> false -> shorts the branch
    }

    /**
     * @reason Implement tick schedule update for limiting the catchup ticks.
     * @author Spottedleaf
     */
    @Redirect(
        method = "runServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/ServerTickRateManager;nanosecondsPerTick()J",
            ordinal = 0
        )
    )
    private long updateTickSchedule(final ServerTickRateManager tickRateManager) {
        final long now = Util.getNanos();

        // note: we MUST return 0 when sprinting, see the branch above which we shorted
        final boolean sprint = !this.isPaused() && tickRateManager.isSprinting() && tickRateManager.checkShouldSprintThisTick();
        final long interval;
        if (sprint) {
            interval = 0L;
            // force update interval to use now as next period
            this.tickSchedule.setNextPeriod(now, interval);
        } else {
            interval = tickRateManager.nanosecondsPerTick();

            // handle catchup logic
            final long ticksBehind = this.tickSchedule.getPeriodsAhead(interval, now);
            final long catchup = (long)Math.max(
                1,
                ConfigHolder.getConfig().tickLoop.catchupTicks.getOrDefault(MoonriseConfig.TickLoop.DEFAULT_CATCHUP_TICKS).intValue()
            );

            // adjust ticksBehind so that it is not greater-than catchup
            if (ticksBehind > catchup) {
                final long difference = ticksBehind - catchup;
                this.tickSchedule.advanceBy(difference, interval);
            }

            // start next tick
            this.tickSchedule.advanceBy(1L, interval);
        }

        this.nextTickTimeNanos = this.tickSchedule.getDeadline(interval);
        // disable overloaded logic: the logging, and the updating of nextTickTimeNanos
        this.lastOverloadWarningNanos = this.nextTickTimeNanos;

        return interval;
    }

    /**
     * @reason We update the field above to target the next tick already
     * @author Spottedleaf
     */
    @Redirect(
        method = "runServer",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/server/MinecraftServer;nextTickTimeNanos:J",
            opcode = Opcodes.PUTFIELD,
            ordinal = 3
        )
    )
    private void dropNextTickTimeInc(final MinecraftServer instance, final long value) {}

    /**
     * @reason Do not delay queued tasks up to 3 ticks. Instead, change the delay so that there is
     *         at most 1 tick of processing delay. Combined with the mixin below, this ensures that
     *         all tasks queued before a tick starts will be executed on that tick.
     * @author Spottedleaf
     */
    @ModifyConstant(
        method = "shouldRun(Lnet/minecraft/server/TickTask;)Z",
        constant = @Constant(
            intValue = 3, ordinal = 0
        )
    )
    private int changeMaxTaskDelay(final int constant) {
        return 1;
    }

    /**
     * @reason Even if the server is falling behind in ticks, we want to ensure that we process immediately all
     *         player packets so that perceived latency from players is minimized. We need to run this after incrementing
     *         the tickCount field so that all tasks queued before the tick start are guaranteed to run (see above mixin).
     *         We also want to process all chunk system tasks as well.
     * @author Spottedleaf
     */
    @Inject(
        method = "runServer",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/jtracy/DiscontinuousFrame;start()V",
            shift = At.Shift.AFTER
        )
    )
    private void runAllTasksAtStart(final CallbackInfo ci) {
        this.startMeasuringTaskExecutionTime();

        // note: To avoid possibly spinning forever, only execute tasks that are roughly available at the beginning
        //       of this call. Packet processing and chunk system tasks are possibly always being queued.
        final ProfilerFiller profiler = Profiler.get();
        profiler.push("moonrise:run_all_tasks");

        profiler.push("moonrise:run_all_server");
        // avoid calling MinecraftServer#pollTask - we just want to execute queued tasks
        while (super.pollTask()) {
            // execute small amounts of other tasks just in case the number of tasks we are
            // draining is large - chunk system and packet processing may be latency sensitive

            ((ChunkSystemMinecraftServer)this).moonrise$executeMidTickTasks();
            ((TickLoopPacketProcessor)this.packetProcessor).moonrise$executeSinglePacket();
        }
        profiler.popPush("moonrise:run_all_packets");
        while (((TickLoopPacketProcessor)this.packetProcessor).moonrise$executeSinglePacket()) {
            // execute possibly latency sensitive chunk system tasks (see above)
            ((ChunkSystemMinecraftServer)this).moonrise$executeMidTickTasks();
        }
        profiler.popPush("moonrise:run_all_chunk");
        for (final ServerLevel world : this.getAllLevels()) {
            profiler.push(world.toString() + " " + world.dimension().location()); // keep same formatting from regular tick, see tickChildren

            // note: legacy tasks may expect a distance manager update
            profiler.push("moonrise:distance_manager_update");
            ((ChunkSystemServerLevel)world).moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates();
            profiler.popPush("moonrise:legacy_chunk_tasks");
            ((TickLoopBlockableEventLoop)(Object)world.getChunkSource().mainThreadProcessor).moonrise$executeAllRecentInternalTasks();
            profiler.popPush("moonrise:chunk_system_tasks");
            ((ChunkSystemServerLevel)world).moonrise$getChunkTaskScheduler().executeAllRecentlyQueuedMainThreadTasks();
            profiler.pop();

            profiler.pop(); // world name
        }
        profiler.pop(); // moonrise:run_all_chunk
        profiler.pop(); // moonrise:run_all_tasks

        this.finishMeasuringTaskExecutionTime();
    }

    /**
     * @reason Change the behavior of this function so that it only blocks until the next tick starts.
     *         We move the execution of all tasks to the beginning of the tick.
     * @author Spottedleaf
     */
    @Overwrite
    public void waitUntilNextTick() {
        final ProfilerFiller profiler = Profiler.get();

        profiler.push("moonrise:wait_for_next_tick");
        this.waitingForNextTick = true;
        try {
            this.managedBlock(() -> {
                // done if time in next time >= 0
                return Util.getNanos() - this.nextTickTimeNanos >= 0L;
            });
        } finally {
            this.waitingForNextTick = false;
        }
        profiler.pop();
    }

    /**
     * @reason Make executions of pollTask execute packets. This allows packets
     *         to be processed while waiting for the next tick - lowering perceived
     *         latency on servers that are close to idle.
     *         Note: We change packet processing to unpark the main thread when packets
     *         are queued to force a timely response.
     * @author Spottedleaf
     */
    @WrapOperation(
        method = "pollTask",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;pollTaskInternal()Z"
        )
    )
    private boolean makePollExecutePacket(final MinecraftServer instance, final Operation<Boolean> original) {
        return ((TickLoopPacketProcessor)instance.packetProcessor()).moonrise$executeSinglePacket() | original.call(instance).booleanValue();
    }
}
