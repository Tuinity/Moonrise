package ca.spottedleaf.moonrise.neoforge.mixin.collisions;

import ca.spottedleaf.moonrise.neoforge.patches.collisions.FluidPushCalculation;
import ca.spottedleaf.moonrise.patches.getblock.GetBlockLevel;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import net.minecraft.core.BlockPos;
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
    public void updateFluidHeightAndDoFluidPushing() {
        if (this.touchingUnloadedChunk()) {
            return;
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

        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        final int minChunkX = minBlockX >> 4;
        final int maxChunkX = maxBlockX >> 4;

        final int minChunkY = minBlockY >> 4;
        final int maxChunkY = maxBlockY >> 4;

        final int minChunkZ = minBlockZ >> 4;
        final int maxChunkZ = maxBlockZ >> 4;

        final ChunkSource chunkSource = world.getChunkSource();

        final Reference2ReferenceArrayMap<FluidType, FluidPushCalculation> calculations = new Reference2ReferenceArrayMap<>();

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

                                if (fluidState.isEmpty()) {
                                    continue;
                                }

                                final FluidType type = fluidState.getFluidType();

                                // note: assume fluidState.isEmpty() == type.isAir()

                                final FluidPushCalculation calculation = calculations.computeIfAbsent(type, (final FluidType key) -> {
                                    return new FluidPushCalculation();
                                });

                                final double height = (double)((float)blockY + fluidState.getHeight(world, mutablePos));
                                final double diff = height - boundingBox.minY;

                                if (diff < 0.0) {
                                    continue;
                                }

                                calculation.maxHeightDiff = Math.max(calculation.maxHeightDiff, diff);

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

                                if (diff < 0.4) {
                                    calculation.pushVector = calculation.pushVector.add(flow.scale(diff));
                                } else {
                                    calculation.pushVector = calculation.pushVector.add(flow);
                                }
                            }
                        }
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

            Vec3 pushVector = calculation.pushVector;

            if (pushVector.lengthSqr() == 0.0) {
                continue;
            }

            // note: totalPushes != 0 as pushVector != 0
            pushVector = pushVector.scale(1.0 / calculation.totalPushes);
            final Vec3 currMovement = this.getDeltaMovement();

            if (!((Entity)(Object)this instanceof Player)) {
                pushVector = pushVector.normalize();
            }

            pushVector.scale(this.getFluidMotionScale(type));
            if (Math.abs(currMovement.x) < 0.003 && Math.abs(currMovement.z) < 0.003 && pushVector.length() < 0.0045000000000000005) {
                pushVector = pushVector.normalize().scale(0.0045000000000000005);
            }

            this.setDeltaMovement(currMovement.add(pushVector));
        }
    }
}
