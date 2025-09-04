package ca.spottedleaf.moonrise.server.mixin.skylands;

import net.minecraft.world.chunk.storage.AlphaChunkStorage;
import net.minecraft.world.chunk.storage.ChunkStorage;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.dimension.TheEndDimension;
import net.minecraft.world.storage.AlphaWorldStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.io.File;

@Mixin(AlphaWorldStorage.class)
abstract class AlphaWorldStorageMixin {

	@Shadow
	@Final
	private File dir;

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
			final File dir = new File(this.dir, "DIM1");
			dir.mkdirs();
			cir.setReturnValue(new AlphaChunkStorage(dir, true));
		}
	}
}
