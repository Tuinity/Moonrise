package ca.spottedleaf.moonrise.patches.sampler;

import ca.spottedleaf.sampler.EventRegistry;
import ca.spottedleaf.sampler.EventRegistry.RegisteredEvent;

public record ServerSkipTicksEvent(long count) {
    public static final RegisteredEvent<ServerSkipTicksEvent> EVENT = EventRegistry.register("moonrise:server_skip_ticks", ServerSkipTicksEvent.class);
}
