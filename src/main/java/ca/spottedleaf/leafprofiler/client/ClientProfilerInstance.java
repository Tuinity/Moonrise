package ca.spottedleaf.leafprofiler.client;

import ca.spottedleaf.leafprofiler.LProfileGraph;
import ca.spottedleaf.leafprofiler.LProfilerRegistry;
import ca.spottedleaf.leafprofiler.LeafProfiler;
import ca.spottedleaf.leafprofiler.TickAccumulator;
import ca.spottedleaf.leafprofiler.TickTime;
import com.mojang.logging.LogUtils;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;
import org.slf4j.Logger;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ClientProfilerInstance implements ProfilerFiller {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();
    private static final boolean MEASURE_CPU_TIME;
    static {
        MEASURE_CPU_TIME = THREAD_MX_BEAN.isThreadCpuTimeSupported();
        if (MEASURE_CPU_TIME) {
            THREAD_MX_BEAN.setThreadCpuTimeEnabled(true);
        } else {
            LOGGER.warn("TickRegionScheduler CPU time measurement is not available");
        }
    }

    private final LProfilerRegistry registry = new LProfilerRegistry();
    private final TickAccumulator tickAccumulator = new TickAccumulator(TimeUnit.SECONDS.toNanos(1L));

    public final int clientFrame = this.registry.createType(LProfilerRegistry.ProfileType.TIMER, "Client Frame");
    public final int clientTick = this.registry.createType(LProfilerRegistry.ProfileType.TIMER, "Client Tick");

    private long previousTickStart = TickTime.DEADLINE_NOT_SET;
    private long tickStart = TickTime.DEADLINE_NOT_SET;
    private long tickStartCPU = TickTime.DEADLINE_NOT_SET;

    private LeafProfiler delayedFrameProfiler;
    private LeafProfiler frameProfiler;

    private static final double LARGE_TICK_THRESHOLD = 1.0 - 0.05;

    private long tick;

    private final List<LargeTick> largeTicks = new ArrayList<>();

    private static record LargeTick(long tickNum, LeafProfiler.ProfilingData profile) {}

    public void startProfiler() {
        this.delayedFrameProfiler = new LeafProfiler(this.registry, new LProfileGraph());
    }

    public void stopProfiler() {
        this.delayedFrameProfiler = null;
    }

    @Override
    public void startTick() {
        final long time = System.nanoTime();
        final long cpuTime = MEASURE_CPU_TIME ? THREAD_MX_BEAN.getCurrentThreadCpuTime() : 0L;
        this.previousTickStart = this.tickStart;
        this.tickStart = time;
        this.tickStartCPU = cpuTime;

        this.frameProfiler = this.delayedFrameProfiler;
        if (this.frameProfiler != null) {
            this.frameProfiler.startTimer(this.clientFrame, time);
        }
    }

    @Override
    public void endTick() {
        final long cpuTime = MEASURE_CPU_TIME ? THREAD_MX_BEAN.getCurrentThreadCpuTime() : 0L;
        final long time = System.nanoTime();

        final TickTime tickTime = new TickTime(
                this.previousTickStart, this.tickStart, this.tickStart, this.tickStartCPU, time, cpuTime, MEASURE_CPU_TIME
        );

        final int index = this.tickAccumulator.addDataFrom(tickTime);
        final int count = this.tickAccumulator.size();

        if (this.frameProfiler != null) {
            this.frameProfiler.stopTimer(this.clientFrame, time);

            if (count == 1 || (double)index/(double)(count - 1) >= LARGE_TICK_THRESHOLD) {
                this.largeTicks.add(new LargeTick(this.tick, this.frameProfiler.copyCurrent()));
            }

            this.frameProfiler.accumulate();
        }
        this.frameProfiler = null;
        ++this.tick;
    }

    public void startRealClientTick() {
        if (this.frameProfiler != null) {
            this.frameProfiler.startTimer(this.clientTick, System.nanoTime());
        }
    }

    public void endRealClientTick() {
        if (this.frameProfiler != null) {
            this.frameProfiler.stopTimer(this.clientTick, System.nanoTime());
        }
    }

    @Override
    public void push(final String string) {
        final LeafProfiler frameProfiler = this.frameProfiler;
        if (frameProfiler == null) {
            return;
        }

        final long time = System.nanoTime();
        final int timerId = frameProfiler.registry.getOrCreateTimer(string);

        frameProfiler.startTimer(timerId, time);
    }

    @Override
    public void push(final Supplier<String> supplier) {
        final LeafProfiler frameProfiler = this.frameProfiler;
        if (frameProfiler == null) {
            return;
        }

        final long time = System.nanoTime();
        final int timerId = frameProfiler.registry.getOrCreateTimer(supplier.get());

        frameProfiler.startTimer(timerId, time);
    }

    @Override
    public void pop() {
        final LeafProfiler frameProfiler = this.frameProfiler;
        if (frameProfiler == null) {
            return;
        }

        final long time = System.nanoTime();

        frameProfiler.stopLastTimer(time);
    }

    @Override
    public void popPush(final String string) {
        this.pop();
        this.push(string);
    }

    @Override
    public void popPush(final Supplier<String> supplier) {
        this.pop();
        this.push(supplier);
    }

    @Override
    public void markForCharting(final MetricCategory metricCategory) {
        // what the fuck is this supposed to do?
    }

    @Override
    public void incrementCounter(final String string) {
        this.incrementCounter(string, 1);
    }

    @Override
    public void incrementCounter(final String string, final int i) {
        final LeafProfiler frameProfiler = this.frameProfiler;
        if (frameProfiler == null) {
            return;
        }

        final int timerId = frameProfiler.registry.getOrCreateCounter(string);

        frameProfiler.incrementCounter(timerId, (long)i);
    }

    @Override
    public void incrementCounter(final Supplier<String> supplier) {
        this.incrementCounter(supplier, 1);
    }

    @Override
    public void incrementCounter(final Supplier<String> supplier, final int i) {
        final LeafProfiler frameProfiler = this.frameProfiler;
        if (frameProfiler == null) {
            return;
        }

        final int timerId = frameProfiler.registry.getOrCreateCounter(supplier.get());

        frameProfiler.incrementCounter(timerId, (long)i);
    }
}
