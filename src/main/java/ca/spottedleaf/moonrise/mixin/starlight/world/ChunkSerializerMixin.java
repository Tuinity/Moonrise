package ca.spottedleaf.moonrise.mixin.starlight.world;

import ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
abstract class ChunkSerializerMixin {

    /**
     * Overwrites vanilla's light data with our own.
     * TODO this needs to be checked on update to account for format changes
     */
    @Inject(
            method = "write",
            at = @At("RETURN")
    )
    private static void saveLightHook(final ServerLevel world, final ChunkAccess chunk, final CallbackInfoReturnable<CompoundTag> cir) {
        SaveUtil.saveLightHook(world, chunk, cir.getReturnValue());
    }

    /**
     * Loads our light data into the returned chunk object from the tag.
     * TODO this needs to be checked on update to account for format changes
     */
    @Inject(
            method = "read",
            at = @At("RETURN")
    )
    private static void loadLightHook(final ServerLevel serverLevel, final PoiManager poiManager,
                                      final RegionStorageInfo regionStorageInfo, final ChunkPos chunkPos,
                                      final CompoundTag compoundTag, final CallbackInfoReturnable<ProtoChunk> cir) {
        SaveUtil.loadLightHook(serverLevel, chunkPos, compoundTag, cir.getReturnValue());
    }
}
