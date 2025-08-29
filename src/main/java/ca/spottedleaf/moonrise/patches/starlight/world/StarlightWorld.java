package ca.spottedleaf.moonrise.patches.starlight.world;

import ca.spottedleaf.moonrise.patches.starlight.StarlightEngine;

public interface StarlightWorld {

	public StarlightEngine starlight$getSkyLight();

	public StarlightEngine starlight$getBlockLight();

	public StarlightChunk starlight$getChunkIfLoaded(final int chunkX, final int chunkZ);

	public boolean starlight$hasSkylight();

	public boolean starlight$hasGeneratedBlockLight();

}
