package ca.spottedleaf.moonrise.mixin.util_time_source;

import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Util.class)
public abstract class UtilMixin {

    /**
     * @reason GLFW clock will use the same one as nanoTime, except that it is JNI
     * and cannot be inlined and incurs the JNI method invoke cost. This resolves
     * the client profiler causing more lag than it should if there are many things
     * being profiled such as many entities
     * @author Spottedleaf
     */
    @Overwrite
    public static long getNanos() {
        return System.nanoTime();
    }
}
