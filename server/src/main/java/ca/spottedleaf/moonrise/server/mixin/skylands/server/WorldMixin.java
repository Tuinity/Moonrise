package ca.spottedleaf.moonrise.server.mixin.skylands.server;

import net.minecraft.world.World;
import net.minecraft.world.WorldData;
import net.minecraft.world.dimension.Dimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
abstract class WorldMixin {

	@Shadow
	protected WorldData data;

	/**
	 * @reason Support loading overworld as sky type
	 * @author Spottedleaf
	 */
	@Redirect(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/dimension/Dimension;fromId(I)Lnet/minecraft/world/dimension/Dimension;"
		)
	)
	private Dimension hookSkyDimension(final int id) {
		if (id == 0) {
			if (this.data != null && this.data.getDimensionId() == 1) {
				return Dimension.fromId(1);
			}
		}
		return Dimension.fromId(id);
	}
}
