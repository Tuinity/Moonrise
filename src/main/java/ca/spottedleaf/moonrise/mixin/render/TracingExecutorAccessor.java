package ca.spottedleaf.moonrise.mixin.render;

import net.minecraft.TracingExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TracingExecutor.class)
interface TracingExecutorAccessor {

    @Invoker("wrapUnnamed")
    static Runnable moonrise$wrapUnnamed(final Runnable task) {
        throw new AbstractMethodError();
    }
}
