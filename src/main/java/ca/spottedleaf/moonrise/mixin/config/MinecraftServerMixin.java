package ca.spottedleaf.moonrise.mixin.config;

import ca.spottedleaf.moonrise.common.util.ConfigHolder;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    /**
     * @reason Init for config
     * @author Spottedleaf
     */
    @Inject(
        method = "spin",
        at = @At(
            value = "HEAD"
        )
    )
    private static <S extends MinecraftServer> void initConfig(final CallbackInfoReturnable<S> cir) {
        ConfigHolder.getConfig(); // force class init
    }
}
