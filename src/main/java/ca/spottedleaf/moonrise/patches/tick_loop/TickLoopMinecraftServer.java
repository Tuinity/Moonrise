package ca.spottedleaf.moonrise.patches.tick_loop;

import ca.spottedleaf.moonrise.common.time.TickData;

public interface TickLoopMinecraftServer {

    public TickData moonrise$getTickData5s();

    public TickData moonrise$getTickData10s();

    public TickData moonrise$getTickData1m();

    public TickData moonrise$getTickData5m();

    public TickData moonrise$getTickData15m();

}
