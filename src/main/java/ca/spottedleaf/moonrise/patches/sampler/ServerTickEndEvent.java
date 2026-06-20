package ca.spottedleaf.moonrise.patches.sampler;

import ca.spottedleaf.sampler.EventRegistry;

public record ServerTickEndEvent() {
    public static final EventRegistry.RegisteredEvent<ServerTickEndEvent> EVENT = EventRegistry.register("moonrise:server_tick_end", ServerTickEndEvent.class);
}
