package ca.spottedleaf.moonrise.mixin.profiler;

import ca.spottedleaf.moonrise.patches.profiler.client.ClientProfilerInstance;
import ca.spottedleaf.moonrise.patches.profiler.client.ProfilerMinecraft;
import com.mojang.blaze3d.platform.WindowEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin extends ReentrantBlockableEventLoop<Runnable> implements WindowEventHandler, ProfilerMinecraft {

    public MinecraftMixin(String string) {
        super(string);
    }

    @Unique
    private final ClientProfilerInstance leafProfiler = new ClientProfilerInstance();

    @Override
    public final ClientProfilerInstance moonrise$profilerInstance() {
        return this.leafProfiler;
    }

    /**
     * @reason Insert our own profiler for client
     * @author Spottedleaf
     */
    @Inject(
        method = "constructProfiler",
        cancellable = true,
        at = @At(
            value = "RETURN"
        )
    )
    public void addOurProfiler(final boolean shouldRenderFPSPie, final SingleTickProfiler singleTickProfiler,
                               final CallbackInfoReturnable<ProfilerFiller> cir) {
        final ProfilerFiller ret = cir.getReturnValue();

        if (!this.leafProfiler.isActive()) {
            return;
        }

        cir.setReturnValue(ret == null || ret == InactiveProfiler.INSTANCE ? this.leafProfiler : ProfilerFiller.tee(this.leafProfiler, ret));
    }

    /**
     * @reason Hook to track clientside ticking
     * @author Spottedleaf
     */
    @Inject(
        method = "tick",
        at = @At(
            value = "HEAD"
        )
    )
    private void clientStartHook(final CallbackInfo ci) {
        this.leafProfiler.startRealClientTick();
    }

    /**
     * @reason Hook to track clientside ticking
     * @author Spottedleaf
     */
    @Inject(
        method = "tick",
        at = @At(
            value = "RETURN"
        )
    )
    private void clientStopHook(final CallbackInfo ci) {
        this.leafProfiler.endRealClientTick();
    }
}
