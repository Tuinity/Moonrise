package ca.spottedleaf.moonrise.patches.starlight.world;

import ca.spottedleaf.moonrise.patches.starlight.data.StarlightNibbleArray;

public interface StarlightChunk {

	public StarlightNibbleArray starlight$getSkyLight();

	public StarlightNibbleArray starlight$getBlockLight();

	public int starlight$getBlock(final int localX, final int localY, final int localZ);

	public void starlight$markDirty();

	public void starlight$initLightingForRealNotJustHeightmap();

	public void starlight$updateLight(final int localX, final int localY, final int localZ);

	public boolean starlight$isLightingDone();

	public void starlight$setIsLightingDone(final boolean value);

}
