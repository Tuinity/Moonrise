package ca.spottedleaf.moonrise.patches.starlight.data;

public interface StarlightNibbleArray {

	public int starlight$getData(final int localX, final int localY, final int localZ);

	public void starlight$setData(final int localX, final int localY, final int localZ, final int to);

	public boolean starlight$isDirty();

	public void starlight$setDirty(final boolean value);

}
