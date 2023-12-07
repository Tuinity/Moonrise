package ca.spottedleaf.moonrise.mixin.block_entity_remove;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelAccessor, AutoCloseable {

    @Shadow
    protected List<TickingBlockEntity> blockEntityTickers;

    @Shadow
    private List<TickingBlockEntity> pendingBlockEntityTickers;

    @Shadow
    public abstract TickRateManager tickRateManager();

    @Shadow
    public abstract boolean shouldTickBlocksAt(BlockPos blockPos);

    /**
     * @reason Make blockEntityTickers be of type {@link ObjectArrayList}, so that we can use {@link ObjectArrayList#elements()}
     * and {@link ObjectArrayList#size(int)} to manipulate the raw list for faster removals
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void newFieldType(final CallbackInfo ci) {
        // ObjectArrayList exposes raw elements + size
        this.blockEntityTickers = ObjectArrayList.wrap(new TickingBlockEntity[0]);
        this.pendingBlockEntityTickers = ObjectArrayList.wrap(new TickingBlockEntity[0]);
    }

    /**
     * @reason Run an in-place remove while ticking block entities, instead of using the iterator remove (which is O(n) _per_ remove)
     * This brings the remove calls to O(1) time
     * @author Spottedleaf
     */
    @Redirect(
            method = "tickBlockEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;hasNext()Z",
                    ordinal = 0
            )
    )
    private boolean newBlockEntityTick(final Iterator<TickingBlockEntity> ignored) {
        final boolean doTick = this.tickRateManager().runsNormally();
        final ObjectArrayList<TickingBlockEntity> tickList = (ObjectArrayList<TickingBlockEntity>)this.blockEntityTickers;
        // can cast the array, as we used wrap() with the array type
        final TickingBlockEntity[] elements = tickList.elements();

        boolean writeToBase = false;

        int base = 0;
        int i = 0; // current ticking entity (exclusive)
        int len = tickList.size();

        Objects.checkFromToIndex(0, len, elements.length);
        try {
            for (; i < len; ++i) {
                final TickingBlockEntity tileEntity = elements[i];
                if (tileEntity.isRemoved()) {
                    writeToBase = true;
                    continue;
                }

                if (doTick && this.shouldTickBlocksAt(tileEntity.getPos())) {
                    tileEntity.tick();
                }

                if (writeToBase) {
                    // avoid polluting cache if we are not removing
                    elements[base] = tileEntity;
                    ++base;
                } else {
                    ++base;
                }
            }
        } finally {
            // always keep the list in a non-broken state when handling (synchronous) exceptions
            if (i != base) {
                // writeToBase = true here
                // len - i != 0 only occurs when an exception is handled: remove elements between base and i
                System.arraycopy(elements, i, elements, base, len - i);

                // adjust size by removed elements
                tickList.size(len - (i - base));
            }
        }

        // force skip vanilla tick loop by always returning false
        return false;
    }
}
