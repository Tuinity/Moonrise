package ca.spottedleaf.moonrise.mixin.starlight.chunk;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkAccess.class)
interface ChunkAccessAccessorMixin {

    @Accessor("sections")
    LevelChunkSection[] starlight$getSections();
}
