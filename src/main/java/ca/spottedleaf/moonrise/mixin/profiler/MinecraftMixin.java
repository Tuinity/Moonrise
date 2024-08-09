package ca.spottedleaf.moonrise.mixin.profiler;

import ca.spottedleaf.leafprofiler.client.ClientProfilerInstance;
import ca.spottedleaf.leafprofiler.client.MinecraftBridge;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin extends ReentrantBlockableEventLoop<Runnable> implements WindowEventHandler, MinecraftBridge {

    public MinecraftMixin(String string) {
        super(string);
    }

    @Unique
    private final ClientProfilerInstance leafProfiler = new ClientProfilerInstance();

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

        if (ret == null || ret == InactiveProfiler.INSTANCE) {
            this.leafProfiler.reset();
            return;
        }

        cir.setReturnValue(ProfilerFiller.tee(this.leafProfiler, ret));
    }

    @Override
    public ClientProfilerInstance moonrise$profilerInstance() {
        return this.leafProfiler;
    }
}
