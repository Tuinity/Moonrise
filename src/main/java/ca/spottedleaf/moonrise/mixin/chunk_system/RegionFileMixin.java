package ca.spottedleaf.moonrise.mixin.chunk_system;

import net.minecraft.world.level.chunk.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RegionFile.class)
public abstract class RegionFileMixin {

    // TODO can't really add synchronized to methods, can we?

}
