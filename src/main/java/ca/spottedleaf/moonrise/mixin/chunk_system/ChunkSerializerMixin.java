package ca.spottedleaf.moonrise.mixin.chunk_system;

import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {

    /**
     * @reason Chunk system handles this during full transition
     * @author Spottedleaf
     * @see ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkFullTask
     */
    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;checkConsistencyWithBlocks(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;)V"
            )
    )
    private static void skipConsistencyCheck(PoiManager instance, SectionPos sectionPos, LevelChunkSection levelChunkSection) {}

}
