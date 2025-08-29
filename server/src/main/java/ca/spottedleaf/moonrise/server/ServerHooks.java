package ca.spottedleaf.moonrise.server;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import net.minecraft.block.Block;

public final class ServerHooks implements PlatformHooks {

	@Override
	public boolean isClient() {
		return false;
	}

	@Override
	public int getLightBlock(final int blockId) {
		return Block.OPACITIES[blockId];
	}

	@Override
	public int getLightEmitted(final int blockId) {
		return Block.LIGHT_LEVELS[blockId];
	}
}
