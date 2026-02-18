package ca.spottedleaf.moonrise.patches.tick_loop;

import java.util.function.Predicate;

public interface TickLoopBlockableEventLoop<R extends Runnable> {

    // executes all tasks available at the beginning of this function
    public boolean moonrise$executeAllRecentInternalTasks();

    // return true if a task was ran
    public boolean moonrise$runTaskIf(final Predicate<? super R> predicate);

}
