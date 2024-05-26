package ca.spottedleaf.moonrise.patches.chunk_system.level.storage;

import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ChunkSystemSectionStorage {

    public CompoundTag moonrise$read(final int chunkX, final int chunkZ) throws IOException;

    public void moonrise$write(final int chunkX, final int chunkZ, final CompoundTag data) throws IOException;

    public RegionFileStorage moonrise$getRegionStorage();

    public void moonrise$close() throws IOException;

}
