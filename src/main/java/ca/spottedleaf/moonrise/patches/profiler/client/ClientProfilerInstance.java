package ca.spottedleaf.moonrise.patches.profiler.client;

import ca.spottedleaf.concurrentutil.executor.thread.PrioritisedThreadPool;
import ca.spottedleaf.moonrise.patches.profiler.LProfileGraph;
import ca.spottedleaf.moonrise.patches.profiler.LProfilerRegistry;
import ca.spottedleaf.moonrise.patches.profiler.LeafProfiler;
import ca.spottedleaf.moonrise.patches.profiler.TickTime;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    private final Path root;
    private final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor dumpPool;

    private final LProfilerRegistry registry = new LProfilerRegistry();

    public final int clientFrame = this.registry.createType(LProfilerRegistry.ProfileType.TIMER, "Client Frame");
    public final int clientTick = this.registry.createType(LProfilerRegistry.ProfileType.TIMER, "Client Tick");

    private long previousTickStart = TickTime.DEADLINE_NOT_SET;
    private long tickStart = TickTime.DEADLINE_NOT_SET;
    private long tickStartCPU = TickTime.DEADLINE_NOT_SET;

    private LeafProfiler delayedFrameProfiler;
    private LeafProfiler frameProfiler;

    private long tick;

    private Path sessionPath;
    // ns
    private long averageThreshold;
    // ns
    private long recordThreshold;

    private final List<RecordedTick> recordedTicks = new ArrayList<>();

    private static record RecordedTick(
        TickTime tickTime, long tickNum, LeafProfiler.ProfilingData profilingData
    ) {}

    public ClientProfilerInstance() {
        this.root = Path.of("moonrise", "profiler", "large_ticks");
        this.dumpPool = MoonriseCommon.CLIENT_PROFILER_IO_GROUP.createExecutor(1, MoonriseCommon.IO_QUEUE_HOLD_TIME, 0);
    }

    private void reset() {
        this.previousTickStart = TickTime.DEADLINE_NOT_SET;
        this.tickStart = TickTime.DEADLINE_NOT_SET;
        this.tickStartCPU = TickTime.DEADLINE_NOT_SET;
        this.tick = 0L;
        this.delayedFrameProfiler = null;
        this.sessionPath = null;
        this.averageThreshold = 0L;
        this.recordThreshold = 0L;
        this.recordedTicks.clear();
    }

    public boolean startSession(final long averageThresholdNS, final long recordThresholdNS)  {
        if (this.sessionPath != null) {
            return false;
        }

        final String sessionId = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").format(LocalDateTime.now());
        this.sessionPath = this.root.resolve(sessionId);
        this.averageThreshold = averageThresholdNS < 0L ? Long.MAX_VALUE : averageThresholdNS;
        this.recordThreshold = recordThresholdNS < 0L ? Long.MAX_VALUE : recordThresholdNS;
        this.delayedFrameProfiler = new LeafProfiler(this.registry, new LProfileGraph());

        LOGGER.info("Starting client profiler with avg_threshold=" + averageThresholdNS + "rec_threshold=" + recordThresholdNS + ",sessionId=" + sessionId);

        return true;
    }

    public boolean endSession() {
        if (this.sessionPath == null) {
            return false;
        }

        LOGGER.info("Ending client profiler session");

        this.write();

        this.reset();
        return true;
    }

    private void writeTick(final RecordedTick recordedTick) {
        final Path path = this.sessionPath.resolve(recordedTick.tickNum + ".txt");

        this.dumpPool.queueTask(() -> {
            try {
                if (!Files.isDirectory(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                Files.write(
                    path,
                    recordedTick.profilingData().dumpToString()
                );
            } catch (final IOException ex) {
                throw new RuntimeException("Failed to write tick " + recordedTick, ex);
            }
        });
    }

    private void writeAverages() {
        final Path path = this.sessionPath.resolve("averages.txt");

        final LeafProfiler.ProfilingData profilingData = this.delayedFrameProfiler.copyAccumulated();

        this.dumpPool.queueTask(() -> {
            try {
                if (!Files.isDirectory(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                Files.write(
                    path,
                    profilingData.dumpToString()
                );
            } catch (final IOException ex) {
                throw new RuntimeException("Failed to write averages", ex);
            }
        });
    }

    private void write() {
        for (final RecordedTick tick : this.recordedTicks) {
            this.writeTick(tick);
        }

        this.writeAverages();
    }

    public boolean isActive() {
        return this.delayedFrameProfiler != null;
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

        if (this.frameProfiler != null) {
            this.frameProfiler.stopTimer(this.clientFrame, time);

            if (tickTime.tickLength() >= this.recordThreshold) {
                this.recordedTicks.add(new RecordedTick(tickTime, this.tick, this.frameProfiler.copyCurrent()));
            }

            if (tickTime.tickLength() >= this.averageThreshold) {
                this.frameProfiler.accumulate();
            } else {
                this.frameProfiler.clearCurrent();
            }
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
