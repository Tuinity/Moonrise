package ca.spottedleaf.moonrise.common;

import java.util.ServiceLoader;

// TODO - placeholder for if we need platform-specific impls for logic that needs to be called from common
public interface PlatformHooks {
    static PlatformHooks get() {
        return Holder.INSTANCE;
    }

    void doSomePlatformAction();

    final class Holder {
        private Holder() {
        }

        private static final PlatformHooks INSTANCE;

        static {
            INSTANCE = ServiceLoader.load(PlatformHooks.class, PlatformHooks.class.getClassLoader()).findFirst()
                    .orElseThrow(() -> new RuntimeException("Failed to locate PlatformHooks"));
        }
    }
}
