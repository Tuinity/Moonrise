package ca.spottedleaf.moonrise.patches.tick_loop;

public interface TickLoopBlockableEventLoop<R extends Runnable> {

    // executes all tasks available at the beginning of this function
    public void moonrise$executeAllRecentInternalTasks();

}
