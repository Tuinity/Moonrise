package ca.spottedleaf.moonrise.mixin.chunk_system;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DedicatedServerProperties.class)
abstract class DedicatedServerPropertiesMixin {

    /**
     * @reason Default sync-chunk-writes to false, as the option is replaced by our config option.
     *         An existing value in server.properties is kept.
     * @author jpenilla
     */
    @WrapOperation(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/dedicated/DedicatedServerProperties;get(Ljava/lang/String;Z)Z"
        )
    )
    private boolean disableSyncChunkWritesByDefault(final DedicatedServerProperties instance, final String key,
                                                    final boolean defaultValue, final Operation<Boolean> original) {
        return original.call(instance, key, "sync-chunk-writes".equals(key) ? false : defaultValue);
    }
}
