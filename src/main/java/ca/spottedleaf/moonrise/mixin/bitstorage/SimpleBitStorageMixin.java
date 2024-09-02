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

@Mixin(SimpleBitStorage.class)
abstract class SimpleBitStorageMixin implements BitStorage {

    @Shadow
    @Final
    private int bits;

    @Shadow
    @Final
    private long[] data;

    @Shadow
    @Final
    private long mask;

    @Shadow
    @Final
    private int size;

    /*
     Credit to https://lemire.me/blog/2019/02/08/faster-remainders-when-the-divisor-is-a-constant-beating-compilers-and-libdivide
     and https://github.com/Vrganj for the algorithm to determine a magic value to use for both division and mod operations

     */

    @Unique
    private static final int[] BETTER_MAGIC = new int[33];
    static {
        // 20 bits of precision
        // since index is always [0, 4095] (i.e 12 bits), multiplication by a magic value here (20 bits)
        // fits exactly in an int and allows us to use integer arithmetic
        for (int bits = 1; bits < BETTER_MAGIC.length; ++bits) {
            BETTER_MAGIC[bits] = (int)IntegerUtil.getUnsignedDivisorMagic(64L / bits, 20);
        }
    }

    @Unique
    private int magic;

    @Unique
    private int mulBits;

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
        this.mulBits = (64 / this.bits) * this.bits;
        if (this.size > 4096) {
            throw new IllegalStateException("Size > 4096 not supported");
        }
    }

    /**
     * @reason Do not validate input, and optimise method to use our magic value, which does not perform an add
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public int getAndSet(final int index, final int value) {
        // assume index/value in range
        final int full = this.magic * index; // 20 bits of magic + 12 bits of index = barely int
        final int divQ = full >>> 20;
        final int divR = (full & 0xFFFFF) * this.mulBits >>> 20;

        final long[] dataArray = this.data;

        final long data = dataArray[divQ];
        final long mask = this.mask;

        final long write = data & ~(mask << divR) | ((long)value & mask) << divR;

        dataArray[divQ] = write;

        return (int)(data >>> divR & mask);
    }

    /**
     * @reason Do not validate input, and optimise method to use our magic value, which does not perform an add
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public void set(final int index, final int value) {
        // assume index/value in range
        final int full = this.magic * index; // 20 bits of magic + 12 bits of index = barely int
        final int divQ = full >>> 20;
        final int divR = (full & 0xFFFFF) * this.mulBits >>> 20;

        final long[] dataArray = this.data;

        final long data = dataArray[divQ];
        final long mask = this.mask;

        final long write = data & ~(mask << divR) | ((long)value & mask) << divR;

        dataArray[divQ] = write;
    }

    /**
     * @reason Do not validate input, and optimise method to use our magic value, which does not perform an add
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public int get(final int index) {
        // assume index in range
        final int full = this.magic * index; // 20 bits of magic + 12 bits of index = barely int
        final int divQ = full >>> 20;
        final int divR = (full & 0xFFFFF) * this.mulBits >>> 20;

        return (int)(this.data[divQ] >>> divR & this.mask);
    }
}
