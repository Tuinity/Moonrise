package ca.spottedleaf.moonrise.mixin.bitstorage;

import net.minecraft.util.BitStorage;
import net.minecraft.util.ZeroBitStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ZeroBitStorage.class)
abstract class ZeroBitStorageMixin implements BitStorage {

    /**
     * @reason Do not validate input
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public int getAndSet(final int index, final int value) {
        return 0;
    }

    /**
     * @reason Do not validate input
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public void set(final int index, final int value) {

    }

    /**
     * @reason Do not validate input
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public int get(final int index) {
        return 0;
    }
}
