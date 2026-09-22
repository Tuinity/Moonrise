package ca.spottedleaf.moonrise.patches.sampler;

import ca.spottedleaf.sampler.EventRegistry;

public record ServerTickStartEvent() {
    public static final EventRegistry.RegisteredEvent<ServerTickStartEvent> EVENT = EventRegistry.register("moonrise:server_tick_start", ServerTickStartEvent.class);
}
