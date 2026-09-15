package ca.spottedleaf.moonrise.mixin.mob_spawning;

import ca.spottedleaf.moonrise.patches.mob_spawning.MobSpawningEntityType;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {

    /**
     * @reason Avoid looking up biomes for mobs which have no cost
     * @author Spottedleaf
     */
    @Redirect(
        method = {"lambda$createState$0"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/EnvironmentAttributeSystem;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/core/BlockPos;)Ljava/lang/Object;"
        )
    )
    private static Object avoidBiomeLookupIfPossible(final EnvironmentAttributeSystem attributes,
                                                      final EnvironmentAttribute<MobSpawnSettings> attribute,
                                                      final BlockPos pos,
                                                      @Local(ordinal = 0, argsOnly = true) final Entity entity) {
        if (!((MobSpawningEntityType)entity.getType()).moonrise$hasAnyBiomeCost()) {
            // if the type has no associated cost with any biome, then no point in looking
            return MobSpawnSettings.EMPTY;
        }

        return attributes.getValue(attribute, pos);
    }
}
