package ca.spottedleaf.moonrise.server.mixin.starlight;

import ca.spottedleaf.moonrise.patches.starlight.world.StarlightChunk;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.storage.AlphaChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AlphaChunkStorage.class)
abstract class AlphaChunkStorageMixin {

	@Unique
	private static final String LIGHT_FLAG_TAG = "Starlight.LightGenerated";

	/**
	 * @reason Support reading/writing light flag
	 * @author Spottedleaf
	 */
	@Inject(
		method = "saveChunkToNbt",
		at = @At(
			value = "RETURN"
		)
	)
	private static void writeLightFlag(final WorldChunk chunk, final World world, final NbtCompound nbt, final CallbackInfo ci) {
		nbt.putBoolean(LIGHT_FLAG_TAG, ((StarlightChunk)chunk).starlight$isLightingDone());
	}

	/**
	 * @reason Support reading/writing light flag
	 * @author Spottedleaf
	 */
	@Inject(
		method = "loadChunkFromNbt",
		at = @At(
			value = "RETURN"
		)
	)
	private static void readLightFlag(final World world, final NbtCompound nbt, final CallbackInfoReturnable<WorldChunk> cir) {
		((StarlightChunk)cir.getReturnValue()).starlight$setIsLightingDone(nbt.getBoolean(LIGHT_FLAG_TAG));
	}
}
