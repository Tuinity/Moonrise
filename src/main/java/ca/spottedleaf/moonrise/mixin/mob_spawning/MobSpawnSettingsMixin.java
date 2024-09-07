package ca.spottedleaf.moonrise.mixin.mob_spawning;

import ca.spottedleaf.moonrise.patches.mob_spawning.MobSpawningEntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(MobSpawnSettings.class)
abstract class MobSpawnSettingsMixin {

    @Shadow
    @Final
    private Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts;

    /**
     * @reason Set biome cost flag for any EntityType which has a cost
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void initBiomeCost(final CallbackInfo ci) {
        for (final EntityType<?> type : this.mobSpawnCosts.keySet()) {
            ((MobSpawningEntityType)type).moonrise$setHasBiomeCost();
        }
    }
}
