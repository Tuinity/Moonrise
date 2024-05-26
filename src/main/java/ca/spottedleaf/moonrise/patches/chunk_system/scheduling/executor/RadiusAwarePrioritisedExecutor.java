package ca.spottedleaf.moonrise.patches.chunk_system.scheduling.executor;

import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class RadiusAwarePrioritisedExecutor {

    private static final Comparator<DependencyNode> DEPENDENCY_NODE_COMPARATOR = (final DependencyNode t1, final DependencyNode t2) -> {
        return Long.compare(t1.id, t2.id);
    };

    private final DependencyTree[] queues = new DependencyTree[PrioritisedExecutor.Priority.TOTAL_SCHEDULABLE_PRIORITIES];
    private static final int NO_TASKS_QUEUED = -1;
    private int selectedQueue = NO_TASKS_QUEUED;
    private boolean canQueueTasks = true;

    public RadiusAwarePrioritisedExecutor(final PrioritisedExecutor executor, final int maxToSchedule) {
        for (int i = 0; i < this.queues.length; ++i) {
            this.queues[i] = new DependencyTree(this, executor, maxToSchedule, i);
        }
    }

    private boolean canQueueTasks() {
        return this.canQueueTasks;
    }

    private List<PrioritisedExecutor.PrioritisedTask> treeFinished() {
        this.canQueueTasks = true;
        for (int priority = 0; priority < this.queues.length; ++priority) {
            final DependencyTree queue = this.queues[priority];
            if (queue.hasWaitingTasks()) {
                final List<PrioritisedExecutor.PrioritisedTask> ret = queue.tryPushTasks();

                if (ret == null || ret.isEmpty()) {
                    // this happens when the tasks in the wait queue were purged
                    // in this case, the queue was actually empty, we just had to purge it
                    // if we set the selected queue without scheduling any tasks, the queue will never be unselected
                    // as that requires a scheduled task completing...
                    continue;
                }

                this.selectedQueue = priority;
                return ret;
            }
        }

        this.selectedQueue = NO_TASKS_QUEUED;

        return null;
    }

    private List<PrioritisedExecutor.PrioritisedTask> queue(final Task task, final PrioritisedExecutor.Priority priority) {
        final int priorityId = priority.priority;
        final DependencyTree queue = this.queues[priorityId];

        final DependencyNode node = new DependencyNode(task, queue);

        if (task.dependencyNode != null) {
            throw new IllegalStateException();
        }
        task.dependencyNode = node;

        queue.pushNode(node);

        if (this.selectedQueue == NO_TASKS_QUEUED) {
            this.canQueueTasks = true;
            this.selectedQueue = priorityId;
            return queue.tryPushTasks();
        }

        if (!this.canQueueTasks) {
            return null;
        }

        if (PrioritisedExecutor.Priority.isHigherPriority(priorityId, this.selectedQueue)) {
            // prevent the lower priority tree from queueing more tasks
            this.canQueueTasks = false;
            return null;
        }

        // priorityId != selectedQueue: lower priority, don't care - treeFinished will pick it up
        return priorityId == this.selectedQueue ? queue.tryPushTasks() : null;
    }

    public PrioritisedExecutor.PrioritisedTask createTask(final int chunkX, final int chunkZ, final int radius,
                                                          final Runnable run, final PrioritisedExecutor.Priority priority) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must be > 0: " + radius);
        }
        return new Task(this, chunkX, chunkZ, radius, run, priority);
    }

    public PrioritisedExecutor.PrioritisedTask createTask(final int chunkX, final int chunkZ, final int radius,
                                                          final Runnable run) {
        return this.createTask(chunkX, chunkZ, radius, run, PrioritisedExecutor.Priority.NORMAL);
    }

    public PrioritisedExecutor.PrioritisedTask queueTask(final int chunkX, final int chunkZ, final int radius,
                                                         final Runnable run, final PrioritisedExecutor.Priority priority) {
        final PrioritisedExecutor.PrioritisedTask ret = this.createTask(chunkX, chunkZ, radius, run, priority);

        ret.queue();

        return ret;
    }

    public PrioritisedExecutor.PrioritisedTask queueTask(final int chunkX, final int chunkZ, final int radius,
                                                         final Runnable run) {
        final PrioritisedExecutor.PrioritisedTask ret = this.createTask(chunkX, chunkZ, radius, run);

        ret.queue();

        return ret;
    }

    public PrioritisedExecutor.PrioritisedTask createInfiniteRadiusTask(final Runnable run, final PrioritisedExecutor.Priority priority) {
        return new Task(this, 0, 0, -1, run, priority);
    }

    public PrioritisedExecutor.PrioritisedTask createInfiniteRadiusTask(final Runnable run) {
        return this.createInfiniteRadiusTask(run, PrioritisedExecutor.Priority.NORMAL);
    }

    public PrioritisedExecutor.PrioritisedTask queueInfiniteRadiusTask(final Runnable run, final PrioritisedExecutor.Priority priority) {
        final PrioritisedExecutor.PrioritisedTask ret = this.createInfiniteRadiusTask(run, priority);

        ret.queue();

        return ret;
    }

    public PrioritisedExecutor.PrioritisedTask queueInfiniteRadiusTask(final Runnable run) {
        final PrioritisedExecutor.PrioritisedTask ret = this.createInfiniteRadiusTask(run, PrioritisedExecutor.Priority.NORMAL);

        ret.queue();

        return ret;
    }

    // all accesses must be synchronised by the radius aware object
    private static final class DependencyTree {

        private final RadiusAwarePrioritisedExecutor scheduler;
        private final PrioritisedExecutor executor;
        private final int maxToSchedule;
        private final int treeIndex;

        private int currentlyExecuting;
        private long idGenerator;

        private final PriorityQueue<DependencyNode> awaiting = new PriorityQueue<>(DEPENDENCY_NODE_COMPARATOR);

        private final PriorityQueue<DependencyNode> infiniteRadius = new PriorityQueue<>(DEPENDENCY_NODE_COMPARATOR);
        private boolean isInfiniteRadiusScheduled;

        private final Long2ReferenceOpenHashMap<DependencyNode> nodeByPosition = new Long2ReferenceOpenHashMap<>();

        public DependencyTree(final RadiusAwarePrioritisedExecutor scheduler, final PrioritisedExecutor executor,
                              final int maxToSchedule, final int treeIndex) {
            this.scheduler = scheduler;
            this.executor = executor;
            this.maxToSchedule = maxToSchedule;
            this.treeIndex = treeIndex;
        }

        public boolean hasWaitingTasks() {
            return !this.awaiting.isEmpty() || !this.infiniteRadius.isEmpty();
        }

        private long nextId() {
            return this.idGenerator++;
        }

        private boolean isExecutingAnyTasks() {
            return this.currentlyExecuting != 0;
        }

        private void pushNode(final DependencyNode node) {
            if (!node.task.isFiniteRadius()) {
                this.infiniteRadius.add(node);
                return;
            }

            // set up dependency for node
            final Task task = node.task;

            final int centerX = task.chunkX;
            final int centerZ = task.chunkZ;
            final int radius = task.radius;

            final int minX = centerX - radius;
            final int maxX = centerX + radius;

            final int minZ = centerZ - radius;
            final int maxZ = centerZ + radius;

            ReferenceOpenHashSet<DependencyNode> parents = null;
            for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                for (int currX = minX; currX <= maxX; ++currX) {
                    final DependencyNode dependency = this.nodeByPosition.put(CoordinateUtils.getChunkKey(currX, currZ), node);
                    if (dependency != null) {
                        if (parents == null) {
                            parents = new ReferenceOpenHashSet<>();
                        }
                        if (parents.add(dependency)) {
                            // added a dependency, so we need to add as a child to the dependency
                            if (dependency.children == null) {
                                dependency.children = new ArrayList<>();
                            }
                            dependency.children.add(node);
                        }
                    }
                }
            }

            if (parents == null) {
                // no dependencies, add straight to awaiting
                this.awaiting.add(node);
            } else {
                node.parents = parents.size();
                // we will be added to awaiting once we have no parents
            }
        }

        // called only when a node is returned after being executed
        private List<PrioritisedExecutor.PrioritisedTask> returnNode(final DependencyNode node) {
            final Task task = node.task;

            // now that the task is completed, we can push its children to the awaiting queue
            this.pushChildren(node);

            if (task.isFiniteRadius()) {
                // remove from dependency map
                this.removeNodeFromMap(node);
            } else {
                // mark as no longer executing infinite radius
                if (!this.isInfiniteRadiusScheduled) {
                    throw new IllegalStateException();
                }
                this.isInfiniteRadiusScheduled = false;
            }

            // decrement executing count, we are done executing this task
            --this.currentlyExecuting;

            if (this.currentlyExecuting == 0) {
                return this.scheduler.treeFinished();
            }

            return this.scheduler.canQueueTasks() ? this.tryPushTasks() : null;
        }

        private List<PrioritisedExecutor.PrioritisedTask> tryPushTasks() {
            // tasks are not queued, but only created here - we do hold the lock for the map
            List<PrioritisedExecutor.PrioritisedTask> ret = null;
            PrioritisedExecutor.PrioritisedTask pushedTask;
            while ((pushedTask = this.tryPushTask()) != null) {
                if (ret == null) {
                    ret = new ArrayList<>();
                }
                ret.add(pushedTask);
            }

            return ret;
        }

        private void removeNodeFromMap(final DependencyNode node) {
            final Task task = node.task;

            final int centerX = task.chunkX;
            final int centerZ = task.chunkZ;
            final int radius = task.radius;

            final int minX = centerX - radius;
            final int maxX = centerX + radius;

            final int minZ = centerZ - radius;
            final int maxZ = centerZ + radius;

            for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                for (int currX = minX; currX <= maxX; ++currX) {
                    this.nodeByPosition.remove(CoordinateUtils.getChunkKey(currX, currZ), node);
                }
            }
        }

        private void pushChildren(final DependencyNode node) {
            // add all the children that we can into awaiting
            final List<DependencyNode> children = node.children;
            if (children != null) {
                for (int i = 0, len = children.size(); i < len; ++i) {
                    final DependencyNode child = children.get(i);
                    int newParents = --child.parents;
                    if (newParents == 0) {
                        // no more dependents, we can push to awaiting
                        // even if the child is purged, we need to push it so that its children will be pushed
                        this.awaiting.add(child);
                    } else if (newParents < 0) {
                        throw new IllegalStateException();
                    }
                }
            }
        }

        private DependencyNode pollAwaiting() {
            final DependencyNode ret = this.awaiting.poll();
            if (ret == null) {
                return ret;
            }

            if (ret.parents != 0) {
                throw new IllegalStateException();
            }

            if (ret.purged) {
                // need to manually remove from state here
                this.pushChildren(ret);
                this.removeNodeFromMap(ret);
            } // else: delay children push until the task has finished

            return ret;
        }

        private DependencyNode pollInfinite() {
            return this.infiniteRadius.poll();
        }

        public PrioritisedExecutor.PrioritisedTask tryPushTask() {
            if (this.currentlyExecuting >= this.maxToSchedule || this.isInfiniteRadiusScheduled) {
                return null;
            }

            DependencyNode firstInfinite;
            while ((firstInfinite = this.infiniteRadius.peek()) != null && firstInfinite.purged) {
                this.pollInfinite();
            }

            DependencyNode firstAwaiting;
            while ((firstAwaiting = this.awaiting.peek()) != null && firstAwaiting.purged) {
                this.pollAwaiting();
            }

            if (firstInfinite == null && firstAwaiting == null) {
                return null;
            }

            // firstAwaiting compared to firstInfinite
            final int compare;

            if (firstAwaiting == null) {
                // we choose first infinite, or infinite < awaiting
                compare = 1;
            } else if (firstInfinite == null) {
                // we choose first awaiting, or awaiting < infinite
                compare = -1;
            } else {
                compare = DEPENDENCY_NODE_COMPARATOR.compare(firstAwaiting, firstInfinite);
            }

            if (compare >= 0) {
                if (this.currentlyExecuting != 0) {
                    // don't queue infinite task while other tasks are executing in parallel
                    return null;
                }
                ++this.currentlyExecuting;
                this.pollInfinite();
                this.isInfiniteRadiusScheduled = true;
                return firstInfinite.task.pushTask(this.executor);
            } else {
                ++this.currentlyExecuting;
                this.pollAwaiting();
                return firstAwaiting.task.pushTask(this.executor);
            }
        }
    }

    private static final class DependencyNode {

        private final Task task;
        private final DependencyTree tree;

        // dependency tree fields
        // (must hold lock on the scheduler to use)
        // null is the same as empty, we just use it so that we don't allocate the set unless we need to
        private List<DependencyNode> children;
        // 0 indicates that this task is considered "awaiting"
        private int parents;
        // false -> scheduled and not cancelled
        // true -> scheduled but cancelled
        private boolean purged;
        private final long id;

        public DependencyNode(final Task task, final DependencyTree tree) {
            this.task = task;
            this.id = tree.nextId();
            this.tree = tree;
        }
    }

    private static final class Task implements PrioritisedExecutor.PrioritisedTask, Runnable {

        // task specific fields
        private final RadiusAwarePrioritisedExecutor scheduler;
        private final int chunkX;
        private final int chunkZ;
        private final int radius;
        private Runnable run;
        private PrioritisedExecutor.Priority priority;

        private DependencyNode dependencyNode;
        private PrioritisedExecutor.PrioritisedTask queuedTask;

        private Task(final RadiusAwarePrioritisedExecutor scheduler, final int chunkX, final int chunkZ, final int radius,
                     final Runnable run, final PrioritisedExecutor.Priority priority) {
            this.scheduler = scheduler;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.radius = radius;
            this.run = run;
            this.priority = priority;
        }

        private boolean isFiniteRadius() {
            return this.radius >= 0;
        }

        private PrioritisedExecutor.PrioritisedTask pushTask(final PrioritisedExecutor executor) {
            return this.queuedTask = executor.createTask(this, this.priority);
        }

        private void executeTask() {
            final Runnable run = this.run;
            this.run = null;
            run.run();
        }

        private static void scheduleTasks(final List<PrioritisedExecutor.PrioritisedTask> toSchedule) {
            if (toSchedule != null) {
                for (int i = 0, len = toSchedule.size(); i < len; ++i) {
                    toSchedule.get(i).queue();
                }
            }
        }

        private void returnNode() {
            final List<PrioritisedExecutor.PrioritisedTask> toSchedule;
            synchronized (this.scheduler) {
                final DependencyNode node = this.dependencyNode;
                this.dependencyNode = null;
                toSchedule = node.tree.returnNode(node);
            }

            scheduleTasks(toSchedule);
        }

        @Override
        public void run() {
            final Runnable run = this.run;
            this.run = null;
            try {
                run.run();
            } finally {
                this.returnNode();
            }
        }

        @Override
        public boolean queue() {
            final List<PrioritisedExecutor.PrioritisedTask> toSchedule;
            synchronized (this.scheduler) {
                if (this.queuedTask != null || this.dependencyNode != null || this.priority == PrioritisedExecutor.Priority.COMPLETING) {
                    return false;
                }

                toSchedule = this.scheduler.queue(this, this.priority);
            }

            scheduleTasks(toSchedule);
            return true;
        }

        @Override
        public boolean cancel() {
            final PrioritisedExecutor.PrioritisedTask task;
            synchronized (this.scheduler) {
                if ((task = this.queuedTask) == null) {
                    if (this.priority == PrioritisedExecutor.Priority.COMPLETING) {
                        return false;
                    }

                    this.priority = PrioritisedExecutor.Priority.COMPLETING;
                    if (this.dependencyNode != null) {
                        this.dependencyNode.purged = true;
                        this.dependencyNode = null;
                    }

                    return true;
                }
            }

            if (task.cancel()) {
                // must manually return the node
                this.run = null;
                this.returnNode();
                return true;
            }
            return false;
        }

        @Override
        public boolean execute() {
            final PrioritisedExecutor.PrioritisedTask task;
            synchronized (this.scheduler) {
                if ((task = this.queuedTask) == null) {
                    if (this.priority == PrioritisedExecutor.Priority.COMPLETING) {
                        return false;
                    }

                    this.priority = PrioritisedExecutor.Priority.COMPLETING;
                    if (this.dependencyNode != null) {
                        this.dependencyNode.purged = true;
                        this.dependencyNode = null;
                    }
                    // fall through to execution logic
                }
            }

            if (task != null) {
                // will run the return node logic automatically
                return task.execute();
            } else {
                // don't run node removal/insertion logic, we aren't actually removed from the dependency tree
                this.executeTask();
                return true;
            }
        }

        @Override
        public PrioritisedExecutor.Priority getPriority() {
            final PrioritisedExecutor.PrioritisedTask task;
            synchronized (this.scheduler) {
                if ((task = this.queuedTask) == null) {
                    return this.priority;
                }
            }

            return task.getPriority();
        }

        @Override
        public boolean setPriority(final PrioritisedExecutor.Priority priority) {
            if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
                throw new IllegalArgumentException("Invalid priority " + priority);
            }

            final PrioritisedExecutor.PrioritisedTask task;
            List<PrioritisedExecutor.PrioritisedTask> toSchedule = null;
            synchronized (this.scheduler) {
                if ((task = this.queuedTask) == null) {
                    if (this.priority == PrioritisedExecutor.Priority.COMPLETING) {
                        return false;
                    }

                    if (this.priority == priority) {
                        return true;
                    }

                    this.priority = priority;
                    if (this.dependencyNode != null) {
                        // need to re-insert node
                        this.dependencyNode.purged = true;
                        this.dependencyNode = null;
                        toSchedule = this.scheduler.queue(this, priority);
                    }
                }
            }

            if (task != null) {
                return task.setPriority(priority);
            }

            scheduleTasks(toSchedule);

            return true;
        }

        @Override
        public boolean raisePriority(final PrioritisedExecutor.Priority priority) {
            if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
                throw new IllegalArgumentException("Invalid priority " + priority);
            }

            final PrioritisedExecutor.PrioritisedTask task;
            List<PrioritisedExecutor.PrioritisedTask> toSchedule = null;
            synchronized (this.scheduler) {
                if ((task = this.queuedTask) == null) {
                    if (this.priority == PrioritisedExecutor.Priority.COMPLETING) {
                        return false;
                    }

                    if (this.priority.isHigherOrEqualPriority(priority)) {
                        return true;
                    }

                    this.priority = priority;
                    if (this.dependencyNode != null) {
                        // need to re-insert node
                        this.dependencyNode.purged = true;
                        this.dependencyNode = null;
                        toSchedule = this.scheduler.queue(this, priority);
                    }
                }
            }

            if (task != null) {
                return task.raisePriority(priority);
            }

            scheduleTasks(toSchedule);

            return true;
        }

        @Override
        public boolean lowerPriority(final PrioritisedExecutor.Priority priority) {
            if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
                throw new IllegalArgumentException("Invalid priority " + priority);
            }

            final PrioritisedExecutor.PrioritisedTask task;
            List<PrioritisedExecutor.PrioritisedTask> toSchedule = null;
            synchronized (this.scheduler) {
                if ((task = this.queuedTask) == null) {
                    if (this.priority == PrioritisedExecutor.Priority.COMPLETING) {
                        return false;
                    }

                    if (this.priority.isLowerOrEqualPriority(priority)) {
                        return true;
                    }

                    this.priority = priority;
                    if (this.dependencyNode != null) {
                        // need to re-insert node
                        this.dependencyNode.purged = true;
                        this.dependencyNode = null;
                        toSchedule = this.scheduler.queue(this, priority);
                    }
                }
            }

            if (task != null) {
                return task.lowerPriority(priority);
            }

            scheduleTasks(toSchedule);

            return true;
        }
    }
}