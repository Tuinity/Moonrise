package ca.spottedleaf.moonrise.client;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import net.minecraft.block.Block;

public final class ClientHooks implements PlatformHooks {

	@Override
	public boolean isClient() {
		return true;
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
