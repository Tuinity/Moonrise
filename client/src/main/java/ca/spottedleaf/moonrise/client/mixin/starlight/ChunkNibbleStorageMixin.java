package ca.spottedleaf.moonrise.client.mixin.starlight;

import ca.spottedleaf.moonrise.patches.starlight.data.StarlightNibbleArray;
import net.minecraft.world.chunk.ChunkNibbleStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkNibbleStorage.class)
abstract class ChunkNibbleStorageMixin implements StarlightNibbleArray {

	@Shadow
	@Final
	public byte[] data;

	@Unique
	private boolean isDirty;

	@Override
	public final int starlight$getData(final int localX, final int localY, final int localZ) {
		final int index = localX << 11 | localZ << 7 | localY;
		final byte value = this.data[index >>> 1];
		// if we are an even index, we want lower 4 bits
		// if we are an odd index, we want upper 4 bits
		return ((value >>> ((index & 1) << 2)) & 0xF);
	}

	@Override
	public final void starlight$setData(final int localX, final int localY, final int localZ, final int to) {
		final int index = localX << 11 | localZ << 7 | localY;
		final int shift = (index & 1) << 2;
		final int i = index >>> 1;

		this.data[i] = (byte)((this.data[i] & (0xF0 >>> shift)) | (to << shift));
		this.isDirty = true;
	}

	@Override
	public final boolean starlight$isDirty() {
		return this.isDirty;
	}

	@Override
	public final void starlight$setDirty(final boolean value) {
		this.isDirty = value;
	}
}
