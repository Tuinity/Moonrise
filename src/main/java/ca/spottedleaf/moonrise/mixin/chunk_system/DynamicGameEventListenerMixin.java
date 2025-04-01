package ca.spottedleaf.moonrise.mixin.chunk_system;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicGameEventListener.class)
abstract class DynamicGameEventListenerMixin {
    @Shadow
    @Nullable
    private SectionPos lastSection;

    @Inject(
        method = "remove",
        at = @At("RETURN")
    )
    private void onRemove(final CallbackInfo ci) {
        // We need to unset the last section when removed, otherwise if the same instance is re-added at the same position it
        // will assume there was no change and fail to re-register.
        // In vanilla, chunks rarely unload and re-load quickly enough to trigger this issue. However, our chunk system has a
        // quirk where fast chunk reload cycles will often occur on player login (see PR #22).
        // So we fix this vanilla oversight as our changes cause it to manifest in bugs much more often (see issue #87).
        this.lastSection = null;
    }
}
