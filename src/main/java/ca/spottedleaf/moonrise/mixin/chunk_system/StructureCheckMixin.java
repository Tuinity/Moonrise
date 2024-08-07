package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.map.SynchronisedLong2BooleanMap;
import ca.spottedleaf.moonrise.common.map.SynchronisedLong2ObjectMap;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(StructureCheck.class)
abstract class StructureCheckMixin {

    @Shadow
    private Long2ObjectMap<Object2IntMap<Structure>> loadedChunks;

    @Shadow
    private Map<Structure, Long2BooleanMap> featureChecks;

    @Shadow
    protected abstract boolean canCreateStructure(ChunkPos chunkPos, Structure structure);

    @Shadow
    private static Object2IntMap<Structure> deduplicateEmptyMap(Object2IntMap<Structure> object2IntMap) {
        return null;
    }


    // make sure to purge entries from the maps to prevent memory leaks
    @Unique
    private static final int CHUNK_TOTAL_LIMIT = 50 * (2 * 100 + 1) * (2 * 100 + 1); // cache 50 structure lookups
    @Unique
    private static final int PER_FEATURE_CHECK_LIMIT = 50 * (2 * 100 + 1) * (2 * 100 + 1); // cache 50 structure lookups
    @Unique
    private final SynchronisedLong2ObjectMap<Object2IntMap<Structure>> loadedChunksSafe = new SynchronisedLong2ObjectMap<>(CHUNK_TOTAL_LIMIT);
    @Unique
    private final ConcurrentHashMap<Structure, SynchronisedLong2BooleanMap> featureChecksSafe = new ConcurrentHashMap<>();

    /**
     * @reason Initialise fields and destroy old state
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initHook(ChunkScanAccess chunkScanAccess, RegistryAccess registryAccess,
                          StructureTemplateManager structureTemplateManager, ResourceKey resourceKey,
                          ChunkGenerator chunkGenerator, RandomState randomState, LevelHeightAccessor levelHeightAccessor,
                          BiomeSource biomeSource, long l, DataFixer dataFixer, CallbackInfo ci) {
        this.loadedChunks = null;
        this.featureChecks = null;
    }

    /**
     * @reason Redirect to new map
     * @author Spottedleaf
     */
    @Redirect(
            method = "checkStart",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;get(J)Ljava/lang/Object;",
                    remap = false
            )
    )
    private <V> V redirectCachedGet(final Long2ObjectMap<V> instance, final long pos) {
        return (V)this.loadedChunksSafe.get(pos);
    }

    /**
     * @reason Redirect to new map
     * @author Spottedleaf
     */
    @Inject(
            method = "checkStart",
            cancellable = true,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"
            )
    )
    private void redirectUncached(final ChunkPos pos, final Structure structure, final StructurePlacement structurePlacement,
                                  final boolean bl, final CallbackInfoReturnable<StructureCheckResult> cir) {
        final boolean ret = this.featureChecksSafe
                .computeIfAbsent(structure, structure2 -> new SynchronisedLong2BooleanMap(PER_FEATURE_CHECK_LIMIT))
                .getOrCompute(pos.toLong(), chunkPos -> this.canCreateStructure(pos, structure));
        cir.setReturnValue(!ret ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.CHUNK_LOAD_NEEDED);
    }

    /**
     * @reason Redirect to new map
     * @author Spottedleaf
     */
    @Overwrite
    public void storeFullResults(final long pos, final Object2IntMap<Structure> referencesByStructure) {
        this.loadedChunksSafe.put(pos, deduplicateEmptyMap(referencesByStructure));
        // once we insert into loadedChunks, we don't really need to be very careful about removing everything
        // from this map, as everything that checks this map uses loadedChunks first
        // so, one way or another it's a race condition that doesn't matter
        for (SynchronisedLong2BooleanMap value : this.featureChecksSafe.values()) {
            value.remove(pos);
        }
    }

    /**
     * @reason Redirect to new map
     * @author Spottedleaf
     */
    @Overwrite
    public void incrementReference(final ChunkPos pos, final Structure structure) {
        this.loadedChunksSafe.compute(pos.toLong(), (posx, referencesByStructure) -> { // Paper start - rewrite chunk system - synchronise this class
            // make this COW so that we do not mutate state that may be currently in use
            if (referencesByStructure == null) {
                referencesByStructure = new Object2IntOpenHashMap<>();
            } else {
                referencesByStructure = referencesByStructure instanceof Object2IntOpenHashMap<Structure> fastClone ? fastClone.clone() : new Object2IntOpenHashMap<>(referencesByStructure);
            }
            // Paper end - rewrite chunk system - synchronise this class

            referencesByStructure.computeInt(structure, (feature, references) -> references == null ? 1 : references + 1);
            return referencesByStructure;
        });
    }
}
