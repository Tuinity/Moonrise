package ca.spottedleaf.moonrise.server.mixin.skylands.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.EntityTracker;
import net.minecraft.server.world.ReadOnlyServerWorld;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.storage.WorldStorage;
import net.minecraft.world.storage.WorldStorageSource;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

	@Shadow
	public EntityTracker[] entityTrackers;

	@Shadow
	public ServerWorld[] worlds;

	/**
	 * @reason Create space for sky dimension
	 * @author Spottedleaf
	 */
	@Inject(
		method = "<init>",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/server/MinecraftServer;entityTrackers:[Lnet/minecraft/server/entity/EntityTracker;",
			opcode = Opcodes.PUTFIELD,
			shift = At.Shift.AFTER
		)
	)
	private void addExtraEntityTrackerSlot(final CallbackInfo ci) {
		this.entityTrackers = new EntityTracker[3];
	}

	/**
	 * @reason Add entity tracker for sky dimension
	 * @author Spottedleaf
	 */
	@Inject(
		method = "init",
		at = @At(
			value = "FIELD",
			opcode = Opcodes.PUTFIELD,
			target = "Lnet/minecraft/server/MinecraftServer;playerManager:Lnet/minecraft/server/PlayerManager;",
			shift = At.Shift.AFTER
		)
	)
	private void addSkyEntityTracker(final CallbackInfoReturnable<Boolean> cir) {
		this.entityTrackers[2] = new EntityTracker((MinecraftServer)(Object)this, 1);
	}

	/**
	 * @reason Create space for sky dimension
	 * @author Spottedleaf
	 */
	@Inject(
		method = "loadWorld",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/server/MinecraftServer;worlds:[Lnet/minecraft/server/world/ServerWorld;",
			opcode = Opcodes.PUTFIELD,
			shift = At.Shift.AFTER
		)
	)
	private void addExtraWorldSlot(final WorldStorageSource storageSource, final String worldDirName, final long seed, final CallbackInfo ci) {
		this.worlds = new ServerWorld[3];
	}

	@Redirect(
		method = "loadWorld",
		at = @At(
			value = "NEW",
			target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/world/storage/WorldStorage;Ljava/lang/String;IJLnet/minecraft/server/world/ServerWorld;)Lnet/minecraft/server/world/ReadOnlyServerWorld;",
			ordinal = 0
		)
	)
	private ReadOnlyServerWorld setNewSkyWorldId(final MinecraftServer minecraftServer,
												 final WorldStorage worldStorage, final String worldDirName,
												 final int incorrectDimensionId, long seed,
												 final ServerWorld overworld) {
		int worldIndex = 0;
		for (;worldIndex < this.worlds.length && this.worlds[worldIndex] != null; ++worldIndex) {}

		final int correctDimId;
		switch (worldIndex) {
			case 1: {
				correctDimId = -1;
				break;
			}
			case 2: {
				correctDimId = 1;
				break;
			}
			default: {
				throw new IllegalStateException("Unknown world index: " + worldIndex);
			}
		}

		return new ReadOnlyServerWorld(
			minecraftServer, worldStorage, worldDirName, correctDimId, seed, overworld
		);
	}

	/**
	 * @reason Support reading sky worlds
	 * @author Spottedleaf
	 */
	@Inject(
		method = "getWorld",
		cancellable = true,
		at = @At(
			value = "HEAD"
		)
	)
	private void getSkyWorld(final int dimension, final CallbackInfoReturnable<ServerWorld> cir) {
		if (dimension == 1) {
			cir.setReturnValue(this.worlds[2]);
		}
	}

	/**
	 * @reason Support reading sky entity tracker
	 * @author Spottedleaf
	 */
	@Inject(
		method = "getEntityTracker",
		cancellable = true,
		at = @At(
			value = "HEAD"
		)
	)
	private void getSkyEntityTracker(final int dimension, final CallbackInfoReturnable<EntityTracker> cir) {
		if (dimension == 1) {
			cir.setReturnValue(this.entityTrackers[2]);
		}
	}
}
