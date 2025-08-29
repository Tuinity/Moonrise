package ca.spottedleaf.moonrise.common;

import java.util.ServiceLoader;

public interface PlatformHooks {

	public static PlatformHooks get() {
		return Holder.INSTANCE;
	}

	public boolean isClient();

	public int getLightBlock(final int blockId);

	public int getLightEmitted(final int blockId);

	public static final class Holder {
		private Holder() {
		}

		private static final PlatformHooks INSTANCE;

		static {
			INSTANCE = ServiceLoader.load(PlatformHooks.class, PlatformHooks.class.getClassLoader()).findFirst()
				.orElseThrow(() -> new RuntimeException("Failed to locate PlatformHooks"));
		}
	}
}
