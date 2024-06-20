package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.ticks.ChunkSystemLevelChunkTicks;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.SerializableTickContainer;
import net.minecraft.world.ticks.TickContainerAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;

@Mixin(LevelChunkTicks.class)
public abstract class LevelChunkTicksMixin<T> implements ChunkSystemLevelChunkTicks, SerializableTickContainer<T>, TickContainerAccess<T> {

    @Shadow
    @Final
    private Queue<ScheduledTick<T>> tickQueue;

    @Shadow
    private List<SavedTick<T>> pendingTicks;

    /*
     * Since ticks are saved using relative delays, we need to consider the entire tick list dirty when there are scheduled ticks
     * and the last saved tick is not equal to the current tick
     */
    /*
     * In general, it would be nice to be able to "re-pack" ticks once the chunk becomes non-ticking again, but that is a
     * bit out of scope for the chunk system
     */


    @Unique
    private boolean dirty;

    @Unique
    private long lastSaved = Long.MIN_VALUE;

    @Override
    public final boolean moonrise$isDirty(final long tick) {
        return this.dirty || (!this.tickQueue.isEmpty() && tick != this.lastSaved);
    }

    @Override
    public final void moonrise$clearDirty() {
        this.dirty = false;
    }

    /**
     * @reason Set dirty when a scheduled tick is removed
     * @author Spottedleaf
     */
    @Inject(
            method = "poll",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Set;remove(Ljava/lang/Object;)Z"
            )
    )
    private void pollHook(final CallbackInfoReturnable<ScheduledTick<T>> cir) {
        this.dirty = true;
    }

    /**
     * @reason Set dirty when a tick is scheduled
     * @author Spottedleaf
     */
    @Inject(
            method = "schedule",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/ticks/LevelChunkTicks;scheduleUnchecked(Lnet/minecraft/world/ticks/ScheduledTick;)V"
            )
    )
    private void scheduleHook(final CallbackInfo ci) {
        this.dirty = true;
    }

    /**
     * @reason Set dirty when a tick is removed
     * @author Spottedleaf
     */
    @Inject(
            method = "removeIf",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;remove()V"
            )
    )
    private void removeHook(final CallbackInfo ci) {
        this.dirty = true;
    }

    /**
     * @reason Update last save tick
     * @author Spottedleaf
     */
    @Inject(
            method = "save(JLjava/util/function/Function;)Lnet/minecraft/nbt/ListTag;",
            at = @At(
                    value = "HEAD"
            )
    )
    private void saveHook(final long time, final Function<T, String> idFunction, final CallbackInfoReturnable<ListTag> cir) {
        this.lastSaved = time;
    }

    /**
     * @reason Update last save to current tick when first unpacking the chunk data
     * @author Spottedleaf
     */
    @Inject(
            method = "unpack",
            at = @At(
                    value = "HEAD"
            )
    )
    private void unpackHook(final long tick, final CallbackInfo ci) {
        if (this.pendingTicks == null) {
            return;
        }

        this.lastSaved = tick;
    }
}
