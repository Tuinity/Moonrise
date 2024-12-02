package ca.spottedleaf.moonrise.neoforge;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.MixinEnvironment;

class MixinAuditTest {
    @Test
    void auditMixins() {
        final ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(MixinAuditTest.class.getClassLoader());
            MixinEnvironment.getCurrentEnvironment().audit();
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
