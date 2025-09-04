package ca.spottedleaf.moonrise.client.mixin.skylands;

import net.minecraft.world.chunk.storage.ChunkStorage;
import net.minecraft.world.chunk.storage.RegionChunkStorage;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.dimension.TheEndDimension;
import net.minecraft.world.storage.AlphaWorldStorage;
import net.minecraft.world.storage.RegionWorldStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(RegionWorldStorage.class)
abstract class RegionWorldStorageMixin extends AlphaWorldStorage {

	public RegionWorldStorageMixin(File savesDir, String name, boolean createPlayerDataDir) {
		super(savesDir, name, createPlayerDataDir);
	}

	/**
	 * @reason Create ChunkStorage for the sky dimension (DIM1)
	 * @author Spottedleaf
	 */
	@Inject(
		method = "getChunkStorage",
		cancellable = true,
		at = @At(
			value = "HEAD"
		)
	)
	private void createSkyStorage(final Dimension dimension, final CallbackInfoReturnable<ChunkStorage> cir) {
		if (dimension instanceof TheEndDimension) { // bad mapping
			final File root = this.getDir();
			final File dir = new File(root, "DIM1");
			dir.mkdirs();
			cir.setReturnValue(new RegionChunkStorage(dir));
		}
	}
}
