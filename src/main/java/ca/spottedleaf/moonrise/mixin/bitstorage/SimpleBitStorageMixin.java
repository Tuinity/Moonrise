package ca.spottedleaf.moonrise.mixin.bitstorage;

import ca.spottedleaf.concurrentutil.util.IntegerUtil;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Mixin(SimpleBitStorage.class)
public abstract class SimpleBitStorageMixin implements BitStorage {

    @Shadow
    @Final
    private int bits;

    @Shadow
    @Final
    private long[] data;

    @Shadow
    @Final
    private int valuesPerLong;

    @Unique
    private static final VarHandle LONG_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);


    /*
     This is how the indices are supposed to be computed:
         final int dataIndex = index / this.valuesPerLong;
         final int localIndex = (index % this.valuesPerLong) * this.bitsPerValue;
     where valuesPerLong = 64 / this.bits
     The additional add that Mojang uses is only for unsigned division, when in reality the above is signed division.
     Thus, it is appropriate to use the signed division magic values which do not use an add.
     */



    @Unique
    private static final long[] BETTER_MAGIC = new long[33];
    static {
        for (int i = 1; i < BETTER_MAGIC.length; ++i) {
            BETTER_MAGIC[i] = IntegerUtil.getDivisorNumbers(64 / i);
        }
    }

    @Unique
    private long magic;

    /**
     * @reason Init magic field
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>(II[J)V",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(final CallbackInfo ci) {
        this.magic = BETTER_MAGIC[this.bits];
    }

    /**
     * @reason Do not validate input, and optimise method to use our magic value, which does not perform an add
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public int getAndSet(final int index, final int value) {
        // assume index/value in range
        // note: enforce atomic writes
        final long magic = this.magic;
        final int bits = this.bits;
        final long mul = magic >>> 32;
        final int dataIndex = (int)(((long)index * mul) >>> magic);

        final long[] dataArray = this.data;

        final long data = dataArray[dataIndex];
        final int valuesPerLong = this.valuesPerLong;
        final long mask = (1L << bits) - 1; // avoid extra memory read


        final int bitIndex = (index - (dataIndex * valuesPerLong)) * bits;
        final int prev = (int)(data >> bitIndex & mask);
        final long write = data & ~(mask << bitIndex) | ((long)value & mask) << bitIndex;

        LONG_ARRAY_HANDLE.setOpaque(dataArray, dataIndex, write);

        return prev;
    }

    /**
     * @reason Do not validate input, and optimise method to use our magic value, which does not perform an add
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public void set(final int index, final int value) {
        // assume index/value in range
        // note: enforce atomic writes

        final long magic = this.magic;
        final int bits = this.bits;
        final long mul = magic >>> 32;
        final int dataIndex = (int)(((long)index * mul) >>> magic);

        final long[] dataArray = this.data;

        final long data = dataArray[dataIndex];
        final int valuesPerLong = this.valuesPerLong;
        final long mask = (1L << bits) - 1; // avoid extra memory read

        final int bitIndex = (index - (dataIndex * valuesPerLong)) * bits;
        final long write = data & ~(mask << bitIndex) | ((long)value & mask) << bitIndex;

        LONG_ARRAY_HANDLE.setOpaque(dataArray, dataIndex, write);
    }

    /**
     * @reason Do not validate input, and optimise method to use our magic value, which does not perform an add
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public int get(final int index) {
        // assume index in range
        // note: enforce atomic reads
        final long magic = this.magic;
        final int bits = this.bits;
        final long mul = magic >>> 32;
        final int dataIndex = (int)(((long)index * mul) >>> magic);

        final long mask = (1L << bits) - 1; // avoid extra memory read
        final long data = (long)LONG_ARRAY_HANDLE.getOpaque(this.data, dataIndex);
        final int valuesPerLong = this.valuesPerLong;

        final int bitIndex = (index - (dataIndex * valuesPerLong)) * bits;

        return (int)(data >> bitIndex & mask);
    }
}
