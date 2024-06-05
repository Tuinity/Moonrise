package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.util.ChunkSystemSortedArraySet;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;

@Mixin(SortedArraySet.class)
public abstract class SortedArraySetMixin<T> extends AbstractSet<T> implements ChunkSystemSortedArraySet<T> {

    @Shadow
    int size;

    @Shadow
    T[] contents;

    @Shadow
    @Final
    private Comparator<T> comparator;

    @Shadow
    protected abstract int findIndex(T object);

    @Shadow
    private static int getInsertionPosition(int i) {
        return 0;
    }

    @Shadow
    protected abstract void addInternal(T object, int i);

    @Shadow
    abstract void removeInternal(int i);


    @Override
    public final boolean removeIf(final Predicate<? super T> filter) {
        // prev. impl used an iterator, which could be n^2 and creates garbage
        int i = 0;
        final int len = this.size;
        final T[] backingArray = this.contents;

        for (;;) {
            if (i >= len) {
                return false;
            }
            if (!filter.test(backingArray[i])) {
                ++i;
                continue;
            }
            break;
        }

        // we only want to write back to backingArray if we really need to

        int lastIndex = i; // this is where new elements are shifted to

        for (; i < len; ++i) {
            final T curr = backingArray[i];
            if (!filter.test(curr)) { // if test throws we're screwed
                backingArray[lastIndex++] = curr;
            }
        }

        // cleanup end
        Arrays.fill(backingArray, lastIndex, len, null);
        this.size = lastIndex;
        return true;
    }

    @Override
    public final T moonrise$replace(final T object) {
        final int index = this.findIndex(object);
        if (index >= 0) {
            final T old = this.contents[index];
            this.contents[index] = object;
            return old;
        } else {
            this.addInternal(object, getInsertionPosition(index));
            return object;
        }
    }

    @Override
    public final T moonrise$removeAndGet(final T object) {
        int i = this.findIndex(object);
        if (i >= 0) {
            final T ret = this.contents[i];
            this.removeInternal(i);
            return ret;
        } else {
            return null;
        }
    }

    @Override
    public final SortedArraySet<T> moonrise$copy() {
        final SortedArraySet<T> ret = SortedArraySet.create(this.comparator, 0);

        ((SortedArraySetMixin<T>)(Object)ret).size = this.size;
        ((SortedArraySetMixin<T>)(Object)ret).contents = Arrays.copyOf(this.contents, this.size);

        return ret;
    }

    @Override
    public Object[] moonrise$copyBackingArray() {
        return this.contents.clone();
    }
}
