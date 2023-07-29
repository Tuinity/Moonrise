package ca.spottedleaf.moonrise.patches.collisions.slices;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.collisions.entity.CollisionEntity;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public final class ChunkEntitySlices {

    protected final int minSection;
    protected final int maxSection;
    public final int chunkX;
    public final int chunkZ;
    protected final Level world;

    protected final EntityCollectionBySection allEntities;
    protected final EntityCollectionBySection hardCollidingEntities;
    protected final Reference2ObjectOpenHashMap<EntityType<?>, EntityCollectionBySection> entitiesByType;
    protected final Reference2ObjectOpenHashMap<Class<? extends Entity>, EntityCollectionBySection> entitiesByClass;
    protected final ReferenceList<Entity> entities = new ReferenceList<>();

    public Visibility sectionVisibility = Visibility.TRACKED; // TODO

    public ChunkEntitySlices(final Level world, final int chunkX, final int chunkZ) { // inclusive, inclusive
        this.minSection = WorldUtil.getMinSection(world);
        this.maxSection = WorldUtil.getMaxSection(world);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.world = world;

        this.allEntities = new EntityCollectionBySection(this);
        this.hardCollidingEntities = new EntityCollectionBySection(this);
        this.entitiesByType = new Reference2ObjectOpenHashMap<>();
        this.entitiesByClass = new Reference2ObjectOpenHashMap<>();
    }

    public boolean isEmpty() {
        return this.entities.size() == 0;
    }

    public boolean addEntity(final Entity entity, final int chunkSection) {
        if (!this.entities.add(entity)) {
            return false;
        }
        final int sectionIndex = chunkSection - this.minSection;

        this.allEntities.addEntity(entity, sectionIndex);

        if (((CollisionEntity)entity).isHardColliding()) {
            this.hardCollidingEntities.addEntity(entity, sectionIndex);
        }

        for (final Iterator<Reference2ObjectMap.Entry<Class<? extends Entity>, EntityCollectionBySection>> iterator =
             this.entitiesByClass.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            final Reference2ObjectMap.Entry<Class<? extends Entity>, EntityCollectionBySection> entry = iterator.next();

            if (entry.getKey().isInstance(entity)) {
                entry.getValue().addEntity(entity, sectionIndex);
            }
        }

        EntityCollectionBySection byType = this.entitiesByType.get(entity.getType());
        if (byType != null) {
            byType.addEntity(entity, sectionIndex);
        } else {
            this.entitiesByType.put(entity.getType(), byType = new EntityCollectionBySection(this));
            byType.addEntity(entity, sectionIndex);
        }

        return true;
    }

    public boolean removeEntity(final Entity entity, final int chunkSection) {
        if (!this.entities.remove(entity)) {
            return false;
        }
        final int sectionIndex = chunkSection - this.minSection;

        this.allEntities.removeEntity(entity, sectionIndex);

        if (((CollisionEntity)entity).isHardColliding()) {
            this.hardCollidingEntities.removeEntity(entity, sectionIndex);
        }

        for (final Iterator<Reference2ObjectMap.Entry<Class<? extends Entity>, EntityCollectionBySection>> iterator =
             this.entitiesByClass.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            final Reference2ObjectMap.Entry<Class<? extends Entity>, EntityCollectionBySection> entry = iterator.next();

            if (entry.getKey().isInstance(entity)) {
                entry.getValue().removeEntity(entity, sectionIndex);
            }
        }

        final EntityCollectionBySection byType = this.entitiesByType.get(entity.getType());
        byType.removeEntity(entity, sectionIndex);

        return true;
    }

    public void getHardCollidingEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate) {
        this.hardCollidingEntities.getEntities(except, box, into, predicate);
    }

    public void getEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate) {
        this.allEntities.getEntitiesWithEnderDragonParts(except, box, into, predicate);
    }

    public void getEntitiesWithoutDragonParts(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate) {
        this.allEntities.getEntities(except, box, into, predicate);
    }


    public boolean getEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate,
                            final int maxCount) {
        return this.allEntities.getEntitiesWithEnderDragonPartsLimited(except, box, into, predicate, maxCount);
    }

    public boolean getEntitiesWithoutDragonParts(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate,
                                                 final int maxCount) {
        return this.allEntities.getEntitiesLimited(except, box, into, predicate, maxCount);
    }

    public <T extends Entity> void getEntities(final EntityType<?> type, final AABB box, final List<? super T> into,
                                               final Predicate<? super T> predicate) {
        final EntityCollectionBySection byType = this.entitiesByType.get(type);

        if (byType != null) {
            byType.getEntities((Entity)null, box, (List)into, (Predicate) predicate);
        }
    }

    public <T extends Entity> boolean getEntities(final EntityType<?> type, final AABB box, final List<? super T> into,
                                               final Predicate<? super T> predicate, final int maxCount) {
        final EntityCollectionBySection byType = this.entitiesByType.get(type);

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
        EntityCollectionBySection collection = this.entitiesByClass.get(clazz);
        if (collection != null) {
            collection.getEntitiesWithEnderDragonParts(except, clazz, box, (List)into, (Predicate)predicate);
        } else {
            this.entitiesByClass.put(clazz, collection = this.initClass(clazz));
            collection.getEntitiesWithEnderDragonParts(except, clazz, box, (List)into, (Predicate)predicate);
        }
    }

    public <T extends Entity> boolean getEntities(final Class<? extends T> clazz, final Entity except, final AABB box, final List<? super T> into,
                                               final Predicate<? super T> predicate, final int maxCount) {
        EntityCollectionBySection collection = this.entitiesByClass.get(clazz);
        if (collection != null) {
            return collection.getEntitiesWithEnderDragonPartsLimited(except, clazz, box, (List)into, (Predicate)predicate, maxCount);
        } else {
            this.entitiesByClass.put(clazz, collection = this.initClass(clazz));
            return collection.getEntitiesWithEnderDragonPartsLimited(except, clazz, box, (List)into, (Predicate)predicate, maxCount);
        }
    }

    protected static final class BasicEntityList<E extends Entity> {

        protected static final Entity[] EMPTY = new Entity[0];
        protected static final int DEFAULT_CAPACITY = 4;

        protected E[] storage;
        protected int size;

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

    protected static final class EntityCollectionBySection {

        protected final ChunkEntitySlices manager;
        protected final long[] nonEmptyBitset;
        protected final BasicEntityList<Entity>[] entitiesBySection;
        protected int count;

        public EntityCollectionBySection(final ChunkEntitySlices manager) {
            this.manager = manager;

            final int sectionCount = manager.maxSection - manager.minSection + 1;

            this.nonEmptyBitset = new long[(sectionCount + (Long.SIZE - 1)) >>> 6]; // (sectionCount + (Long.SIZE - 1)) / Long.SIZE
            this.entitiesBySection = new BasicEntityList[sectionCount];
        }

        public void addEntity(final Entity entity, final int sectionIndex) {
            BasicEntityList<Entity> list = this.entitiesBySection[sectionIndex];

            if (list != null && list.has(entity)) {
                return;
            }

            if (list == null) {
                this.entitiesBySection[sectionIndex] = list = new BasicEntityList<>();
                this.nonEmptyBitset[sectionIndex >>> 6] |= (1L << (sectionIndex & (Long.SIZE - 1)));
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
                this.nonEmptyBitset[sectionIndex >>> 6] ^= (1L << (sectionIndex & (Long.SIZE - 1)));
            }
        }

        public void getEntities(final Entity except, final AABB box, final List<Entity> into, final Predicate<? super Entity> predicate) {
            if (this.count == 0) {
                return;
            }

            final int minSection = this.manager.minSection;
            final int maxSection = this.manager.maxSection;

            final int min = Mth.clamp(Mth.floor(box.minY - 2.0) >> 4, minSection, maxSection);
            final int max = Mth.clamp(Mth.floor(box.maxY + 2.0) >> 4, minSection, maxSection);

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

            final int minSection = this.manager.minSection;
            final int maxSection = this.manager.maxSection;

            final int min = Mth.clamp(Mth.floor(box.minY - 2.0) >> 4, minSection, maxSection);
            final int max = Mth.clamp(Mth.floor(box.maxY + 2.0) >> 4, minSection, maxSection);

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

        public void getEntitiesWithEnderDragonParts(final Entity except, final AABB box, final List<Entity> into,
                                                    final Predicate<? super Entity> predicate) {
            if (this.count == 0) {
                return;
            }

            final int minSection = this.manager.minSection;
            final int maxSection = this.manager.maxSection;

            final int min = Mth.clamp(Mth.floor(box.minY - 2.0) >> 4, minSection, maxSection);
            final int max = Mth.clamp(Mth.floor(box.maxY + 2.0) >> 4, minSection, maxSection);

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

                    if (predicate == null || predicate.test(entity)) {
                        into.add(entity);
                    } // else: continue to test the ender dragon parts

                    if (entity instanceof EnderDragon) {
                        for (final EnderDragonPart part : ((EnderDragon)entity).getSubEntities()) {
                            if (part == except || !part.getBoundingBox().intersects(box)) {
                                continue;
                            }

                            if (predicate != null && !predicate.test(part)) {
                                continue;
                            }

                            into.add(part);
                        }
                    }
                }
            }
        }

        public boolean getEntitiesWithEnderDragonPartsLimited(final Entity except, final AABB box, final List<Entity> into,
                                                              final Predicate<? super Entity> predicate, final int maxCount) {
            if (this.count == 0) {
                return false;
            }

            final int minSection = this.manager.minSection;
            final int maxSection = this.manager.maxSection;

            final int min = Mth.clamp(Mth.floor(box.minY - 2.0) >> 4, minSection, maxSection);
            final int max = Mth.clamp(Mth.floor(box.maxY + 2.0) >> 4, minSection, maxSection);

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

                    if (predicate == null || predicate.test(entity)) {
                        into.add(entity);
                        if (into.size() >= maxCount) {
                            return true;
                        }
                    } // else: continue to test the ender dragon parts

                    if (entity instanceof EnderDragon) {
                        for (final EnderDragonPart part : ((EnderDragon)entity).getSubEntities()) {
                            if (part == except || !part.getBoundingBox().intersects(box)) {
                                continue;
                            }

                            if (predicate != null && !predicate.test(part)) {
                                continue;
                            }

                            into.add(part);
                            if (into.size() >= maxCount) {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }

        public void getEntitiesWithEnderDragonParts(final Entity except, final Class<?> clazz, final AABB box, final List<Entity> into,
                                                    final Predicate<? super Entity> predicate) {
            if (this.count == 0) {
                return;
            }

            final int minSection = this.manager.minSection;
            final int maxSection = this.manager.maxSection;

            final int min = Mth.clamp(Mth.floor(box.minY - 2.0) >> 4, minSection, maxSection);
            final int max = Mth.clamp(Mth.floor(box.maxY + 2.0) >> 4, minSection, maxSection);

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

                    if (predicate == null || predicate.test(entity)) {
                        into.add(entity);
                    } // else: continue to test the ender dragon parts

                    if (entity instanceof EnderDragon) {
                        for (final EnderDragonPart part : ((EnderDragon)entity).getSubEntities()) {
                            if (part == except || !part.getBoundingBox().intersects(box) || !clazz.isInstance(part)) {
                                continue;
                            }

                            if (predicate != null && !predicate.test(part)) {
                                continue;
                            }

                            into.add(part);
                        }
                    }
                }
            }
        }

        public boolean getEntitiesWithEnderDragonPartsLimited(final Entity except, final Class<?> clazz, final AABB box, final List<Entity> into,
                                                              final Predicate<? super Entity> predicate, final int maxCount) {
            if (this.count == 0) {
                return false;
            }

            final int minSection = this.manager.minSection;
            final int maxSection = this.manager.maxSection;

            final int min = Mth.clamp(Mth.floor(box.minY - 2.0) >> 4, minSection, maxSection);
            final int max = Mth.clamp(Mth.floor(box.maxY + 2.0) >> 4, minSection, maxSection);

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

                    if (predicate == null || predicate.test(entity)) {
                        into.add(entity);
                        if (into.size() >= maxCount) {
                            return true;
                        }
                    } // else: continue to test the ender dragon parts

                    if (entity instanceof EnderDragon) {
                        for (final EnderDragonPart part : ((EnderDragon)entity).getSubEntities()) {
                            if (part == except || !part.getBoundingBox().intersects(box) || !clazz.isInstance(part)) {
                                continue;
                            }

                            if (predicate != null && !predicate.test(part)) {
                                continue;
                            }

                            into.add(part);
                            if (into.size() >= maxCount) {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }
    }
}
