package ca.spottedleaf.moonrise.client;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.MixinEnvironment;

class MixinAuditTest {

	@Test
	void auditMixins() {
		MixinEnvironment.getCurrentEnvironment().audit();
	}
}
