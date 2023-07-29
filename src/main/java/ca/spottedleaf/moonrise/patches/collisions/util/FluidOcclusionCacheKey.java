package ca.spottedleaf.moonrise.patches.collisions.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public record FluidOcclusionCacheKey(BlockState first, BlockState second, Direction direction, boolean result) {
}
