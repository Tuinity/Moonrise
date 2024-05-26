package ca.spottedleaf.moonrise.patches.chunk_system.async_save;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record AsyncChunkSaveData(
        Tag blockTickList, // non-null if we had to go to the server's tick list
        Tag fluidTickList, // non-null if we had to go to the server's tick list
        ListTag blockEntities,
        long worldTime
) {}
