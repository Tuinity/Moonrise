package ca.spottedleaf.moonrise.fabric.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
        final int minSection = WorldUtil.getMinSection(world);

        final int minBlockX = Mth.floor(boundingBox.minX);
        final int minBlockY = Math.max((minSection << 4), Mth.floor(boundingBox.minY));
        final int minBlockZ = Mth.floor(boundingBox.minZ);

        // note: bounds are exclusive in Vanilla, so we subtract 1 - our loop expects bounds to be inclusive
        final int maxBlockX = Mth.ceil(boundingBox.maxX) - 1;
        final int maxBlockY = Math.min((WorldUtil.getMaxSection(world) << 4) | 15, Mth.ceil(boundingBox.maxY) - 1);
        final int maxBlockZ = Mth.ceil(boundingBox.maxZ) - 1;

        final boolean isPushable = this.isPushedByFluid();
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        Vec3 pushVector = Vec3.ZERO;
        double totalPushes = 0.0;
        double maxHeightDiff = 0.0;
        boolean inFluid = false;

        final int minChunkX = minBlockX >> 4;
        final int maxChunkX = maxBlockX >> 4;

        final int minChunkZ = minBlockZ >> 4;
        final int maxChunkZ = maxBlockZ >> 4;

        final ChunkSource chunkSource = world.getChunkSource();

        final int chunkLenX = maxChunkX - minChunkX + 1;
        // chunk index = (x - minX) + (maxX-minX+1)*(z - minZ)
        //             = x + (maxX-minX+1)*z - (minX + (maxX-minX+1)*minZ)
        final int chunkOffset = -(minChunkX + chunkLenX*minChunkZ);
        //             = x + (maxX-minX+1)*z + chunkOffset
        final LevelChunkSection[][] sections = new LevelChunkSection[chunkLenX * (maxChunkZ - minChunkZ + 1)][];

        // init chunks
        for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
            for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                sections[currChunkX + chunkLenX*currChunkZ + chunkOffset] = chunkSource.getChunk(currChunkX, currChunkZ, ChunkStatus.FULL, false).getSections();
            }
        }

        for (int currX = minBlockX; currX <= maxBlockX; ++currX) {
            for (int currY = minBlockY; currY <= maxBlockY; ++currY) {
                for (int currZ = minBlockZ; currZ <= maxBlockZ; ++currZ) {
                    final FluidState fluidState = sections[(currX >> 4) + chunkLenX*(currZ >> 4) + chunkOffset][(currY >> 4) - minSection]
                                                    .states.get((currX & 15) | ((currZ & 15) << 4) | ((currY & 15) << 8)).getFluidState();

                    if (fluidState.isEmpty() || !fluidState.is(fluid)) {
                        continue;
                    }

                    mutablePos.set(currX, currY, currZ);

                    final double height = (double)((float)currY + fluidState.getHeight(world, mutablePos));
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

                    if (maxHeightDiff < 0.4) {
                        pushVector = pushVector.add(flow.scale(maxHeightDiff));
                    } else {
                        pushVector = pushVector.add(flow);
                    }
                }
            }
        }

        this.fluidHeight.put(fluid, maxHeightDiff);

        if (pushVector == Vec3.ZERO) {
            return inFluid;
        }

        // note: totalPushes != 0 as pushVector was changed
        pushVector = pushVector.scale(1.0 / totalPushes);
        final Vec3 currMovement = this.getDeltaMovement();

        if (!((Entity)(Object)this instanceof Player)) {
            pushVector = pushVector.normalize();
        }

        pushVector = pushVector.scale(flowScale);
        if (Math.abs(currMovement.x) < 0.003 && Math.abs(currMovement.z) < 0.003 && pushVector.length() < 0.0045000000000000005) {
            pushVector = pushVector.normalize().scale(0.0045000000000000005);
        }

        this.setDeltaMovement(currMovement.add(pushVector));

        // note: inFluid = true here as pushVector != 0
        return true;
    }
}
