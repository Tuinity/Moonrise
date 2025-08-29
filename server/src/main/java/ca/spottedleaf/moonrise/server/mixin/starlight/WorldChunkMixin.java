package ca.spottedleaf.moonrise.server.mixin.starlight;

import ca.spottedleaf.moonrise.patches.starlight.data.StarlightNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.world.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.world.StarlightWorld;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkNibbleStorage;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
abstract class WorldChunkMixin implements StarlightChunk {

	@Shadow
	public ChunkNibbleStorage skyLight;

	@Shadow
	public ChunkNibbleStorage blockLight;

	@Shadow
	public byte[] blockIds;

	@Shadow
	public boolean dirty;

	@Shadow
	public World world;

	@Shadow
	@Final
	public int chunkX;

	@Shadow
	@Final
	public int chunkZ;

	@Shadow
	public byte[] heightMap;

	@Shadow
	public int lowestHeight;


	@Unique
	private boolean isLightDone;

	@Override
	public final StarlightNibbleArray starlight$getSkyLight() {
		return (StarlightNibbleArray)this.skyLight;
	}

	@Override
	public final StarlightNibbleArray starlight$getBlockLight() {
		return (StarlightNibbleArray)this.blockLight;
	}

	@Override
	public final int starlight$getBlock(final int localX, final int localY, final int localZ) {
		return (int)this.blockIds[localX << 11 | localZ << 7 | localY] & 255;
	}

	@Override
	public final void starlight$markDirty() {
		this.dirty = true;
	}

	@Override
	public final void starlight$initLightingForRealNotJustHeightmap() {
		if (((StarlightWorld)this.world).starlight$hasSkylight()) {
			((StarlightWorld)this.world).starlight$getSkyLight().initSkylight(this.chunkX, this.chunkZ);
		}

		((StarlightWorld)this.world).starlight$getBlockLight().initBlockLight(this.chunkX, this.chunkZ);
	}

	@Override
	public final void starlight$updateLight(final int localX, final int localY, final int localZ) {
        final int worldX = localX | (this.chunkX << 4);
        final int worldZ = localZ | (this.chunkZ << 4);

		if (((StarlightWorld)this.world).starlight$hasSkylight()) {
			((StarlightWorld)this.world).starlight$getSkyLight().checkSkyEmittance(worldX, localY, worldZ);
		}
		((StarlightWorld)this.world).starlight$getBlockLight().checkBlockEmittance(worldX, localY, worldZ);
	}

	@Override
	public final boolean starlight$isLightingDone() {
		return this.isLightDone;
	}

	@Override
	public final void starlight$setIsLightingDone(final boolean value) {
		this.isLightDone = value;
	}

	/**
	 * @reason Delay light initialisation - we want to be sure that we are added to the chunk map first
	 *         Effectively just makes this a heightmap
	 * @author Spottedleaf
	 */
	@Overwrite
	public void populateSkylight() {
		int lowestHeight = 127;

		for (int x = 0; x < 16; ++x) {
			for (int z = 0; z < 16; ++z) {
				int height = 127;

				int idx;
				for (idx = x << 11 | z << 7; height > 0 && Block.OPACITIES[(int)this.blockIds[idx + height - 1] & 255] == 0; --height);

				this.heightMap[z << 4 | x] = (byte)height;
				lowestHeight = Math.min(height, lowestHeight);

				// Starlight - initialised later
			}
		}

		this.lowestHeight = lowestHeight;

		// Starlight - initialised later

		this.dirty = true;
	}

	/**
	 * @reason Move light update code to starlight
	 *         Effectively just makes this a heightmap update
	 * @author Spottedleaf
	 */
	@Overwrite
	private void resetLightAt(int localX, int y, int localZ) {
		int currentHeight = this.heightMap[localZ << 4 | localX] & 255;
		int newHeight = Math.max(y, currentHeight);

		for (int idx = localX << 11 | localZ << 7; newHeight > 0 && Block.OPACITIES[this.blockIds[idx + newHeight - 1] & 255] == 0; --newHeight);

		if (newHeight != currentHeight) {
			this.world.checkLight(localX, localZ, newHeight, currentHeight); // bad mapping: should be notifyChanged
			this.heightMap[localZ << 4 | localX] = (byte)newHeight;
			if (newHeight < this.lowestHeight) {
				this.lowestHeight = newHeight;
			} else {
				int lowestHeight = Integer.MAX_VALUE;

				for (int idx = 0; idx < 16*16; ++idx) {
					lowestHeight = Math.min((int)this.heightMap[idx] & 255, lowestHeight);
				}

				this.lowestHeight = lowestHeight;
			}

			// Starlight - move light check code elsewhere

			this.dirty = true;
		}
	}

	/**
	 * @reason Move light update to Starlight
	 * @author Spottedleaf
	 */
	@Redirect(
		method = "setBlockWithMetadataAt",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/World;checkLight(Lnet/minecraft/world/LightType;IIIIII)V"
		)
	)
	private void voidMetadataUpdate(World instance, LightType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

	/**
	 * @reason Move light update to Starlight
	 * @author Spottedleaf
	 */
	@Redirect(
		method = "setBlockWithMetadataAt",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/chunk/WorldChunk;queueLightUpdate(II)V"
		)
	)
	private void invokeStarlightForMetadataUpdate(final WorldChunk instance,
												  final int localX, final int localZ,
												  @Local(argsOnly = true, ordinal = 1) final int localY) {
		this.starlight$updateLight(localX, localY, localZ);
	}

	/**
	 * @reason Move light update to Starlight
	 * @author Spottedleaf
	 */
	@Redirect(
		method = "setBlockAt",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/World;checkLight(Lnet/minecraft/world/LightType;IIIIII)V"
		)
	)
	private void voidRegularUpdate(World instance, LightType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

	/**
	 * @reason Move light update to Starlight
	 * @author Spottedleaf
	 */
	@Redirect(
		method = "setBlockAt",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/chunk/WorldChunk;queueLightUpdate(II)V"
		)
	)
	private void invokeStarlightForRegularUpdate(final WorldChunk instance,
												 final int localX, final int localZ,
												 @Local(argsOnly = true, ordinal = 1) final int localY) {
		this.starlight$updateLight(localX, localY, localZ);
	}

	/**
	 * @reason Move to post load NOP to guarantee that we are in the world
	 * @author Spottedleaf
	 */
	@Inject(
		method = {
			"m_4003692", // server
			"m_7782267" // client
		},
		at = @At(
			value = "HEAD"
		)
	)
	private void initLight(final CallbackInfo ci) {
		if (!this.isLightDone && !((Object)this instanceof EmptyChunk)) {
			this.starlight$initLightingForRealNotJustHeightmap();
			this.isLightDone = true;
		}
	}
}
