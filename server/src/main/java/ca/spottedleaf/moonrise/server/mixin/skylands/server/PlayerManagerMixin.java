package ca.spottedleaf.moonrise.server.mixin.skylands.server;

import net.minecraft.server.ChunkMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerManager.class)
abstract class PlayerManagerMixin {

	@Shadow
	private ChunkMap[] chunkMaps;

	/**
	 * @reason Create space for sky dimension
	 * @author Spottedleaf
	 */
	@Inject(
		method = "<init>",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/server/PlayerManager;chunkMaps:[Lnet/minecraft/server/ChunkMap;",
			opcode = Opcodes.PUTFIELD,
			shift = At.Shift.AFTER
		)
	)
	private void addExtraChunkMapSlot(final CallbackInfo ci) {
		this.chunkMaps = new ChunkMap[3];
	}

	/**
	 * @reason Add chunk map for sky dimension
	 * @author Spottedleaf
	 */
	@Inject(
		method = "<init>",
		at = @At(
			value = "FIELD",
			opcode = Opcodes.PUTFIELD,
			target = "Lnet/minecraft/server/PlayerManager;maxPlayerCount:I",
			shift = At.Shift.AFTER
		)
	)
	private void addSkyChunkMap(final MinecraftServer server, final CallbackInfo ci) {
		this.chunkMaps[2] = new ChunkMap(server, 1, (this.chunkMaps[0].getChunkViewDistance() + 16) / 16);
	}


	/**
	 * @reason Support removing from sky chunk map
	 * @author Spottedleaf
	 */
	@Inject(
		method = "onChangedDimension",
		at = @At(
			value = "HEAD"
		)
	)
	private void onChangedDimensionSky(final ServerPlayerEntity player, final CallbackInfo ci) {
		this.chunkMaps[2].removePlayer(player);
	}

	/**
	 * @reason Support reading sky chunk map
	 * @author Spottedleaf
	 */
	@Inject(
		method = "getChunkMap",
		cancellable = true,
		at = @At(
			value = "HEAD"
		)
	)
	private void getSkyChunkMap(final int dimension, final CallbackInfoReturnable<ChunkMap> cir) {
		if (dimension == 1) {
			cir.setReturnValue(this.chunkMaps[2]);
		}
	}
}
