package ca.spottedleaf.moonrise.neoforge.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.neoforge.patches.collisions.FluidPushCalculation;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.util.Iterator;

@Mixin(Entity.class)
abstract class EntityMixin implements IEntityExtension {

    @Shadow
    public abstract boolean touchingUnloadedChunk();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    private Level level;

    @Shadow
    protected abstract void setFluidTypeHeight(final FluidType type, final double height);

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract void setDeltaMovement(final Vec3 arg);

    /**
     * @reason Optimise the block reading in this function
     * @author Spottedleaf
     */
    @Overwrite
    public void updateFluidHeightAndDoFluidPushing(final boolean doFluidPushing) {
        if (this.touchingUnloadedChunk()) {
            return;
        }

        final AABB boundingBox = this.getBoundingBox().deflate(1.0E-3);

        final Level world = this.level;
        final int minSection = WorldUtil.getMinSection(world);

        final int minBlockX = Mth.floor(boundingBox.minX);
        final int minBlockY = Math.max((minSection << 4), Mth.floor(boundingBox.minY));
        final int minBlockZ = Mth.floor(boundingBox.minZ);

        // note: bounds are exclusive in Vanilla, so we subtract 1
        final int maxBlockX = Mth.ceil(boundingBox.maxX) - 1;
        final int maxBlockY = Math.min((WorldUtil.getMaxSection(world) << 4) | 15, Mth.ceil(boundingBox.maxY) - 1);
        final int maxBlockZ = Mth.ceil(boundingBox.maxZ) - 1;

        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

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

        final Reference2ReferenceArrayMap<FluidType, FluidPushCalculation> calculations = new Reference2ReferenceArrayMap<>();

        for (int currX = minBlockX; currX <= maxBlockX; ++currX) {
            for (int currY = minBlockY; currY <= maxBlockY; ++currY) {
                for (int currZ = minBlockZ; currZ <= maxBlockZ; ++currZ) {
                    final FluidState fluidState = sections[(currX >> 4) + chunkLenX*(currZ >> 4) + chunkOffset][(currY >> 4) - minSection]
                        .states.get((currX & 15) | ((currZ & 15) << 4) | ((currY & 15) << 8)).getFluidState();

                    if (fluidState.isEmpty()) {
                        continue;
                    }

                    mutablePos.set(currX, currY, currZ);

                    // note: assume fluidState.isEmpty() == type.isAir()

                    final double height = (double)((float)mutablePos.getY() + fluidState.getHeight(world, mutablePos));
                    final double diff = height - boundingBox.minY;

                    if (diff < 0.0) {
                        continue;
                    }

                    final FluidType type = fluidState.getFluidType();

                    final FluidPushCalculation calculation = calculations.computeIfAbsent(type, (final FluidType key) -> {
                        return new FluidPushCalculation();
                    });

                    final double maxHeightDiff = calculation.maxHeightDiff = Math.max(calculation.maxHeightDiff, diff);

                    if (calculation.isPushed == Boolean.FALSE) {
                        continue;
                    } else if (calculation.isPushed == null) {
                        final boolean isPushed = this.isPushedByFluid(type);
                        calculation.isPushed = Boolean.valueOf(isPushed);
                        if (!isPushed) {
                            continue;
                        }
                    }

                    ++calculation.totalPushes;

                    final Vec3 flow = fluidState.getFlow(world, mutablePos);

                    if (maxHeightDiff < 0.4) {
                        calculation.pushVector = calculation.pushVector.add(flow.scale(maxHeightDiff));
                    } else {
                        calculation.pushVector = calculation.pushVector.add(flow);
                    }
                }
            }
        }

        if (calculations.isEmpty()) {
            return;
        }

        for (final Iterator<Reference2ReferenceMap.Entry<FluidType, FluidPushCalculation>> iterator = calculations.reference2ReferenceEntrySet().fastIterator(); iterator.hasNext();) {
            final Reference2ReferenceMap.Entry<FluidType, FluidPushCalculation> entry = iterator.next();
            final FluidType type = entry.getKey();
            final FluidPushCalculation calculation = entry.getValue();

            this.setFluidTypeHeight(type, calculation.maxHeightDiff);

            if (!doFluidPushing) {
                continue;
            }

            Vec3 pushVector = calculation.pushVector;

            if (pushVector == Vec3.ZERO) {
                continue;
            }

            // note: totalPushes != 0 as pushVector was changed
            pushVector = pushVector.scale(1.0 / calculation.totalPushes);
            final Vec3 currMovement = this.getDeltaMovement();

            if (!((Entity)(Object)this instanceof Player)) {
                pushVector = pushVector.normalize();
            }

            pushVector = pushVector.scale(this.getFluidMotionScale(type));
            if (Math.abs(currMovement.x) < 0.003 && Math.abs(currMovement.z) < 0.003 && pushVector.length() < 0.0045000000000000005) {
                pushVector = pushVector.normalize().scale(0.0045000000000000005);
            }

            this.setDeltaMovement(currMovement.add(pushVector));
        }
    }
}
