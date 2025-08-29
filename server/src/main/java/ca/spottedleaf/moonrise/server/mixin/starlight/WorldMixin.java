package ca.spottedleaf.moonrise.server.mixin.starlight;

import ca.spottedleaf.moonrise.patches.starlight.StarlightEngine;
import ca.spottedleaf.moonrise.patches.starlight.world.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.world.StarlightWorld;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.dimension.Dimension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(World.class)
abstract class WorldMixin implements WorldView, StarlightWorld {

	@Shadow
	protected ChunkSource chunkSource;

	@Shadow
	@Final
	public Dimension dimension;


	@Unique
	private final StarlightEngine skyLight = new StarlightEngine(true, this);

	@Unique
	private final StarlightEngine blockLight = new StarlightEngine(false, this);


	@Override
	public final StarlightEngine starlight$getSkyLight() {
		return this.skyLight;
	}

	@Override
	public final StarlightEngine starlight$getBlockLight() {
		return this.blockLight;
	}

	@Override
	public final StarlightChunk starlight$getChunkIfLoaded(final int chunkX, final int chunkZ) {
		return this.chunkSource.hasChunk(chunkX, chunkZ) ? (StarlightChunk)this.chunkSource.getChunk(chunkX, chunkZ) : null;
	}

	@Override
	public final boolean starlight$hasSkylight() {
		return !this.dimension.isNether;
	}

	@Override
	public final boolean starlight$hasGeneratedBlockLight() {
		return this.dimension.isNether;
	}
}
