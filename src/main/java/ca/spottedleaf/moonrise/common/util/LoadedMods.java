package ca.spottedleaf.moonrise.common.util;

import ca.spottedleaf.moonrise.common.PlatformHooks;

/**
 * Holds whether optional mods we integrate with are loaded. Reading a flag is the only thing that initialises this
 * class, so code that links against an optional mod must be guarded by the matching flag.
 */
public final class LoadedMods {

    public static final boolean LITHIUM = PlatformHooks.get().isModLoaded("lithium");

    private LoadedMods() {}
}
