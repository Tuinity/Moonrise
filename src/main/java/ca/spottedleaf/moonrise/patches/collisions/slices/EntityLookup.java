package ca.spottedleaf.moonrise.patches.collisions.slices;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Predicate;

public final class EntityLookup {

    private static final Logger LOGGER = LogUtils.getLogger();

    protected static final int REGION_SHIFT = 5;
    protected static final int REGION_MASK = (1 << REGION_SHIFT) - 1;
    protected static final int REGION_SIZE = 1 << REGION_SHIFT;

    public final Level world;
    protected final Long2ObjectOpenHashMap<ChunkSlicesRegion> regions = new Long2ObjectOpenHashMap<>(128, 0.5f);

    public EntityLookup(final Level world) {
        this.world = world;
    }

    public void getEntitiesWithoutDragonParts(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate) {
        final int minChunkX = (Mth.floor(box.minX) - 2) >> 4;
        final int minChunkZ = (Mth.floor(box.minZ) - 2) >> 4;
        final int maxChunkX = (Mth.floor(box.maxX) + 2) >> 4;
        final int maxChunkZ = (Mth.floor(box.maxZ) + 2) >> 4;

        final int minRegionX = minChunkX >> REGION_SHIFT;
        final int minRegionZ = minChunkZ >> REGION_SHIFT;
        final int maxRegionX = maxChunkX >> REGION_SHIFT;
        final int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int currRegionZ = minRegionZ; currRegionZ <= maxRegionZ; ++currRegionZ) {
            final int minZ = currRegionZ == minRegionZ ? minChunkZ & REGION_MASK : 0;
            final int maxZ = currRegionZ == maxRegionZ ? maxChunkZ & REGION_MASK : REGION_MASK;

            for (int currRegionX = minRegionX; currRegionX <= maxRegionX; ++currRegionX) {
                final ChunkSlicesRegion region = this.getRegion(currRegionX, currRegionZ);

                if (region == null) {
                    continue;
                }

                final int minX = currRegionX == minRegionX ? minChunkX & REGION_MASK : 0;
                final int maxX = currRegionX == maxRegionX ? maxChunkX & REGION_MASK : REGION_MASK;

                for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                    for (int currX = minX; currX <= maxX; ++currX) {
                        final ChunkEntitySlices chunk = region.get(currX | (currZ << REGION_SHIFT));
                        if (chunk == null || !chunk.sectionVisibility.isAccessible()) {
                            continue;
                        }

                        chunk.getEntitiesWithoutDragonParts(except, box, into, predicate);
                    }
                }
            }
        }
    }

    public void getEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate) {
        final int minChunkX = (Mth.floor(box.minX) - 2) >> 4;
        final int minChunkZ = (Mth.floor(box.minZ) - 2) >> 4;
        final int maxChunkX = (Mth.floor(box.maxX) + 2) >> 4;
        final int maxChunkZ = (Mth.floor(box.maxZ) + 2) >> 4;

        final int minRegionX = minChunkX >> REGION_SHIFT;
        final int minRegionZ = minChunkZ >> REGION_SHIFT;
        final int maxRegionX = maxChunkX >> REGION_SHIFT;
        final int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int currRegionZ = minRegionZ; currRegionZ <= maxRegionZ; ++currRegionZ) {
            final int minZ = currRegionZ == minRegionZ ? minChunkZ & REGION_MASK : 0;
            final int maxZ = currRegionZ == maxRegionZ ? maxChunkZ & REGION_MASK : REGION_MASK;

            for (int currRegionX = minRegionX; currRegionX <= maxRegionX; ++currRegionX) {
                final ChunkSlicesRegion region = this.getRegion(currRegionX, currRegionZ);

                if (region == null) {
                    continue;
                }

                final int minX = currRegionX == minRegionX ? minChunkX & REGION_MASK : 0;
                final int maxX = currRegionX == maxRegionX ? maxChunkX & REGION_MASK : REGION_MASK;

                for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                    for (int currX = minX; currX <= maxX; ++currX) {
                        final ChunkEntitySlices chunk = region.get(currX | (currZ << REGION_SHIFT));
                        if (chunk == null || !chunk.sectionVisibility.isAccessible()) {
                            continue;
                        }

                        chunk.getEntities(except, box, into, predicate);
                    }
                }
            }
        }
    }

    public void getHardCollidingEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate) {
        final int minChunkX = (Mth.floor(box.minX) - 2) >> 4;
        final int minChunkZ = (Mth.floor(box.minZ) - 2) >> 4;
        final int maxChunkX = (Mth.floor(box.maxX) + 2) >> 4;
        final int maxChunkZ = (Mth.floor(box.maxZ) + 2) >> 4;

        final int minRegionX = minChunkX >> REGION_SHIFT;
        final int minRegionZ = minChunkZ >> REGION_SHIFT;
        final int maxRegionX = maxChunkX >> REGION_SHIFT;
        final int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int currRegionZ = minRegionZ; currRegionZ <= maxRegionZ; ++currRegionZ) {
            final int minZ = currRegionZ == minRegionZ ? minChunkZ & REGION_MASK : 0;
            final int maxZ = currRegionZ == maxRegionZ ? maxChunkZ & REGION_MASK : REGION_MASK;

            for (int currRegionX = minRegionX; currRegionX <= maxRegionX; ++currRegionX) {
                final ChunkSlicesRegion region = this.getRegion(currRegionX, currRegionZ);

                if (region == null) {
                    continue;
                }

                final int minX = currRegionX == minRegionX ? minChunkX & REGION_MASK : 0;
                final int maxX = currRegionX == maxRegionX ? maxChunkX & REGION_MASK : REGION_MASK;

                for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                    for (int currX = minX; currX <= maxX; ++currX) {
                        final ChunkEntitySlices chunk = region.get(currX | (currZ << REGION_SHIFT));
                        if (chunk == null || !chunk.sectionVisibility.isAccessible()) {
                            continue;
                        }

                        chunk.getHardCollidingEntities(except, box, into, predicate);
                    }
                }
            }
        }
    }

    public <T extends Entity> void getEntities(final EntityType<?> type, final AABB box, final List<? super T> into,
                                               final Predicate<? super T> predicate) {
        final int minChunkX = (Mth.floor(box.minX) - 2) >> 4;
        final int minChunkZ = (Mth.floor(box.minZ) - 2) >> 4;
        final int maxChunkX = (Mth.floor(box.maxX) + 2) >> 4;
        final int maxChunkZ = (Mth.floor(box.maxZ) + 2) >> 4;

        final int minRegionX = minChunkX >> REGION_SHIFT;
        final int minRegionZ = minChunkZ >> REGION_SHIFT;
        final int maxRegionX = maxChunkX >> REGION_SHIFT;
        final int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int currRegionZ = minRegionZ; currRegionZ <= maxRegionZ; ++currRegionZ) {
            final int minZ = currRegionZ == minRegionZ ? minChunkZ & REGION_MASK : 0;
            final int maxZ = currRegionZ == maxRegionZ ? maxChunkZ & REGION_MASK : REGION_MASK;

            for (int currRegionX = minRegionX; currRegionX <= maxRegionX; ++currRegionX) {
                final ChunkSlicesRegion region = this.getRegion(currRegionX, currRegionZ);

                if (region == null) {
                    continue;
                }

                final int minX = currRegionX == minRegionX ? minChunkX & REGION_MASK : 0;
                final int maxX = currRegionX == maxRegionX ? maxChunkX & REGION_MASK : REGION_MASK;

                for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                    for (int currX = minX; currX <= maxX; ++currX) {
                        final ChunkEntitySlices chunk = region.get(currX | (currZ << REGION_SHIFT));
                        if (chunk == null || !chunk.sectionVisibility.isAccessible()) {
                            continue;
                        }

                        chunk.getEntities(type, box, (List)into, (Predicate)predicate);
                    }
                }
            }
        }
    }

    public <T extends Entity> void getEntities(final Class<? extends T> clazz, final Entity except, final AABB box, final List<? super T> into,
                                               final Predicate<? super T> predicate) {
        final int minChunkX = (Mth.floor(box.minX) - 2) >> 4;
        final int minChunkZ = (Mth.floor(box.minZ) - 2) >> 4;
        final int maxChunkX = (Mth.floor(box.maxX) + 2) >> 4;
        final int maxChunkZ = (Mth.floor(box.maxZ) + 2) >> 4;

        final int minRegionX = minChunkX >> REGION_SHIFT;
        final int minRegionZ = minChunkZ >> REGION_SHIFT;
        final int maxRegionX = maxChunkX >> REGION_SHIFT;
        final int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int currRegionZ = minRegionZ; currRegionZ <= maxRegionZ; ++currRegionZ) {
            final int minZ = currRegionZ == minRegionZ ? minChunkZ & REGION_MASK : 0;
            final int maxZ = currRegionZ == maxRegionZ ? maxChunkZ & REGION_MASK : REGION_MASK;

            for (int currRegionX = minRegionX; currRegionX <= maxRegionX; ++currRegionX) {
                final ChunkSlicesRegion region = this.getRegion(currRegionX, currRegionZ);

                if (region == null) {
                    continue;
                }

                final int minX = currRegionX == minRegionX ? minChunkX & REGION_MASK : 0;
                final int maxX = currRegionX == maxRegionX ? maxChunkX & REGION_MASK : REGION_MASK;

                for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                    for (int currX = minX; currX <= maxX; ++currX) {
                        final ChunkEntitySlices chunk = region.get(currX | (currZ << REGION_SHIFT));
                        if (chunk == null || !chunk.sectionVisibility.isAccessible()) {
                            continue;
                        }

                        chunk.getEntities(clazz, except, box, into, predicate);
                    }
                }
            }
        }
    }

    //////// Limited ////////

    public void getEntitiesWithoutDragonParts(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate,
                                              final int maxCount) {
        final int minChunkX = (Mth.floor(box.minX) - 2) >> 4;
        final int minChunkZ = (Mth.floor(box.minZ) - 2) >> 4;
        final int maxChunkX = (Mth.floor(box.maxX) + 2) >> 4;
        final int maxChunkZ = (Mth.floor(box.maxZ) + 2) >> 4;

        final int minRegionX = minChunkX >> REGION_SHIFT;
        final int minRegionZ = minChunkZ >> REGION_SHIFT;
        final int maxRegionX = maxChunkX >> REGION_SHIFT;
        final int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int currRegionZ = minRegionZ; currRegionZ <= maxRegionZ; ++currRegionZ) {
            final int minZ = currRegionZ == minRegionZ ? minChunkZ & REGION_MASK : 0;
            final int maxZ = currRegionZ == maxRegionZ ? maxChunkZ & REGION_MASK : REGION_MASK;

            for (int currRegionX = minRegionX; currRegionX <= maxRegionX; ++currRegionX) {
                final ChunkSlicesRegion region = this.getRegion(currRegionX, currRegionZ);

                if (region == null) {
                    continue;
                }

                final int minX = currRegionX == minRegionX ? minChunkX & REGION_MASK : 0;
                final int maxX = currRegionX == maxRegionX ? maxChunkX & REGION_MASK : REGION_MASK;

                for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                    for (int currX = minX; currX <= maxX; ++currX) {
                        final ChunkEntitySlices chunk = region.get(currX | (currZ << REGION_SHIFT));
                        if (chunk == null || !chunk.sectionVisibility.isAccessible()) {
                            continue;
                        }

                        if (chunk.getEntitiesWithoutDragonParts(except, box, into, predicate, maxCount)) {
                            return;
                        }
                    }
                }
            }
        }
    }

    public void getEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate,
                            final int maxCount) {
        final int minChunkX = (Mth.floor(box.minX) - 2) >> 4;
        final int minChunkZ = (Mth.floor(box.minZ) - 2) >> 4;
        final int maxChunkX = (Mth.floor(box.maxX) + 2) >> 4;
        final int maxChunkZ = (Mth.floor(box.maxZ) + 2) >> 4;

        final int minRegionX = minChunkX >> REGION_SHIFT;
        final int minRegionZ = minChunkZ >> REGION_SHIFT;
        final int maxRegionX = maxChunkX >> REGION_SHIFT;
        final int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int currRegionZ = minRegionZ; currRegionZ <= maxRegionZ; ++currRegionZ) {
            final int minZ = currRegionZ == minRegionZ ? minChunkZ & REGION_MASK : 0;
            final int maxZ = currRegionZ == maxRegionZ ? maxChunkZ & REGION_MASK : REGION_MASK;

            for (int currRegionX = minRegionX; currRegionX <= maxRegionX; ++currRegionX) {
                final ChunkSlicesRegion region = this.getRegion(currRegionX, currRegionZ);

                if (region == null) {
                    continue;
                }

                final int minX = currRegionX == minRegionX ? minChunkX & REGION_MASK : 0;
                final int maxX = currRegionX == maxRegionX ? maxChunkX & REGION_MASK : REGION_MASK;

                for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                    for (int currX = minX; currX <= maxX; ++currX) {
                        final ChunkEntitySlices chunk = region.get(currX | (currZ << REGION_SHIFT));
                        if (chunk == null || !chunk.sectionVisibility.isAccessible()) {
                            continue;
                        }

                        if (chunk.getEntities(except, box, into, predicate, maxCount)) {
                            return;
                        }
                    }
                }
            }
        }
    }

    public <T extends Entity> void getEntities(final EntityType<?> type, final AABB box, final List<? super T> into,
                                               final Predicate<? super T> predicate, final int maxCount) {
        final int minChunkX = (Mth.floor(box.minX) - 2) >> 4;
        final int minChunkZ = (Mth.floor(box.minZ) - 2) >> 4;
        final int maxChunkX = (Mth.floor(box.maxX) + 2) >> 4;
        final int maxChunkZ = (Mth.floor(box.maxZ) + 2) >> 4;

        final int minRegionX = minChunkX >> REGION_SHIFT;
        final int minRegionZ = minChunkZ >> REGION_SHIFT;
        final int maxRegionX = maxChunkX >> REGION_SHIFT;
        final int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int currRegionZ = minRegionZ; currRegionZ <= maxRegionZ; ++currRegionZ) {
            final int minZ = currRegionZ == minRegionZ ? minChunkZ & REGION_MASK : 0;
            final int maxZ = currRegionZ == maxRegionZ ? maxChunkZ & REGION_MASK : REGION_MASK;

            for (int currRegionX = minRegionX; currRegionX <= maxRegionX; ++currRegionX) {
                final ChunkSlicesRegion region = this.getRegion(currRegionX, currRegionZ);

                if (region == null) {
                    continue;
                }

                final int minX = currRegionX == minRegionX ? minChunkX & REGION_MASK : 0;
                final int maxX = currRegionX == maxRegionX ? maxChunkX & REGION_MASK : REGION_MASK;

                for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                    for (int currX = minX; currX <= maxX; ++currX) {
                        final ChunkEntitySlices chunk = region.get(currX | (currZ << REGION_SHIFT));
                        if (chunk == null || !chunk.sectionVisibility.isAccessible()) {
                            continue;
                        }

                        if (chunk.getEntities(type, box, (List)into, (Predicate)predicate, maxCount)) {
                            return;
                        }
                    }
                }
            }
        }
    }

    public <T extends Entity> void getEntities(final Class<? extends T> clazz, final Entity except, final AABB box, final List<? super T> into,
                                               final Predicate<? super T> predicate, final int maxCount) {
        final int minChunkX = (Mth.floor(box.minX) - 2) >> 4;
        final int minChunkZ = (Mth.floor(box.minZ) - 2) >> 4;
        final int maxChunkX = (Mth.floor(box.maxX) + 2) >> 4;
        final int maxChunkZ = (Mth.floor(box.maxZ) + 2) >> 4;

        final int minRegionX = minChunkX >> REGION_SHIFT;
        final int minRegionZ = minChunkZ >> REGION_SHIFT;
        final int maxRegionX = maxChunkX >> REGION_SHIFT;
        final int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int currRegionZ = minRegionZ; currRegionZ <= maxRegionZ; ++currRegionZ) {
            final int minZ = currRegionZ == minRegionZ ? minChunkZ & REGION_MASK : 0;
            final int maxZ = currRegionZ == maxRegionZ ? maxChunkZ & REGION_MASK : REGION_MASK;

            for (int currRegionX = minRegionX; currRegionX <= maxRegionX; ++currRegionX) {
                final ChunkSlicesRegion region = this.getRegion(currRegionX, currRegionZ);

                if (region == null) {
                    continue;
                }

                final int minX = currRegionX == minRegionX ? minChunkX & REGION_MASK : 0;
                final int maxX = currRegionX == maxRegionX ? maxChunkX & REGION_MASK : REGION_MASK;

                for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                    for (int currX = minX; currX <= maxX; ++currX) {
                        final ChunkEntitySlices chunk = region.get(currX | (currZ << REGION_SHIFT));
                        if (chunk == null || !chunk.sectionVisibility.isAccessible()) {
                            continue;
                        }

                        if (chunk.getEntities(clazz, except, box, into, predicate, maxCount)) {
                            return;
                        }
                    }
                }
            }
        }
    }

    public ChunkEntitySlices getChunk(final int chunkX, final int chunkZ) {
        final ChunkSlicesRegion region = this.getRegion(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
        if (region == null) {
            return null;
        }

        return region.get((chunkX & REGION_MASK) | ((chunkZ & REGION_MASK) << REGION_SHIFT));
    }

    public ChunkEntitySlices getOrCreateChunk(final int chunkX, final int chunkZ) {
        final ChunkSlicesRegion region = this.getRegion(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
        final int localIdx = (chunkX & REGION_MASK) | ((chunkZ & REGION_MASK) << REGION_SHIFT);

        ChunkEntitySlices ret;
        if (region != null && (ret = region.get(localIdx)) != null) {
            return ret;
        }

        ret = new ChunkEntitySlices(this.world, chunkX, chunkZ);
        this.addChunk(chunkX, chunkZ, ret);
        return ret;
    }

    public ChunkSlicesRegion getRegion(final int regionX, final int regionZ) {
        return this.regions.get(CoordinateUtils.getChunkKey(regionX, regionZ));
    }

    private void removeChunk(final int chunkX, final int chunkZ) {
        final long key = CoordinateUtils.getChunkKey(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
        final int relIndex = (chunkX & REGION_MASK) | ((chunkZ & REGION_MASK) << REGION_SHIFT);

        final ChunkSlicesRegion region = this.regions.get(key);
        final int remaining = region.remove(relIndex);

        if (remaining == 0) {
            this.regions.remove(key);
        }
    }

    private void addChunk(final int chunkX, final int chunkZ, final ChunkEntitySlices slices) {
        final long key = CoordinateUtils.getChunkKey(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
        final int relIndex = (chunkX & REGION_MASK) | ((chunkZ & REGION_MASK) << REGION_SHIFT);

        ChunkSlicesRegion region = this.regions.get(key);
        if (region != null) {
            region.add(relIndex, slices);
        } else {
            region = new ChunkSlicesRegion();
            region.add(relIndex, slices);
            this.regions.put(key, region);
        }
    }

    public void addEntity(final Entity entity) {
        final BlockPos pos = entity.blockPosition();
        final int sectionX = pos.getX() >> 4;
        final int sectionY = Mth.clamp(pos.getY() >> 4, WorldUtil.getMinSection(this.world), WorldUtil.getMaxSection(this.world));
        final int sectionZ = pos.getZ() >> 4;

        if (entity.isRemoved()) {
            LOGGER.warn("Refusing to add removed entity: " + entity);
            return;
        }

        final ChunkEntitySlices slices = this.getOrCreateChunk(sectionX, sectionZ);
        if (!slices.addEntity(entity, sectionY)) {
            LOGGER.warn("Entity " + entity + " added to world, but was already contained in entity chunk (" + sectionX + "," + sectionZ + ")");
        }

        return;
    }

    public void moveEntity(final Entity entity, final long oldSection) {
        final int minSection = WorldUtil.getMinSection(this.world);
        final int maxSection = WorldUtil.getMaxSection(this.world);

        final int oldSectionX = CoordinateUtils.getChunkSectionX(oldSection);
        final int oldSectionY = Mth.clamp(CoordinateUtils.getChunkSectionY(oldSection), minSection, maxSection);
        final int oldSectionZ = CoordinateUtils.getChunkSectionZ(oldSection);

        final BlockPos newPos = entity.blockPosition();
        final int newSectionX = newPos.getX() >> 4;
        final int newSectionY = Mth.clamp(newPos.getY() >> 4, minSection, maxSection);
        final int newSectionZ = newPos.getZ() >> 4;

        final ChunkEntitySlices old = this.getChunk(oldSectionX, oldSectionZ);
        final ChunkEntitySlices slices = this.getOrCreateChunk(newSectionX, newSectionZ);

        if (!old.removeEntity(entity, oldSectionY)) {
            LOGGER.warn("Could not remove entity " + entity + " from its old chunk section (" + oldSectionX + "," + oldSectionY + "," + oldSectionZ + ") since it was not contained in the section");
        }

        if (!slices.addEntity(entity, newSectionY)) {
            LOGGER.warn("Could not add entity " + entity + " to its new chunk section (" + newSectionX + "," + newSectionY + "," + newSectionZ + ") as it is already contained in the section");
        }
    }

    public void removeEntity(final Entity entity) {
        final BlockPos newPos = entity.blockPosition();
        final int sectionX = newPos.getX() >> 4;
        final int sectionY = Mth.clamp(newPos.getY() >> 4, WorldUtil.getMinSection(this.world), WorldUtil.getMaxSection(this.world));
        final int sectionZ = newPos.getZ() >> 4;

        final ChunkEntitySlices slices = this.getChunk(sectionX, sectionZ);
        // all entities should be in a chunk
        if (slices == null) {
            LOGGER.warn("Cannot remove entity " + entity + " from null entity slices (" + sectionX + "," + sectionZ + ")");
        } else {
            if (!slices.removeEntity(entity, sectionY)) {
                LOGGER.warn("Failed to remove entity " + entity + " from entity slices (" + sectionX + "," + sectionZ + ")");
            }
        }
    }

    public static final class ChunkSlicesRegion {

        protected final ChunkEntitySlices[] slices = new ChunkEntitySlices[REGION_SIZE * REGION_SIZE];
        protected int sliceCount;

        public ChunkEntitySlices get(final int index) {
            return this.slices[index];
        }

        public int remove(final int index) {
            final ChunkEntitySlices slices = this.slices[index];
            if (slices == null) {
                throw new IllegalStateException();
            }

            this.slices[index] = null;

            return --this.sliceCount;
        }

        public void add(final int index, final ChunkEntitySlices slices) {
            final ChunkEntitySlices curr = this.slices[index];
            if (curr != null) {
                throw new IllegalStateException();
            }

            this.slices[index] = slices;

            ++this.sliceCount;
        }
    }
}
