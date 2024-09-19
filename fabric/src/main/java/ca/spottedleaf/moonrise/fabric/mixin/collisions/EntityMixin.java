package ca.spottedleaf.moonrise.fabric.mixin.collisions;

import ca.spottedleaf.moonrise.patches.getblock.GetBlockLevel;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Entity.class)
abstract class EntityMixin {

    @Shadow
    public abstract boolean touchingUnloadedChunk();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    @Deprecated
    public abstract boolean isPushedByFluid();

    @Shadow
    private Level level;

    @Shadow
    @Deprecated
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract void setDeltaMovement(final Vec3 arg);

    /**
     * @reason Optimise the block reading in this function
     * @author Spottedleaf
     */
    @Overwrite
    public boolean updateFluidHeightAndDoFluidPushing(final TagKey<Fluid> fluid, final double flowScale) {
        if (this.touchingUnloadedChunk()) {
            return false;
        }

        final AABB boundingBox = this.getBoundingBox().deflate(1.0E-3);

        final Level world = this.level;
        final int minSection = ((GetBlockLevel)world).moonrise$getMinSection();

        final int minBlockX = Mth.floor(boundingBox.minX);
        final int minBlockY = Math.max((minSection << 4), Mth.floor(boundingBox.minY));
        final int minBlockZ = Mth.floor(boundingBox.minZ);

        final int maxBlockX = Mth.ceil(boundingBox.maxX);
        final int maxBlockY = Math.min((((GetBlockLevel)world).moonrise$getMaxSection() << 4) | 15, Mth.ceil(boundingBox.maxY));
        final int maxBlockZ = Mth.ceil(boundingBox.maxZ);

        final boolean isPushable = this.isPushedByFluid();
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        Vec3 pushVector = Vec3.ZERO;
        double totalPushes = 0.0;
        double maxHeightDiff = 0.0;
        boolean inFluid = false;

        final int minChunkX = minBlockX >> 4;
        final int maxChunkX = maxBlockX >> 4;

        final int minChunkY = minBlockY >> 4;
        final int maxChunkY = maxBlockY >> 4;

        final int minChunkZ = minBlockZ >> 4;
        final int maxChunkZ = maxBlockZ >> 4;

        final ChunkSource chunkSource = world.getChunkSource();

        for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
            for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                final ChunkAccess chunk = chunkSource.getChunk(currChunkX, currChunkZ, ChunkStatus.FULL, false);

                final LevelChunkSection[] sections = chunk.getSections();

                // bound y
                for (int currChunkY = minChunkY; currChunkY <= maxChunkY; ++currChunkY) {
                    final int sectionIdx = currChunkY - minSection;
                    if (sectionIdx < 0 || sectionIdx >= sections.length) {
                        continue;
                    }
                    final LevelChunkSection section = sections[sectionIdx];
                    if (section.hasOnlyAir()) {
                        // empty
                        continue;
                    }

                    final PalettedContainer<BlockState> blocks = section.states;

                    final int minXIterate = currChunkX == minChunkX ? (minBlockX & 15) : 0;
                    final int maxXIterate = currChunkX == maxChunkX ? (maxBlockX & 15) : 15;
                    final int minZIterate = currChunkZ == minChunkZ ? (minBlockZ & 15) : 0;
                    final int maxZIterate = currChunkZ == maxChunkZ ? (maxBlockZ & 15) : 15;
                    final int minYIterate = currChunkY == minChunkY ? (minBlockY & 15) : 0;
                    final int maxYIterate = currChunkY == maxChunkY ? (maxBlockY & 15) : 15;

                    for (int currY = minYIterate; currY <= maxYIterate; ++currY) {
                        final int blockY = currY | (currChunkY << 4);
                        mutablePos.setY(blockY);
                        for (int currZ = minZIterate; currZ <= maxZIterate; ++currZ) {
                            final int blockZ = currZ | (currChunkZ << 4);
                            mutablePos.setZ(blockZ);
                            for (int currX = minXIterate; currX <= maxXIterate; ++currX) {
                                final int localBlockIndex = (currX) | (currZ << 4) | ((currY) << 8);
                                final int blockX = currX | (currChunkX << 4);
                                mutablePos.setX(blockX);

                                final FluidState fluidState = blocks.get(localBlockIndex).getFluidState();

                                if (fluidState.isEmpty() || !fluidState.is(fluid)) {
                                    continue;
                                }

                                final double height = (double)((float)blockY + fluidState.getHeight(world, mutablePos));
                                final double diff = height - boundingBox.minY;

                                if (diff < 0.0) {
                                    continue;
                                }

                                inFluid = true;
                                maxHeightDiff = Math.max(maxHeightDiff, diff);

                                if (!isPushable) {
                                    continue;
                                }

                                ++totalPushes;

                                final Vec3 flow = fluidState.getFlow(world, mutablePos);

                                if (diff < 0.4) {
                                    pushVector = pushVector.add(flow.scale(diff));
                                } else {
                                    pushVector = pushVector.add(flow);
                                }
                            }
                        }
                    }
                }
            }
        }

        this.fluidHeight.put(fluid, maxHeightDiff);

        if (pushVector.lengthSqr() == 0.0) {
            return inFluid;
        }

        // note: totalPushes != 0 as pushVector != 0
        pushVector = pushVector.scale(1.0 / totalPushes);
        final Vec3 currMovement = this.getDeltaMovement();

        if (!((Entity)(Object)this instanceof Player)) {
            pushVector = pushVector.normalize();
        }

        pushVector.scale(flowScale);
        if (Math.abs(currMovement.x) < 0.003 && Math.abs(currMovement.z) < 0.003 && pushVector.length() < 0.0045000000000000005) {
            pushVector = pushVector.normalize().scale(0.0045000000000000005);
        }

        this.setDeltaMovement(currMovement.add(pushVector));

        // note: inFluid = true here as pushVector != 0
        return true;
    }
}
