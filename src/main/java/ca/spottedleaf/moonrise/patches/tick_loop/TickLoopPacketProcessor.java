package ca.spottedleaf.moonrise.patches.tick_loop;

public interface TickLoopPacketProcessor {

    // returns false if shutdown or if there were no tasks to execute
    public boolean moonrise$executeSinglePacket();

}
