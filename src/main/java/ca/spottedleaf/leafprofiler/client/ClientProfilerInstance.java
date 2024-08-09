package ca.spottedleaf.leafprofiler.client;

import ca.spottedleaf.concurrentutil.executor.thread.PrioritisedThreadPool;
import ca.spottedleaf.leafprofiler.LProfileGraph;
import ca.spottedleaf.leafprofiler.LProfilerRegistry;
import ca.spottedleaf.leafprofiler.LeafProfiler;
import ca.spottedleaf.leafprofiler.TickAccumulator;
import ca.spottedleaf.leafprofiler.TickTime;
import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            LOGGER.warn("ClientProfilerInstance CPU time measurement is not available");
        }
    }

    private static final double LARGE_TICK_THRESHOLD = 1.0 - 0.05;

    private final Path root;
    private final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor dumpPool;
    private double userTickThreshold = -1;
    private double userRenderThreshold = -1;

    private Path currentPath;

    private final LProfilerRegistry registry = new LProfilerRegistry();
    private final TickAccumulator tickAccumulator = new TickAccumulator(TimeUnit.SECONDS.toNanos(1L));

    public final int clientFrame = this.registry.createType(LProfilerRegistry.ProfileType.TIMER, "Client Frame");
    public final int clientTick = this.registry.createType(LProfilerRegistry.ProfileType.TIMER, "Client Tick");

    private long previousTickStart = TickTime.DEADLINE_NOT_SET;
    private long tickStart = TickTime.DEADLINE_NOT_SET;
    private long tickStartCPU = TickTime.DEADLINE_NOT_SET;

    private LeafProfiler delayedFrameProfiler;
    private LeafProfiler frameProfiler;

    private long tick;

    private final List<LargeTick> largeTicks = new ArrayList<>();

    private static record LargeTick(long tickNum, LeafProfiler.ProfilingData profile) {}

    public ClientProfilerInstance() {
        this.root = Path.of("leafprofiler", "large_ticks");
        this.dumpPool = MoonriseCommon.PROFILER_DUMP_IO_GROUP.createExecutor(1, MoonriseCommon.IO_QUEUE_HOLD_TIME, 0);
        this.reset();
    }

    // TODO: Call when leaving server/SP world
    public void clearThresholds() {
        this.userTickThreshold = -1;
        this.userRenderThreshold = -1;
    }

    public void setThresholds(final double tickMs, final double renderMs) {
        this.userTickThreshold = tickMs;
        this.userRenderThreshold = renderMs;
        this.newDumpSession();
    }

    public void reset() {
        this.previousTickStart = TickTime.DEADLINE_NOT_SET;
        this.tickStart = TickTime.DEADLINE_NOT_SET;
        this.tickStartCPU = TickTime.DEADLINE_NOT_SET;
        this.tick = 0L;
        this.largeTicks.clear();
        this.delayedFrameProfiler = new LeafProfiler(this.registry, new LProfileGraph());
    }

    private void newDumpSession() {
        this.currentPath = this.root.resolve(String.valueOf(System.currentTimeMillis()));
        LOGGER.info("Profiler dumping to {}", this.currentPath);
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
                final LargeTick largeTick = new LargeTick(this.tick, this.frameProfiler.copyCurrent());
                this.largeTicks.add(largeTick);
                this.dumpIfOverThreshold(largeTick);
            }

            this.frameProfiler.accumulate();
        }
        this.frameProfiler = null;
        ++this.tick;
    }

    private void dumpIfOverThreshold(LargeTick largeTick) {
        boolean dump = false;
        if (this.userRenderThreshold >= 0) {
            if (largeTick.profile.timers()[this.clientFrame] / 1_000_000.0 >= this.userRenderThreshold) {
                dump = true;
            }
        }
        if (this.userTickThreshold >= 0) {
            if (largeTick.profile.timers()[this.clientTick] / 1_000_000.0 >= this.userTickThreshold) {
                dump = true;
            }
        }
        if (dump) {
            final Path path = this.currentPath.resolve(largeTick.tickNum + ".txt");
            this.dumpPool.queueTask(() -> {
                try {
                    if (!Files.isDirectory(path.getParent())) {
                        Files.createDirectories(path.getParent());
                    }
                    Files.write(
                        path,
                        largeTick.profile().dumpToString()
                    );
                } catch (final IOException e) {
                    throw new RuntimeException("Failed to dump large tick " + largeTick, e);
                }
            });
        }
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
