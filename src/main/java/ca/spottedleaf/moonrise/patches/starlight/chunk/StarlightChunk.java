package ca.spottedleaf.moonrise.patches.starlight.chunk;

import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;

public interface StarlightChunk {

    public SWMRNibbleArray[] starlight$getBlockNibbles();
    public void starlight$setBlockNibbles(final SWMRNibbleArray[] nibbles);

    public SWMRNibbleArray[] starlight$getSkyNibbles();
    public void starlight$setSkyNibbles(final SWMRNibbleArray[] nibbles);

    public boolean[] starlight$getSkyEmptinessMap();
    public void starlight$setSkyEmptinessMap(final boolean[] emptinessMap);

    public boolean[] starlight$getBlockEmptinessMap();
    public void starlight$setBlockEmptinessMap(final boolean[] emptinessMap);
}
