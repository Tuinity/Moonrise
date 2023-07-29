package ca.spottedleaf.moonrise.mixin.datawatcher;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataMixin {
    @Shadow
    @Final
    private Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById;

    @Shadow
    private boolean isDirty;

    @Shadow
    @Final
    private Entity entity;

    @Shadow
    protected abstract <T> void assignValue(SynchedEntityData.DataItem<T> dataItem, SynchedEntityData.DataValue<?> dataValue);




    @Unique
    private static final SynchedEntityData.DataItem<?>[] EMPTY = new SynchedEntityData.DataItem<?>[0];

    @Unique
    private SynchedEntityData.DataItem<?>[] itemsByArray = EMPTY;

    /**
     * @reason Remove unnecessary locking, and use the array lookup
     * @author Spottedleaf
     */
    @Overwrite
    private <T> void createDataItem(final EntityDataAccessor<T> entityDataAccessor, final T dfl) {
        final int id = entityDataAccessor.getId();
        final SynchedEntityData.DataItem<T> dataItem = new SynchedEntityData.DataItem<>(entityDataAccessor, dfl);

        this.itemsById.put(id, dataItem);
        if (id >= this.itemsByArray.length) {
            this.itemsByArray = Arrays.copyOf(this.itemsByArray, Math.max(4, id << 1));
        }

        this.itemsByArray[id] = dataItem;
    }

    /**
     * @reason Use array lookup
     * @author Spottedleaf
     */
    @Overwrite
    public <T> boolean hasItem(final EntityDataAccessor<T> entityDataAccessor) {
        final int id = entityDataAccessor.getId();

        return id >= 0 && id < this.itemsByArray.length && this.itemsByArray[id] != null;
    }

    /**
     * @reason Remove unnecessary locking, and use the array lookup
     * @author Spottedleaf
     */
    @Overwrite
    private <T> SynchedEntityData.DataItem<T> getItem(final EntityDataAccessor<T> entityDataAccessor) {
        final int id = entityDataAccessor.getId();

        if (id < 0 || id >= this.itemsByArray.length) {
            return null;
        }

        return (SynchedEntityData.DataItem<T>)this.itemsByArray[id];
    }

    /**
     * @reason Remove unnecessary locking, and use the array lookup
     * @author Spottedleaf
     */
    @Overwrite
    public List<SynchedEntityData.DataValue<?>> packDirty() {
        if (!this.isDirty) {
            return null;
        }
        this.isDirty = false;

        final List<SynchedEntityData.DataValue<?>> ret = new ArrayList<>();

        for (final SynchedEntityData.DataItem<?> dataItem : this.itemsByArray) {
            if (dataItem == null || !dataItem.isDirty()) {
                continue;
            }
            dataItem.setDirty(false);
            ret.add(dataItem.value());
        }

        return ret;
    }

    /**
     * @reason Remove unnecessary locking, and use the array lookup
     * @author Spottedleaf
     */
    @Overwrite
    public List<SynchedEntityData.DataValue<?>> getNonDefaultValues() {
        List<SynchedEntityData.DataValue<?>> ret = null;
        for (final SynchedEntityData.DataItem<?> dataItem : this.itemsByArray) {
            if (dataItem == null || dataItem.isSetToDefault()) {
                continue;
            }

            if (ret == null) {
                ret = new ArrayList<>();
                ret.add(dataItem.value());
                continue;
            } else {
                ret.add(dataItem.value());
                continue;
            }
        }

        return ret;
    }

    /**
     * @reason Remove unnecessary locking, and use the array lookup
     * @author Spottedleaf
     */
    @Overwrite
    public void assignValues(final List<SynchedEntityData.DataValue<?>> list) {
        final SynchedEntityData.DataItem<?>[] items = this.itemsByArray;
        final int itemsLen = items.length;
        for (int i = 0, len = list.size(); i < len; ++i) {
            final SynchedEntityData.DataValue<?> value = list.get(i);
            final int valueId = value.id();

            final SynchedEntityData.DataItem<?> dataItem;
            if (valueId < 0 || valueId >= itemsLen || (dataItem = items[valueId]) == null) {
                continue;
            }

            final EntityDataAccessor<?> accessor = dataItem.getAccessor();
            this.assignValue(dataItem, value);
            this.entity.onSyncedDataUpdated(accessor);
        }

        this.entity.onSyncedDataUpdated(list);
    }
}
