package ca.spottedleaf.moonrise.patches.chunk_system.level.entity;

import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable;
import ca.spottedleaf.concurrentutil.map.SWMRLong2ObjectHashTable;
import ca.spottedleaf.moonrise.common.list.EntityList;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class EntityLookup implements LevelEntityGetter<Entity> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityLookup.class);

    protected static final int REGION_SHIFT = 5;
    protected static final int REGION_MASK = (1 << REGION_SHIFT) - 1;
    protected static final int REGION_SIZE = 1 << REGION_SHIFT;

    public final Level world;

    protected final SWMRLong2ObjectHashTable<ChunkSlicesRegion> regions = new SWMRLong2ObjectHashTable<>(128, 0.5f);

    protected final int minSection; // inclusive
    protected final int maxSection; // inclusive
    protected final LevelCallback<Entity> worldCallback;

    protected final ConcurrentLong2ReferenceChainedHashTable<Entity> entityById = new ConcurrentLong2ReferenceChainedHashTable<>();
    protected final ConcurrentHashMap<UUID, Entity> entityByUUID = new ConcurrentHashMap<>();
    protected final EntityList accessibleEntities = new EntityList();

    public EntityLookup(final Level world, final LevelCallback<Entity> worldCallback) {
        this.world = world;
        this.minSection = WorldUtil.getMinSection(world);
        this.maxSection = WorldUtil.getMaxSection(world);
        this.worldCallback = worldCallback;
    }

    protected abstract Boolean blockTicketUpdates();

    protected abstract void setBlockTicketUpdates(final Boolean value);

    protected abstract void checkThread(final int chunkX, final int chunkZ, final String reason);

    protected abstract void checkThread(final Entity entity, final String reason);

    protected abstract ChunkEntitySlices createEntityChunk(final int chunkX, final int chunkZ, final boolean transientChunk);

    protected abstract void onEmptySlices(final int chunkX, final int chunkZ);

    protected abstract void entitySectionChangeCallback(
            final Entity entity,
            final int oldSectionX, final int oldSectionY, final int oldSectionZ,
            final int newSectionX, final int newSectionY, final int newSectionZ
    );

    protected abstract void addEntityCallback(final Entity entity);

    protected abstract void removeEntityCallback(final Entity entity);

    protected abstract void entityStartLoaded(final Entity entity);

    protected abstract void entityEndLoaded(final Entity entity);

    protected abstract void entityStartTicking(final Entity entity);

    protected abstract void entityEndTicking(final Entity entity);

    protected abstract boolean screenEntity(final Entity entity);

    private static Entity maskNonAccessible(final Entity entity) {
        if (entity == null) {
            return null;
        }
        final Visibility visibility = EntityLookup.getEntityStatus(entity);
        return visibility.isAccessible() ? entity : null;
    }

    @Override
    public Entity get(final int id) {
        return maskNonAccessible(this.entityById.get((long)id));
    }

    @Override
    public Entity get(final UUID id) {
        return maskNonAccessible(id == null ? null : this.entityByUUID.get(id));
    }

    public boolean hasEntity(final UUID uuid) {
        return this.get(uuid) != null;
    }

    public String getDebugInfo() {
        return "count_id:" + this.entityById.size() + ",count_uuid:" + this.entityByUUID.size() + ",count_accessible:" + this.getEntityCount() + ",region_count:" + this.regions.size();
    }

    protected static final class ArrayIterable<T> implements Iterable<T> {

        private final T[] array;
        private final int off;
        private final int length;

        public ArrayIterable(final T[] array, final int off, final int length) {
            this.array = array;
            this.off = off;
            this.length = length;
            if (length > array.length) {
                throw new IllegalArgumentException("Length must be no greater-than the array length");
            }
        }

        @Override
        public Iterator<T> iterator() {
            return new ArrayIterator<>(this.array, this.off, this.length);
        }

        protected static final class ArrayIterator<T> implements Iterator<T> {

            private final T[] array;
            private int off;
            private final int length;

            public ArrayIterator(final T[] array, final int off, final int length) {
                this.array = array;
                this.off = off;
                this.length = length;
            }

            @Override
            public boolean hasNext() {
                return this.off < this.length;
            }

            @Override
            public T next() {
                if (this.off >= this.length) {
                    throw new NoSuchElementException();
                }
                return this.array[this.off++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        }
    }

    @Override
    public Iterable<Entity> getAll() {
        synchronized (this.accessibleEntities) {
            final int len = this.accessibleEntities.size();
            final Entity[] cpy = Arrays.copyOf(this.accessibleEntities.getRawData(), len, Entity[].class);

            Objects.checkFromToIndex(0, len, cpy.length);

            return new ArrayIterable<>(cpy, 0, len);
        }
    }

    public int getEntityCount() {
        synchronized (this.accessibleEntities) {
            return this.accessibleEntities.size();
        }
    }

    public Entity[] getAllCopy() {
        synchronized (this.accessibleEntities) {
            return Arrays.copyOf(this.accessibleEntities.getRawData(), this.accessibleEntities.size(), Entity[].class);
        }
    }

    @Override
    public <U extends Entity> void get(final EntityTypeTest<Entity, U> filter, final AbortableIterationConsumer<U> action) {
        for (final Iterator<Entity> iterator = this.entityById.valueIterator(); iterator.hasNext();) {
            final Entity entity = iterator.next();
            final Visibility visibility = EntityLookup.getEntityStatus(entity);
            if (!visibility.isAccessible()) {
                continue;
            }
            final U casted = filter.tryCast(entity);
            if (casted != null && action.accept(casted).shouldAbort()) {
                break;
            }
        }
    }

    @Override
    public void get(final AABB box, final Consumer<Entity> action) {
        List<Entity> entities = new ArrayList<>();
        this.getEntitiesWithoutDragonParts(null, box, entities, null);
        for (int i = 0, len = entities.size(); i < len; ++i) {
            action.accept(entities.get(i));
        }
    }

    @Override
    public <U extends Entity> void get(final EntityTypeTest<Entity, U> filter, final AABB box, final AbortableIterationConsumer<U> action) {
        List<Entity> entities = new ArrayList<>();
        this.getEntitiesWithoutDragonParts(null, box, entities, null);
        for (int i = 0, len = entities.size(); i < len; ++i) {
            final U casted = filter.tryCast(entities.get(i));
            if (casted != null && action.accept(casted).shouldAbort()) {
                break;
            }
        }
    }

    public void entityStatusChange(final Entity entity, final ChunkEntitySlices slices, final Visibility oldVisibility, final Visibility newVisibility, final boolean moved,
                                   final boolean created, final boolean destroyed) {
        this.checkThread(entity, "Entity status change must only happen on the main thread");

        if (((ChunkSystemEntity)entity).moonrise$isUpdatingSectionStatus()) {
            // recursive status update
            LOGGER.error("Cannot recursively update entity chunk status for entity " + entity, new Throwable());
            return;
        }

        final boolean entityStatusUpdateBefore = slices == null ? false : slices.startPreventingStatusUpdates();

        if (entityStatusUpdateBefore) {
            LOGGER.error("Cannot update chunk status for entity " + entity + " since entity chunk (" + slices.chunkX + "," + slices.chunkZ + ") is receiving update", new Throwable());
            return;
        }

        try {
            final Boolean ticketBlockBefore = this.blockTicketUpdates();
            try {
                ((ChunkSystemEntity)entity).moonrise$setUpdatingSectionStatus(true);
                try {
                    if (created) {
                        if (EntityLookup.this.worldCallback != null) {
                            EntityLookup.this.worldCallback.onCreated(entity);
                        }
                    }

                    if (oldVisibility == newVisibility) {
                        if (moved && newVisibility.isAccessible()) {
                            if (EntityLookup.this.worldCallback != null) {
                                EntityLookup.this.worldCallback.onSectionChange(entity);
                            }
                        }
                        return;
                    }

                    if (newVisibility.ordinal() > oldVisibility.ordinal()) {
                        // status upgrade
                        if (!oldVisibility.isAccessible() && newVisibility.isAccessible()) {
                            EntityLookup.this.entityStartLoaded(entity);
                            synchronized (this.accessibleEntities) {
                                this.accessibleEntities.add(entity);
                            }
                            if (EntityLookup.this.worldCallback != null) {
                                EntityLookup.this.worldCallback.onTrackingStart(entity);
                            }
                        }

                        if (!oldVisibility.isTicking() && newVisibility.isTicking()) {
                            EntityLookup.this.entityStartTicking(entity);
                            if (EntityLookup.this.worldCallback != null) {
                                EntityLookup.this.worldCallback.onTickingStart(entity);
                            }
                        }
                    } else {
                        // status downgrade
                        if (oldVisibility.isTicking() && !newVisibility.isTicking()) {
                            EntityLookup.this.entityEndTicking(entity);
                            if (EntityLookup.this.worldCallback != null) {
                                EntityLookup.this.worldCallback.onTickingEnd(entity);
                            }
                        }

                        if (oldVisibility.isAccessible() && !newVisibility.isAccessible()) {
                            EntityLookup.this.entityEndLoaded(entity);
                            synchronized (this.accessibleEntities) {
                                this.accessibleEntities.remove(entity);
                            }
                            if (EntityLookup.this.worldCallback != null) {
                                EntityLookup.this.worldCallback.onTrackingEnd(entity);
                            }
                        }
                    }

                    if (moved && newVisibility.isAccessible()) {
                        if (EntityLookup.this.worldCallback != null) {
                            EntityLookup.this.worldCallback.onSectionChange(entity);
                        }
                    }

                    if (destroyed) {
                        if (EntityLookup.this.worldCallback != null) {
                            EntityLookup.this.worldCallback.onDestroyed(entity);
                        }
                    }
                } finally {
                    ((ChunkSystemEntity)entity).moonrise$setUpdatingSectionStatus(false);
                }
            } finally {
                this.setBlockTicketUpdates(ticketBlockBefore);
            }
        } finally {
            if (slices != null) {
                slices.stopPreventingStatusUpdates(false);
            }
        }
    }

    public void chunkStatusChange(final int x, final int z, final FullChunkStatus newStatus) {
        this.getChunk(x, z).updateStatus(newStatus, this);
    }

    public void addLegacyChunkEntities(final List<Entity> entities, final ChunkPos forChunk) {
        this.addEntityChunk(entities, forChunk, true);
    }

    public void addEntityChunkEntities(final List<Entity> entities, final ChunkPos forChunk) {
        this.addEntityChunk(entities, forChunk, true);
    }

    public void addWorldGenChunkEntities(final List<Entity> entities, final ChunkPos forChunk) {
        this.addEntityChunk(entities, forChunk, false);
    }

    protected void addRecursivelySafe(final Entity root, final boolean fromDisk) {
        if (!this.addEntity(root, fromDisk)) {
            // possible we are a passenger, and so should dismount from any valid entity in the world
            root.stopRiding();
            return;
        }
        for (final Entity passenger : root.getPassengers()) {
            this.addRecursivelySafe(passenger, fromDisk);
        }
    }

    protected void addEntityChunk(final List<Entity> entities, final ChunkPos forChunk, final boolean fromDisk) {
        for (int i = 0, len = entities.size(); i < len; ++i) {
            final Entity entity = entities.get(i);
            if (entity.isPassenger()) {
                continue;
            }

            if (forChunk != null && !entity.chunkPosition().equals(forChunk)) {
                LOGGER.warn("Root entity " + entity + " is outside of serialized chunk " + forChunk);
                // can't set removed here, as we may not own the chunk position
                // skip the entity
                continue;
            }

            final Vec3 rootPosition = entity.position();

            // always adjust positions before adding passengers in case plugins access the entity, and so that
            // they are added to the right entity chunk
            for (final Entity passenger : entity.getIndirectPassengers()) {
                if (forChunk != null && !passenger.chunkPosition().equals(forChunk)) {
                    passenger.setPosRaw(rootPosition.x, rootPosition.y, rootPosition.z);
                }
            }

            this.addRecursivelySafe(entity, fromDisk);
         }
    }

    public boolean addNewEntity(final Entity entity) {
        return this.addEntity(entity, false);
    }

    public static Visibility getEntityStatus(final Entity entity) {
        if (entity.isAlwaysTicking()) {
            return Visibility.TICKING;
        }
        final FullChunkStatus entityStatus = ((ChunkSystemEntity)entity).moonrise$getChunkStatus();
        return Visibility.fromFullChunkStatus(entityStatus == null ? FullChunkStatus.INACCESSIBLE : entityStatus);
    }

    protected boolean addEntity(final Entity entity, final boolean fromDisk) {
        final BlockPos pos = entity.blockPosition();
        final int sectionX = pos.getX() >> 4;
        final int sectionY = Mth.clamp(pos.getY() >> 4, this.minSection, this.maxSection);
        final int sectionZ = pos.getZ() >> 4;
        this.checkThread(sectionX, sectionZ, "Cannot add entity off-main thread");

        if (entity.isRemoved()) {
            LOGGER.warn("Refusing to add removed entity: " + entity);
            return false;
        }

        if (((ChunkSystemEntity)entity).moonrise$isUpdatingSectionStatus()) {
            LOGGER.warn("Entity " + entity + " is currently prevented from being added/removed to world since it is processing section status updates", new Throwable());
            return false;
        }

        if (!this.screenEntity(entity)) {
            return false;
        }

        Entity currentlyMapped = this.entityById.putIfAbsent((long)entity.getId(), entity);
        if (currentlyMapped != null) {
            LOGGER.warn("Entity id already exists: " + entity.getId() + ", mapped to " + currentlyMapped + ", can't add " + entity);
            return false;
        }

        currentlyMapped = this.entityByUUID.putIfAbsent(entity.getUUID(), entity);
        if (currentlyMapped != null) {
            // need to remove mapping for id
            this.entityById.remove((long)entity.getId(), entity);
            LOGGER.warn("Entity uuid already exists: " + entity.getUUID() + ", mapped to " + currentlyMapped + ", can't add " + entity);
            return false;
        }

        ((ChunkSystemEntity)entity).moonrise$setSectionX(sectionX);
        ((ChunkSystemEntity)entity).moonrise$setSectionY(sectionY);
        ((ChunkSystemEntity)entity).moonrise$setSectionZ(sectionZ);
        final ChunkEntitySlices slices = this.getOrCreateChunk(sectionX, sectionZ);
        if (!slices.addEntity(entity, sectionY)) {
            LOGGER.warn("Entity " + entity + " added to world '" + WorldUtil.getWorldName(this.world) + "', but was already contained in entity chunk (" + sectionX + "," + sectionZ + ")");
        }

        entity.setLevelCallback(new EntityCallback(entity));

        this.addEntityCallback(entity);

        this.entityStatusChange(entity, slices, Visibility.HIDDEN, getEntityStatus(entity), false, !fromDisk, false);

        return true;
    }

    public boolean canRemoveEntity(final Entity entity) {
        if (((ChunkSystemEntity)entity).moonrise$isUpdatingSectionStatus()) {
            return false;
        }

        final int sectionX = ((ChunkSystemEntity)entity).moonrise$getSectionX();
        final int sectionZ = ((ChunkSystemEntity)entity).moonrise$getSectionZ();
        final ChunkEntitySlices slices = this.getChunk(sectionX, sectionZ);
        return slices == null || !slices.isPreventingStatusUpdates();
    }

    protected void removeEntity(final Entity entity) {
        final int sectionX = ((ChunkSystemEntity)entity).moonrise$getSectionX();
        final int sectionY = ((ChunkSystemEntity)entity).moonrise$getSectionY();
        final int sectionZ = ((ChunkSystemEntity)entity).moonrise$getSectionZ();
        this.checkThread(sectionX, sectionZ, "Cannot remove entity off-main");
        if (!entity.isRemoved()) {
            throw new IllegalStateException("Only call Entity#setRemoved to remove an entity");
        }
        final ChunkEntitySlices slices = this.getChunk(sectionX, sectionZ);
        // all entities should be in a chunk
        if (slices == null) {
            LOGGER.warn("Cannot remove entity " + entity + " from null entity slices (" + sectionX + "," + sectionZ + ")");
        } else {
            if (slices.isPreventingStatusUpdates()) {
                throw new IllegalStateException("Attempting to remove entity " + entity + " from entity slices (" + sectionX + "," + sectionZ + ") that is receiving status updates");
            }
            if (!slices.removeEntity(entity, sectionY)) {
                LOGGER.warn("Failed to remove entity " + entity + " from entity slices (" + sectionX + "," + sectionZ + ")");
            }
        }
        ((ChunkSystemEntity)entity).moonrise$setSectionX(Integer.MIN_VALUE);
        ((ChunkSystemEntity)entity).moonrise$setSectionY(Integer.MIN_VALUE);
        ((ChunkSystemEntity)entity).moonrise$setSectionZ(Integer.MIN_VALUE);


        Entity currentlyMapped;
        if ((currentlyMapped = this.entityById.remove(entity.getId(), entity)) != entity) {
            LOGGER.warn("Failed to remove entity " + entity + " by id, current entity mapped: " + currentlyMapped);
        }

        Entity[] currentlyMappedArr = new Entity[1];

        // need reference equality
        this.entityByUUID.compute(entity.getUUID(), (final UUID keyInMap, final Entity valueInMap) -> {
            currentlyMappedArr[0] = valueInMap;
            if (valueInMap != entity) {
                return valueInMap;
            }
            return null;
        });

        if (currentlyMappedArr[0] != entity) {
            LOGGER.warn("Failed to remove entity " + entity + " by uuid, current entity mapped: " + currentlyMappedArr[0]);
        }

        if (slices != null && slices.isEmpty()) {
            this.onEmptySlices(sectionX, sectionZ);
        }
    }

    protected ChunkEntitySlices moveEntity(final Entity entity) {
        // ensure we own the entity
        this.checkThread(entity, "Cannot move entity off-main");

        final int sectionX = ((ChunkSystemEntity)entity).moonrise$getSectionX();
        final int sectionY = ((ChunkSystemEntity)entity).moonrise$getSectionY();
        final int sectionZ = ((ChunkSystemEntity)entity).moonrise$getSectionZ();
        final BlockPos newPos = entity.blockPosition();
        final int newSectionX = newPos.getX() >> 4;
        final int newSectionY = Mth.clamp(newPos.getY() >> 4, this.minSection, this.maxSection);
        final int newSectionZ = newPos.getZ() >> 4;

        if (newSectionX == sectionX && newSectionY == sectionY && newSectionZ == sectionZ) {
            return null;
        }

        // ensure the new section is owned by this tick thread
        this.checkThread(newSectionX, newSectionZ, "Cannot move entity off-main");

        // ensure the old section is owned by this tick thread
        this.checkThread(sectionX, sectionZ, "Cannot move entity off-main");

        final ChunkEntitySlices old = this.getChunk(sectionX, sectionZ);
        final ChunkEntitySlices slices = this.getOrCreateChunk(newSectionX, newSectionZ);

        if (!old.removeEntity(entity, sectionY)) {
            LOGGER.warn("Could not remove entity " + entity + " from its old chunk section (" + sectionX + "," + sectionY + "," + sectionZ + ") since it was not contained in the section");
        }

        if (!slices.addEntity(entity, newSectionY)) {
            LOGGER.warn("Could not add entity " + entity + " to its new chunk section (" + newSectionX + "," + newSectionY + "," + newSectionZ + ") as it is already contained in the section");
        }

        ((ChunkSystemEntity)entity).moonrise$setSectionX(newSectionX);
        ((ChunkSystemEntity)entity).moonrise$setSectionY(newSectionY);
        ((ChunkSystemEntity)entity).moonrise$setSectionZ(newSectionZ);

        if (old.isEmpty()) {
            this.onEmptySlices(sectionX, sectionZ);
        }

        this.entitySectionChangeCallback(
                entity,
                sectionX, sectionY, sectionZ,
                newSectionX, newSectionY, newSectionZ
        );

        return slices;
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
                        if (chunk == null || !chunk.status.isOrAfter(FullChunkStatus.FULL)) {
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
                        if (chunk == null || !chunk.status.isOrAfter(FullChunkStatus.FULL)) {
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
                        if (chunk == null || !chunk.status.isOrAfter(FullChunkStatus.FULL)) {
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
                        if (chunk == null || !chunk.status.isOrAfter(FullChunkStatus.FULL)) {
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
                        if (chunk == null || !chunk.status.isOrAfter(FullChunkStatus.FULL)) {
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
                        if (chunk == null || !chunk.status.isOrAfter(FullChunkStatus.FULL)) {
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
                        if (chunk == null || !chunk.status.isOrAfter(FullChunkStatus.FULL)) {
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
                        if (chunk == null || !chunk.status.isOrAfter(FullChunkStatus.FULL)) {
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
                        if (chunk == null || !chunk.status.isOrAfter(FullChunkStatus.FULL)) {
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

    public void entitySectionLoad(final int chunkX, final int chunkZ, final ChunkEntitySlices slices) {
        this.checkThread(chunkX, chunkZ, "Cannot load in entity section off-main");
        synchronized (this) {
            final ChunkEntitySlices curr = this.getChunk(chunkX, chunkZ);
            if (curr != null) {
                this.removeChunk(chunkX, chunkZ);

                curr.mergeInto(slices);

                this.addChunk(chunkX, chunkZ, slices);
            } else {
                this.addChunk(chunkX, chunkZ, slices);
            }
        }
    }

    public void entitySectionUnload(final int chunkX, final int chunkZ) {
        this.checkThread(chunkX, chunkZ, "Cannot unload entity section off-main");
        this.removeChunk(chunkX, chunkZ);
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
        ChunkEntitySlices ret;
        if (region == null || (ret = region.get((chunkX & REGION_MASK) | ((chunkZ & REGION_MASK) << REGION_SHIFT))) == null) {
            return this.createEntityChunk(chunkX, chunkZ, true);
        }

        return ret;
    }

    public ChunkSlicesRegion getRegion(final int regionX, final int regionZ) {
        final long key = CoordinateUtils.getChunkKey(regionX, regionZ);

        return this.regions.get(key);
    }

    protected synchronized void removeChunk(final int chunkX, final int chunkZ) {
        final long key = CoordinateUtils.getChunkKey(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
        final int relIndex = (chunkX & REGION_MASK) | ((chunkZ & REGION_MASK) << REGION_SHIFT);

        final ChunkSlicesRegion region = this.regions.get(key);
        final int remaining = region.remove(relIndex);

        if (remaining == 0) {
            this.regions.remove(key);
        }
    }

    public synchronized void addChunk(final int chunkX, final int chunkZ, final ChunkEntitySlices slices) {
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

    public static final class ChunkSlicesRegion {

        private final ChunkEntitySlices[] slices = new ChunkEntitySlices[REGION_SIZE * REGION_SIZE];
        private int sliceCount;

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

    protected final class EntityCallback implements EntityInLevelCallback {

        public final Entity entity;

        public EntityCallback(final Entity entity) {
            this.entity = entity;
        }

        @Override
        public void onMove() {
            final Entity entity = this.entity;
            final Visibility oldVisibility = getEntityStatus(entity);
            final ChunkEntitySlices newSlices = EntityLookup.this.moveEntity(this.entity);
            if (newSlices == null) {
                // no new section, so didn't change sections
                return;
            }

            final Visibility newVisibility = getEntityStatus(entity);

            EntityLookup.this.entityStatusChange(entity, newSlices, oldVisibility, newVisibility, true, false, false);
        }

        @Override
        public void onRemove(final Entity.RemovalReason reason) {
            final Entity entity = this.entity;
            EntityLookup.this.checkThread(entity, "Cannot remove entity off-main"); // Paper - rewrite chunk system
            final Visibility tickingState = EntityLookup.getEntityStatus(entity);

            EntityLookup.this.removeEntity(entity);

            EntityLookup.this.entityStatusChange(entity, null, tickingState, Visibility.HIDDEN, false, false, reason.shouldDestroy());

            EntityLookup.this.removeEntityCallback(entity);

            this.entity.setLevelCallback(NoOpCallback.INSTANCE);
        }
    }

    protected static final class NoOpCallback implements EntityInLevelCallback {

        public static final NoOpCallback INSTANCE = new NoOpCallback();

        @Override
        public void onMove() {}

        @Override
        public void onRemove(final Entity.RemovalReason reason) {}
    }
}