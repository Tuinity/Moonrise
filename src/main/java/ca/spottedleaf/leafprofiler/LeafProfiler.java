package ca.spottedleaf.leafprofiler;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.slf4j.Logger;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LeafProfiler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ThreadLocal<DecimalFormat> THREE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.000");
    });
    private static final ThreadLocal<DecimalFormat> NO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0");
    });

    public final LProfilerRegistry registry;
    private final LProfileGraph graph;

    private long[] accumulatedTimers = new long[0];
    private long[] accumulatedCounters = new long[0];

    private long[] timers = new long[16];
    private long[] counters = new long[16];
    private final IntArrayFIFOQueue callStack = new IntArrayFIFOQueue();
    private int topOfStack = LProfileGraph.ROOT_NODE;
    private final LongArrayFIFOQueue timerStack = new LongArrayFIFOQueue();
    private long lastTimerStart = 0L;

    public LeafProfiler(final LProfilerRegistry registry, final LProfileGraph graph) {
        this.registry = registry;
        this.graph = graph;
    }

    private static void add(final long[] dst, final long[] src) {
        for (int i = 0; i < src.length; ++i) {
            dst[i] += src[i];
        }
    }

    public ProfilingData copyCurrent() {
        return new ProfilingData(
                this.registry, this.graph, this.timers.clone(), this.counters.clone()
        );
    }

    public ProfilingData copyAccumulated() {
        return new ProfilingData(
                this.registry, this.graph, this.accumulatedTimers.clone(), this.accumulatedCounters.clone()
        );
    }

    public void accumulate() {
        if (this.accumulatedTimers.length != this.timers.length) {
            this.accumulatedTimers = Arrays.copyOf(this.accumulatedTimers, this.timers.length);
        }
        add(this.accumulatedTimers, this.timers);
        Arrays.fill(this.timers, 0L);

        if (this.accumulatedCounters.length != this.counters.length) {
            this.accumulatedCounters = Arrays.copyOf(this.accumulatedCounters, this.counters.length);
        }
        add(this.accumulatedCounters, this.counters);
        Arrays.fill(this.counters, 0L);
    }

    private long[] resizeTimers(final long[] old, final int least) {
        return this.timers = Arrays.copyOf(old, Math.max(old.length * 2, least * 2));
    }

    private void incrementTimersDirect(final int nodeId, final long count) {
        final long[] timers = this.timers;
        if (nodeId >= timers.length) {
            this.resizeTimers(timers, nodeId)[nodeId] += count;
        } else {
            timers[nodeId] += count;
        }
    }

    private long[] resizeCounters(final long[] old, final int least) {
        return this.counters = Arrays.copyOf(old, Math.max(old.length * 2, least * 2));
    }

    private void incrementCountersDirect(final int nodeId, final long count) {
        final long[] counters = this.counters;
        if (nodeId >= counters.length) {
            this.resizeCounters(counters, nodeId)[nodeId] += count;
        } else {
            counters[nodeId] += count;
        }
    }

    public void incrementCounter(final int timerId, final long count) {
        final int node = this.graph.getOrCreateNode(this.topOfStack, timerId);
        this.incrementCountersDirect(node, count);
    }

    public void incrementTimer(final int timerId, final long count) {
        final int node = this.graph.getOrCreateNode(this.topOfStack, timerId);
        this.incrementTimersDirect(node, count);
    }

    public void startTimer(final int timerId, final long startTime) {
        final long lastTimerStart = this.lastTimerStart;
        final LProfileGraph graph = this.graph;
        final int parentNode = this.topOfStack;
        final IntArrayFIFOQueue callStack = this.callStack;
        final LongArrayFIFOQueue timerStack = this.timerStack;

        this.lastTimerStart = startTime;
        this.topOfStack = graph.getOrCreateNode(parentNode, timerId);

        callStack.enqueue(parentNode);
        timerStack.enqueue(lastTimerStart);
    }

    public void stopTimer(final int timerId, final long endTime) {
        final long lastStart = this.lastTimerStart;
        final int currentNode = this.topOfStack;
        final IntArrayFIFOQueue callStack = this.callStack;
        final LongArrayFIFOQueue timerStack = this.timerStack;
        this.lastTimerStart = timerStack.dequeueLastLong();
        this.topOfStack = callStack.dequeueLastInt();

        if (currentNode != this.graph.getNode(this.topOfStack, timerId)) {
            final LProfilerRegistry.ProfilerEntry timer = this.registry.getById(timerId);
            throw new IllegalStateException("Timer " + (timer == null ? "null" : timer.name()) + " did not stop");
        }

        this.incrementTimersDirect(currentNode, endTime - lastStart);
        this.incrementCountersDirect(currentNode, 1L);
    }

    public void stopLastTimer(final long endTime) {
        final long lastStart = this.lastTimerStart;
        final int currentNode = this.topOfStack;
        final IntArrayFIFOQueue callStack = this.callStack;
        final LongArrayFIFOQueue timerStack = this.timerStack;
        this.lastTimerStart = timerStack.dequeueLastLong();
        this.topOfStack = callStack.dequeueLastInt();

        this.incrementTimersDirect(currentNode, endTime - lastStart);
        this.incrementCountersDirect(currentNode, 1L);
    }

    private static final class ProfileNode {

        public final ProfileNode parent;
        public final int nodeId;
        public final LProfilerRegistry.ProfilerEntry profiler;
        public final long totalTime;
        public final long totalCount;
        public final List<ProfileNode> children = new ArrayList<>();
        public long childrenTimingCount;
        public int depth = -1;

        private ProfileNode(final ProfileNode parent, final int nodeId, final LProfilerRegistry.ProfilerEntry profiler,
                            final long totalTime, final long totalCount) {
            this.parent = parent;
            this.nodeId = nodeId;
            this.profiler = profiler;
            this.totalTime = totalTime;
            this.totalCount = totalCount;
        }
    }



    public static final record ProfilingData(
            LProfilerRegistry registry,
            LProfileGraph graph,
            long[] timers,
            long[] counters
    ) {
        private static final char[][] INDENT_PATTERNS = new char[][] {
                "|---".toCharArray(),
                "|+++".toCharArray(),
        };

        public List<String> dumpToString() {
            final List<LProfileGraph.GraphNode> graphDFS = this.graph.getDFS();
            final Reference2ReferenceOpenHashMap<LProfileGraph.GraphNode, ProfileNode> nodeMap = new Reference2ReferenceOpenHashMap<>();

            final ArrayDeque<ProfileNode> orderedNodes = new ArrayDeque<>();

            for (int i = 0, len = graphDFS.size(); i < len; ++i) {
                final LProfileGraph.GraphNode graphNode = graphDFS.get(i);
                final ProfileNode parent = nodeMap.get(graphNode.parent());
                final int nodeId = graphNode.nodeId();

                final long totalTime = nodeId >= this.timers.length ? 0L : this.timers[nodeId];
                final long totalCount = nodeId >= this.counters.length ? 0L : this.counters[nodeId];
                final LProfilerRegistry.ProfilerEntry profiler = this.registry.getById(graphNode.timerId());

                final ProfileNode profileNode = new ProfileNode(parent, nodeId, profiler, totalTime, totalCount);

                if (parent != null) {
                    parent.childrenTimingCount += totalTime;
                    parent.children.add(profileNode);
                } else if (i != 0) { // i == 0 is root
                    throw new IllegalStateException("Node " + nodeId + " must have parent");
                } else {
                    // set up
                    orderedNodes.add(profileNode);
                }

                nodeMap.put(graphNode, profileNode);
            }

            final List<String> ret = new ArrayList<>();

            long totalTime = 0L;

            // totalTime = sum of times for root node's children
            for (final ProfileNode node : orderedNodes.peekFirst().children) {
                totalTime += node.totalTime;
            }

            ProfileNode profileNode;
            final StringBuilder builder = new StringBuilder();
            while ((profileNode = orderedNodes.pollFirst()) != null) {
                if (profileNode.nodeId != LProfileGraph.ROOT_NODE && profileNode.totalCount == 0L) {
                    // skip nodes not recorded
                    continue;
                }

                final int depth = profileNode.depth;
                profileNode.children.sort((final ProfileNode p1, final ProfileNode p2) -> {
                    final int typeCompare = p1.profiler.type().compareTo(p2.profiler.type());
                    if (typeCompare != 0) {
                        // first count, then profiler
                        return typeCompare;
                    }

                    if (p1.profiler.type() == LProfilerRegistry.ProfileType.COUNTER) {
                        // highest count first
                        return Long.compare(p2.totalCount, p1.totalCount);
                    } else {
                        // highest time first
                        return Long.compare(p2.totalTime, p1.totalTime);
                    }
                });

                for (int i = profileNode.children.size() - 1; i >= 0; --i) {
                    final ProfileNode child = profileNode.children.get(i);
                    child.depth = depth + 1;
                    orderedNodes.addFirst(child);
                }

                if (profileNode.nodeId == LProfileGraph.ROOT_NODE) {
                    // don't display root
                    continue;
                }

                final boolean noParent = profileNode.parent == null || profileNode.parent.nodeId == LProfileGraph.ROOT_NODE;

                final long parentTime = noParent ? totalTime : profileNode.parent.totalTime;
                final LProfilerRegistry.ProfilerEntry profilerEntry = profileNode.profiler;

                // format:
                // For profiler type:
                // <indent><name> X% total, Y% parent, self A% total, self B% children, avg X sum Y, Dms raw sum
                // For counter type:
                // <indent>#<name> avg X sum Y
                builder.setLength(0);
                // prepare indent
                final char[] indent = INDENT_PATTERNS[ret.size() % INDENT_PATTERNS.length];
                for (int i = 0; i < depth; ++i) {
                    builder.append(indent);
                }

                switch (profilerEntry.type()) {
                    case TIMER: {
                        ret.add(
                                builder
                                        .append(profilerEntry.name())
                                        .append(' ')
                                        .append(THREE_DECIMAL_PLACES.get().format(((double)profileNode.totalTime / (double)totalTime) * 100.0))
                                        .append("% total, ")
                                        .append(THREE_DECIMAL_PLACES.get().format(((double)profileNode.totalTime / (double)parentTime) * 100.0))
                                        .append("% parent, self ")
                                        .append(THREE_DECIMAL_PLACES.get().format(((double)(profileNode.totalTime - profileNode.childrenTimingCount) / (double)totalTime) * 100.0))
                                        .append("% total, self ")
                                        .append(THREE_DECIMAL_PLACES.get().format(((double)(profileNode.totalTime - profileNode.childrenTimingCount) / (double)profileNode.totalTime) * 100.0))
                                        .append("% children, avg ")
                                        .append(THREE_DECIMAL_PLACES.get().format((double)profileNode.totalCount / (double)(noParent ? 1L : profileNode.parent.totalCount)))
                                        .append(" sum ")
                                        .append(NO_DECIMAL_PLACES.get().format(profileNode.totalCount))
                                        .append(", ")
                                        .append(THREE_DECIMAL_PLACES.get().format((double)profileNode.totalTime / 1.0E6))
                                        .append("ms raw sum")
                                        .toString()
                        );
                        break;
                    }
                    case COUNTER: {
                        ret.add(
                                builder
                                        .append('#')
                                        .append(profilerEntry.name())
                                        .append(" avg ")
                                        .append(THREE_DECIMAL_PLACES.get().format((double)profileNode.totalCount / (double)(noParent ? 1L : profileNode.parent.totalCount)))
                                        .append(" sum ")
                                        .append(NO_DECIMAL_PLACES.get().format(profileNode.totalCount))
                                        .toString()
                        );
                        break;
                    }
                    default: {
                        throw new IllegalStateException("Unknown type " + profilerEntry.type());
                    }
                }
            }

            return ret;
        }
    }

    /*
    public static void main(final String[] args) throws Throwable {
        final Thread timerHack = new Thread("Timer hack thread") {
            @Override
            public void run() {
                for (;;) {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (final InterruptedException ex) {
                        continue;
                    }
                }
            }
        };
        timerHack.setDaemon(true);
        timerHack.start();

        final LProfilerRegistry registry = new LProfilerRegistry();

        final int tickId = registry.createType(LProfilerRegistry.ProfileType.TIMER, "tick");
        final int entityTickId = registry.createType(LProfilerRegistry.ProfileType.TIMER, "entity tick");
        final int getEntitiesId = registry.createType(LProfilerRegistry.ProfileType.COUNTER, "getEntities call");
        final int tileEntityId = registry.createType(LProfilerRegistry.ProfileType.TIMER, "tile entity tick");
        final int creeperEntityId = registry.createType(LProfilerRegistry.ProfileType.TIMER, "creeper entity tick");
        final int furnaceId = registry.createType(LProfilerRegistry.ProfileType.TIMER, "furnace tile entity tick");

        final LeafProfiler profiler = new LeafProfiler(registry, new LProfileGraph());

        profiler.startTimer(tickId, System.nanoTime());
        Thread.sleep(10L);

        profiler.startTimer(entityTickId, System.nanoTime());
        Thread.sleep(1L);

        profiler.startTimer(creeperEntityId, System.nanoTime());
        Thread.sleep(15L);
        profiler.incrementCounter(getEntitiesId, 50L);
        profiler.stopTimer(creeperEntityId, System.nanoTime());

        profiler.stopTimer(entityTickId, System.nanoTime());

        profiler.startTimer(tileEntityId, System.nanoTime());
        Thread.sleep(1L);

        profiler.startTimer(furnaceId, System.nanoTime());
        Thread.sleep(20L);
        profiler.stopTimer(furnaceId, System.nanoTime());

        profiler.stopTimer(tileEntityId, System.nanoTime());

        profiler.stopTimer(tickId, System.nanoTime());

        System.out.println("Done.");
    }
     */
}
