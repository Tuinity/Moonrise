package ca.spottedleaf.moonrise.mixin.mob_spawning;

import ca.spottedleaf.moonrise.patches.mob_spawning.MobSpawningEntityType;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {

    @Shadow
    static Biome getRoughBiome(final BlockPos arg, final ChunkAccess arg2) {
        return null;
    }

    /**
     * @reason Delay until we determine if the entity type even has a cost
     * @author Spottedleaf
     */
    @Redirect(
        method = {"method_27819", "lambda$createState$2"}, // Fabric, NeoForge
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner;getRoughBiome(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/world/level/biome/Biome;"
        )
    )
    private static Biome delayRoughBiome(final BlockPos pos, final ChunkAccess chunk) {
        return null;
    }

    /**
     * @reason Delay until we determine if the entity type even has a cost
     * @author Spottedleaf
     */
    @Redirect(
        method = {"method_27819", "lambda$createState$2"}, // Fabric, NeoForge
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getMobSettings()Lnet/minecraft/world/level/biome/MobSpawnSettings;"
        )
    )
    private static MobSpawnSettings delayMobSpawnSettings(final Biome biome) {
        return null;
    }

    /**
     * @reason Avoid looking up biomes for mobs which have no cost
     * @author Spottedleaf
     */
    @Redirect(
        method = {"method_27819", "lambda$createState$2"}, // Fabric, NeoForge
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/MobSpawnSettings;getMobSpawnCost(Lnet/minecraft/world/entity/EntityType;)Lnet/minecraft/world/level/biome/MobSpawnSettings$MobSpawnCost;"
        )
    )
    private static MobSpawnSettings.MobSpawnCost avoidBiomeLookupIfPossible(final MobSpawnSettings isNull,
                                                                            final EntityType<?> type,
                                                                            @Local(ordinal = 0, argsOnly = true) final BlockPos pos,
                                                                            @Local(ordinal = 0, argsOnly = true) final LevelChunk chunk) {
        if (!((MobSpawningEntityType)type).moonrise$hasAnyBiomeCost()) {
            // if the type has no associated cost with any biome, then no point in looking
            return null;
        }

        return getRoughBiome(pos, chunk).getMobSettings().getMobSpawnCost(type);
    }
}
