package ca.spottedleaf.moonrise.patches.chunk_system.level.entity;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.common.list.EntityList;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public final class ChunkEntitySlices {

    private static final Logger LOGGER = LogUtils.getLogger();

    public final int minSection;
    public final int maxSection;
    public final int chunkX;
    public final int chunkZ;
    public final Level world;

    private EntityCollectionBySection allEntities;
    private EntityCollectionBySection hardCollidingEntities;
    private Reference2ObjectOpenHashMap<Class<? extends Entity>, EntityCollectionBySection> entitiesByClass;
    private Reference2ObjectOpenHashMap<EntityType<?>, EntityCollectionBySection> entitiesByType;
    private EntityList entities;

    public FullChunkStatus status;
    public final ChunkData chunkData;

    private boolean isTransient;

    public boolean isTransient() {
        return this.isTransient;
    }

    public void setTransient(final boolean value) {
        this.isTransient = value;
    }

    public ChunkEntitySlices(final Level world, final int chunkX, final int chunkZ, final FullChunkStatus status,
                             final ChunkData chunkData, final int minSection, final int maxSection) { // inclusive, inclusive
        this.minSection = minSection;
        this.maxSection = maxSection;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.world = world;

        this.status = status;
        this.chunkData = chunkData;
    }

    public static List<Entity> readEntities(final ServerLevel world, final ChunkPos pos, final CompoundTag compoundTag) {
        try (final ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(pos), LOGGER)) {
            ValueInput valueinput = TagValueInput.create(scopedCollector, world.registryAccess(), compoundTag);
            // TODO check this and below on update for format changes
            return EntityType.loadEntitiesRecursive(valueinput.childrenListOrEmpty("Entities"), world, EntitySpawnReason.LOAD).collect(ImmutableList.toImmutableList());
        }
    }

    // Paper start - rewrite chunk system
    public static void copyEntities(final CompoundTag from, final CompoundTag into) {
        if (from == null) {
            return;
        }
        final ListTag entitiesFrom = from.getListOrEmpty("Entities");
        if (entitiesFrom == null || entitiesFrom.isEmpty()) {
            return;
        }

        final ListTag entitiesInto = into.getListOrEmpty("Entities");
        into.put("Entities", entitiesInto); // this is in case into doesn't have any entities
        entitiesInto.addAll(0, entitiesFrom);
    }

    public static CompoundTag saveEntityChunk(final List<Entity> entities, final ChunkPos chunkPos, final ServerLevel world) {
        return saveEntityChunk0(entities, chunkPos, world, false);
    }

    public static CompoundTag saveEntityChunk0(final List<Entity> entities, final ChunkPos chunkPos, final ServerLevel world, final boolean force) {
        if (!force && entities.isEmpty()) {
            return null;
        }

        final ListTag entitiesTag = new ListTag();
        try (final ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(chunkPos), LOGGER)) {
            for (final Entity entity : PlatformHooks.get().modifySavedEntities(world, chunkPos.x(), chunkPos.z(), entities)) {
                final TagValueOutput savedEntity = TagValueOutput.createWithContext(
                    scopedCollector.forChild(entity.problemPath()), entity.registryAccess()
                );

                try {
                    if (entity.save(savedEntity)) {
                        entitiesTag.add(savedEntity.buildResult());
                    }
                } catch (final Exception ex) {
                    LOGGER.error("Entity type " + entity.getType() + " failed to serialize", ex);
                }
            }
        }

        final CompoundTag ret = NbtUtils.addCurrentDataVersion(new CompoundTag());
        ret.put("Entities", entitiesTag);
        ret.store("Position", ChunkPos.CODEC, chunkPos);

        return !force && entitiesTag.isEmpty() ? null : ret;
    }

    public CompoundTag save() {
        final EntityList entities = this.entities;
        if (entities == null) {
            return null;
        }

        final int len = entities.size();
        if (len == 0) {
            return null;
        }

        final Entity[] rawData = entities.getRawData();
        final List<Entity> collectedEntities = new ArrayList<>(len);
        for (int i = 0; i < len; ++i) {
            final Entity entity = rawData[i];
            if (entity.shouldBeSaved()) {
                collectedEntities.add(entity);
            }
        }

        if (collectedEntities.isEmpty()) {
            return null;
        }

        return saveEntityChunk(collectedEntities, new ChunkPos(this.chunkX, this.chunkZ), (ServerLevel)this.world);
    }

    // returns true if this chunk has transient entities remaining
    public boolean unload() {
        final EntityList entities = this.entities;
        if (entities == null) {
            return false;
        }

        final int len = entities.size();
        final Entity[] collectedEntities = Arrays.copyOf(entities.getRawData(), len);

        for (int i = 0; i < len; ++i) {
            final Entity entity = collectedEntities[i];
            if (entity.isRemoved()) {
                // removed by us below
                continue;
            }
            if (entity.shouldBeSaved()) {
                PlatformHooks.get().unloadEntity(entity);
                if (entity.isVehicle()) {
                    // we cannot assume that these entities are contained within this chunk, because entities can
                    // desync - so we need to remove them all
                    for (final Entity passenger : entity.getIndirectPassengers()) {
                        PlatformHooks.get().unloadEntity(passenger);
                    }
                }
            }
        }

        return entities.size() != 0;
    }

    public List<Entity> getAllEntities() {
        final EntityList entities = this.entities;
        if (entities == null) {
            return new ArrayList<>();
        }

        final int len = entities.size();
        if (len == 0) {
            return new ArrayList<>();
        }

        final Entity[] rawData = entities.getRawData();
        final List<Entity> collectedEntities = new ArrayList<>(len);
        for (int i = 0; i < len; ++i) {
            collectedEntities.add(rawData[i]);
        }

        return collectedEntities;
    }

    public boolean isEmpty() {
        final EntityList entities = this.entities;
        return entities == null || entities.size() == 0;
    }

    public void mergeInto(final ChunkEntitySlices slices) {
        final EntityList entityList = this.entities;
        if (entityList == null) {
            return;
        }

        final Entity[] entities = entityList.getRawData();
        for (int i = 0, size = Math.min(entities.length, entityList.size()); i < size; ++i) {
            final Entity entity = entities[i];
            slices.addEntity(entity, ((ChunkSystemEntity)entity).moonrise$getSectionY());
        }
    }

    private boolean preventStatusUpdates;
    public boolean startPreventingStatusUpdates() {
        final boolean ret = this.preventStatusUpdates;
        this.preventStatusUpdates = true;
        return ret;
    }

    public boolean isPreventingStatusUpdates() {
        return this.preventStatusUpdates;
    }

    public void stopPreventingStatusUpdates(final boolean prev) {
        this.preventStatusUpdates = prev;
    }

    public void updateStatus(final FullChunkStatus status, final EntityLookup lookup) {
        this.status = status;

        final EntityList entityList = this.entities;
        if (entityList == null) {
            return;
        }

        final Entity[] entities = entityList.getRawData();

        for (int i = 0, size = entityList.size(); i < size; ++i) {
            final Entity entity = entities[i];

            final Visibility oldVisibility = EntityLookup.getEntityStatus(entity);
            ((ChunkSystemEntity)entity).moonrise$setChunkStatus(status);
            final Visibility newVisibility = EntityLookup.getEntityStatus(entity);

            lookup.entityStatusChange(entity, this, oldVisibility, newVisibility, false, false, false);
        }
    }

    public boolean addEntity(final Entity entity, final int chunkSection) {
        EntityList entities = this.entities;
        if (entities == null) {
            this.entities = entities = new EntityList();
        }
        if (!entities.add(entity)) {
            return false;
        }
        ((ChunkSystemEntity)entity).moonrise$setChunkStatus(this.status);
        ((ChunkSystemEntity)entity).moonrise$setChunkData(this.chunkData);
        final int sectionIndex = chunkSection - this.minSection;

        EntityCollectionBySection allEntities = this.allEntities;
        if (allEntities == null) {
            this.allEntities = allEntities = new EntityCollectionBySection(this);
        }
        allEntities.addEntity(entity, sectionIndex);

        if (((ChunkSystemEntity)entity).moonrise$isHardColliding()) {
            EntityCollectionBySection hardCollidingEntities = this.hardCollidingEntities;
            if (hardCollidingEntities == null) {
                this.hardCollidingEntities = hardCollidingEntities = new EntityCollectionBySection(this);
            }
            hardCollidingEntities.addEntity(entity, sectionIndex);
        }

        Reference2ObjectOpenHashMap<Class<? extends Entity>, EntityCollectionBySection> entitiesByClass = this.entitiesByClass;
        if (entitiesByClass == null) {
            this.entitiesByClass = entitiesByClass = new Reference2ObjectOpenHashMap<>();
        }
        for (final Iterator<Reference2ObjectMap.Entry<Class<? extends Entity>, EntityCollectionBySection>> iterator =
             entitiesByClass.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            final Reference2ObjectMap.Entry<Class<? extends Entity>, EntityCollectionBySection> entry = iterator.next();

            if (entry.getKey().isInstance(entity)) {
                entry.getValue().addEntity(entity, sectionIndex);
            }
        }

        Reference2ObjectOpenHashMap<EntityType<?>, EntityCollectionBySection> entitiesByType = this.entitiesByType;
        if (entitiesByType == null) {
            this.entitiesByType = entitiesByType = new Reference2ObjectOpenHashMap<>();
        }

        EntityCollectionBySection byType = entitiesByType.get(entity.getType());
        if (byType != null) {
            byType.addEntity(entity, sectionIndex);
        } else {
            entitiesByType.put(entity.getType(), byType = new EntityCollectionBySection(this));
            byType.addEntity(entity, sectionIndex);
        }

        return true;
    }

    public boolean removeEntity(final Entity entity, final int chunkSection) {
        final EntityList entities = this.entities;
        if (entities == null || !entities.remove(entity)) {
            return false;
        }
        ((ChunkSystemEntity)entity).moonrise$setChunkStatus(null);
        ((ChunkSystemEntity)entity).moonrise$setChunkData(null);
        final int sectionIndex = chunkSection - this.minSection;

        this.allEntities.removeEntity(entity, sectionIndex);

        if (((ChunkSystemEntity)entity).moonrise$isHardColliding()) {
            this.hardCollidingEntities.removeEntity(entity, sectionIndex);
        }

        final Reference2ObjectOpenHashMap<Class<? extends Entity>, EntityCollectionBySection> entitiesByClass = this.entitiesByClass;
        if (entitiesByClass != null) {
            for (final Iterator<Reference2ObjectMap.Entry<Class<? extends Entity>, EntityCollectionBySection>> iterator =
                 entitiesByClass.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                final Reference2ObjectMap.Entry<Class<? extends Entity>, EntityCollectionBySection> entry = iterator.next();

                if (entry.getKey().isInstance(entity)) {
                    entry.getValue().removeEntity(entity, sectionIndex);
                }
            }
        }

        final EntityCollectionBySection byType = this.entitiesByType.get(entity.getType());
        byType.removeEntity(entity, sectionIndex);

        return true;
    }

    public void getHardCollidingEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate) {
        final EntityCollectionBySection entities = this.hardCollidingEntities;
        if (entities != null) {
            entities.getEntities(except, box, into, predicate);
        }
    }

    public void getEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate) {
        final EntityCollectionBySection entities = this.allEntities;
        if (entities != null) {
            entities.getEntities(except, box, into, predicate);
        }
    }


    public boolean getEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate,
                               final int maxCount) {
        final EntityCollectionBySection entities = this.allEntities;
        return entities != null && entities.getEntitiesLimited(except, box, into, predicate, maxCount);
    }

    public <T extends Entity> void getEntities(final EntityType<?> type, final AABB box, final List<? super T> into,
                                               final Predicate<? super T> predicate) {
        final Reference2ObjectOpenHashMap<EntityType<?>, EntityCollectionBySection> entitiesByType = this.entitiesByType;
        final EntityCollectionBySection byType = entitiesByType == null ? null : entitiesByType.get(type);

        if (byType != null) {
            byType.getEntities((Entity)null, box, (List)into, (Predicate) predicate);
        }
    }

    public <T extends Entity> boolean getEntities(final EntityType<?> type, final AABB box, final List<? super T> into,
                                                  final Predicate<? super T> predicate, final int maxCount) {
        final Reference2ObjectOpenHashMap<EntityType<?>, EntityCollectionBySection> entitiesByType = this.entitiesByType;
        final EntityCollectionBySection byType = entitiesByType == null ? null : entitiesByType.get(type);

        if (byType != null) {
            return byType.getEntitiesLimited((Entity)null, box, (List)into, (Predicate)predicate, maxCount);
        }

        return false;
    }

    protected EntityCollectionBySection initClass(final Class<? extends Entity> clazz) {
        final EntityCollectionBySection ret = new EntityCollectionBySection(this);

        for (int sectionIndex = 0; sectionIndex < this.allEntities.entitiesBySection.length; ++sectionIndex) {
            final BasicEntityList<Entity> sectionEntities = this.allEntities.entitiesBySection[sectionIndex];
            if (sectionEntities == null) {
                continue;
            }

            final Entity[] storage = sectionEntities.storage;

            for (int i = 0, len = Math.min(storage.length, sectionEntities.size()); i < len; ++i) {
                final Entity entity = storage[i];

                if (clazz.isInstance(entity)) {
                    ret.addEntity(entity, sectionIndex);
                }
            }
        }

        return ret;
    }

    public <T extends Entity> void getEntities(final Class<? extends T> clazz, final Entity except, final AABB box, final List<? super T> into,
                                               final Predicate<? super T> predicate) {
        final Reference2ObjectOpenHashMap<Class<? extends Entity>, EntityCollectionBySection> entitiesByClass = this.entitiesByClass;
        if (entitiesByClass == null) {
            return;
        }

        EntityCollectionBySection collection = entitiesByClass.get(clazz);
        if (collection != null) {
            collection.getEntities(except, box, (List)into, (Predicate)predicate);
        } else {
            entitiesByClass.put(clazz, collection = this.initClass(clazz));
            collection.getEntities(except, box, (List)into, (Predicate)predicate);
        }
    }

    public <T extends Entity> boolean getEntities(final Class<? extends T> clazz, final Entity except, final AABB box, final List<? super T> into,
                                                  final Predicate<? super T> predicate, final int maxCount) {
        final Reference2ObjectOpenHashMap<Class<? extends Entity>, EntityCollectionBySection> entitiesByClass = this.entitiesByClass;
        if (entitiesByClass == null) {
            return false;
        }

        EntityCollectionBySection collection = entitiesByClass.get(clazz);
        if (collection != null) {
            return collection.getEntitiesLimited(except, box, (List)into, (Predicate)predicate, maxCount);
        } else {
            entitiesByClass.put(clazz, collection = this.initClass(clazz));
            return collection.getEntitiesLimited(except, box, (List)into, (Predicate)predicate, maxCount);
        }
    }

    private static final class BasicEntityList<E extends Entity> {

        private static final Entity[] EMPTY = new Entity[0];
        private static final int DEFAULT_CAPACITY = 4;

        private E[] storage;
        private int size;

        public BasicEntityList() {
            this(0);
        }

        public BasicEntityList(final int cap) {
            this.storage = (E[])(cap <= 0 ? EMPTY : new Entity[cap]);
        }

        public boolean isEmpty() {
            return this.size == 0;
        }

        public int size() {
            return this.size;
        }

        private void resize() {
            if (this.storage == EMPTY) {
                this.storage = (E[])new Entity[DEFAULT_CAPACITY];
            } else {
                this.storage = Arrays.copyOf(this.storage, this.storage.length * 2);
            }
        }

        public void add(final E entity) {
            final int idx = this.size++;
            if (idx >= this.storage.length) {
                this.resize();
                this.storage[idx] = entity;
            } else {
                this.storage[idx] = entity;
            }
        }

        public int indexOf(final E entity) {
            final E[] storage = this.storage;

            for (int i = 0, len = Math.min(this.storage.length, this.size); i < len; ++i) {
                if (storage[i] == entity) {
                    return i;
                }
            }

            return -1;
        }

        public boolean remove(final E entity) {
            final int idx = this.indexOf(entity);
            if (idx == -1) {
                return false;
            }

            final int size = --this.size;
            final E[] storage = this.storage;
            if (idx != size) {
                System.arraycopy(storage, idx + 1, storage, idx, size - idx);
            }

            storage[size] = null;

            return true;
        }

        public boolean has(final E entity) {
            return this.indexOf(entity) != -1;
        }
    }

    private static final class EntityCollectionBySection {

        private final ChunkEntitySlices slices;
        private final BasicEntityList<Entity>[] entitiesBySection;
        private int count;

        public EntityCollectionBySection(final ChunkEntitySlices slices) {
            this.slices = slices;

            final int sectionCount = slices.maxSection - slices.minSection + 1;

            this.entitiesBySection = new BasicEntityList[sectionCount];
        }

        public void addEntity(final Entity entity, final int sectionIndex) {
            BasicEntityList<Entity> list = this.entitiesBySection[sectionIndex];

            if (list != null && list.has(entity)) {
                return;
            }

            if (list == null) {
                this.entitiesBySection[sectionIndex] = list = new BasicEntityList<>();
            }

            list.add(entity);
            ++this.count;
        }

        public void removeEntity(final Entity entity, final int sectionIndex) {
            final BasicEntityList<Entity> list = this.entitiesBySection[sectionIndex];

            if (list == null || !list.remove(entity)) {
                return;
            }

            --this.count;

            if (list.isEmpty()) {
                this.entitiesBySection[sectionIndex] = null;
            }
        }

        public void getEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate) {
            if (this.count == 0) {
                return;
            }

            final int minSection = this.slices.minSection;
            final int maxSection = this.slices.maxSection;

            // See EntitySectionStorage#forEachAccessibleNonEmptySection
            final int min = Mth.clamp(Mth.floor(box.minY - 4.0) >> 4, minSection, maxSection);
            final int max = Mth.clamp(Mth.floor(box.maxY + 0.0) >> 4, minSection, maxSection);

            final BasicEntityList<Entity>[] entitiesBySection = this.entitiesBySection;

            for (int section = min; section <= max; ++section) {
                final BasicEntityList<Entity> list = entitiesBySection[section - minSection];

                if (list == null) {
                    continue;
                }

                final Entity[] storage = list.storage;

                for (int i = 0, len = Math.min(storage.length, list.size()); i < len; ++i) {
                    final Entity entity = storage[i];

                    if (entity == null || entity == except || !entity.getBoundingBox().intersects(box)) {
                        continue;
                    }

                    if (predicate != null && !predicate.test(entity)) {
                        continue;
                    }

                    into.add(entity);
                }
            }
        }

        public boolean getEntitiesLimited(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate,
                                          final int maxCount) {
            if (this.count == 0) {
                return false;
            }

            final int minSection = this.slices.minSection;
            final int maxSection = this.slices.maxSection;

            // See EntitySectionStorage#forEachAccessibleNonEmptySection
            final int min = Mth.clamp(Mth.floor(box.minY - 4.0) >> 4, minSection, maxSection);
            final int max = Mth.clamp(Mth.floor(box.maxY + 0.0) >> 4, minSection, maxSection);

            final BasicEntityList<Entity>[] entitiesBySection = this.entitiesBySection;

            for (int section = min; section <= max; ++section) {
                final BasicEntityList<Entity> list = entitiesBySection[section - minSection];

                if (list == null) {
                    continue;
                }

                final Entity[] storage = list.storage;

                for (int i = 0, len = Math.min(storage.length, list.size()); i < len; ++i) {
                    final Entity entity = storage[i];

                    if (entity == null || entity == except || !entity.getBoundingBox().intersects(box)) {
                        continue;
                    }

                    if (predicate != null && !predicate.test(entity)) {
                        continue;
                    }

                    into.add(entity);
                    if (into.size() >= maxCount) {
                        return true;
                    }
                }
            }

            return false;
        }
    }
}
