package ca.spottedleaf.moonrise.mixin.profiler;

import ca.spottedleaf.leafprofiler.client.ClientProfilerInstance;
import com.mojang.blaze3d.platform.WindowEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ContinuousProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin extends ReentrantBlockableEventLoop<Runnable> implements WindowEventHandler {

    @Shadow
    @Final
    private ContinuousProfiler fpsPieProfiler;

    public MinecraftMixin(String string) {
        super(string);
    }

    @Unique
    private final ClientProfilerInstance leafProfiler = new ClientProfilerInstance();

    /**
     * @reason Use our own profiler for client
     * @author Spottedleaf
     */
    @Overwrite
    private ProfilerFiller constructProfiler(final boolean shouldRenderFPSPie, final SingleTickProfiler singleTickProfiler) {
        if (shouldRenderFPSPie) {
            this.fpsPieProfiler.enable();
        } else {
            this.fpsPieProfiler.disable();
        }

        return this.leafProfiler;
    }
}
