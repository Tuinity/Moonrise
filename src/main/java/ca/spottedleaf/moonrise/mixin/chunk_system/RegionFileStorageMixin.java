package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.io.ChunkSystemRegionFileStorage;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemRegionFile;
import ca.spottedleaf.moonrise.patches.chunk_system.util.stream.ExternalChunkStreamMarker;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.util.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Mixin(RegionFileStorage.class)
abstract class RegionFileStorageMixin implements ChunkSystemRegionFileStorage, AutoCloseable {

    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<Optional<RegionFile>> regionCache;

    @Shadow
    @Final
    private static int MAX_CACHE_SIZE;

    @Shadow
    @Final
    private Path folder;

    @Shadow
    @Final
    private boolean sync;

    @Shadow
    @Final
    private RegionStorageInfo info;


    @Unique
    private static final int REGION_SHIFT = 5;

    @Unique
    private static final int MAX_NON_EXISTING_CACHE = 1024 * 4;

    @Unique
    private final LongLinkedOpenHashSet nonExistingRegionFiles = new LongLinkedOpenHashSet();

    @Unique
    private static String getRegionFileName(final int chunkX, final int chunkZ) {
        return "r." + (chunkX >> REGION_SHIFT) + "." + (chunkZ >> REGION_SHIFT) + ".mca";
    }

    @Unique
    private boolean doesRegionFilePossiblyExist(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            if (this.nonExistingRegionFiles.contains(position)) {
                this.nonExistingRegionFiles.addAndMoveToFirst(position);
                return false;
            }
            return true;
        }
    }

    @Unique
    private void createRegionFile(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            this.nonExistingRegionFiles.remove(position);
        }
    }

    @Unique
    private void markNonExisting(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            if (this.nonExistingRegionFiles.addAndMoveToFirst(position)) {
                while (this.nonExistingRegionFiles.size() >= MAX_NON_EXISTING_CACHE) {
                    this.nonExistingRegionFiles.removeLastLong();
                }
            }
        }
    }

    @Override
    public final boolean moonrise$doesRegionFileNotExistNoIO(final int chunkX, final int chunkZ) {
        return !this.doesRegionFilePossiblyExist(ChunkPos.pack(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfLoaded(final int chunkX, final int chunkZ) {
        final Optional<RegionFile> ret = this.regionCache.getAndMoveToFirst(ChunkPos.pack(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
        return ret == null || ret.isEmpty() ? null : ret.get();
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfExists(final int chunkX, final int chunkZ) throws IOException {
        return this.getRegionFile(new ChunkPos(chunkX, chunkZ), false);
    }

    /**
     * @reason Make this method thread-safe, and add in support for storing when regionfiles do not exist
     * @author Spottedleaf
     */
    @Overwrite
    public RegionFile getRegionFile(final ChunkPos chunkPos, final boolean create) throws IOException {
        synchronized (this) {
            final long key = ChunkPos.pack(chunkPos.x() >> REGION_SHIFT, chunkPos.z() >> REGION_SHIFT);

            final Optional<RegionFile> cached = this.regionCache.getAndMoveToFirst(key);
            if (cached != null) {
                if (cached.isPresent()) {
                    return cached.get();
                }

                if (!create) {
                    return null;
                }
            }

            if (!create && !this.doesRegionFilePossiblyExist(key)) {
                return null;
            }

            final Path regionPath = this.folder.resolve(getRegionFileName(chunkPos.x(), chunkPos.z()));

            if (!create && !Files.isRegularFile(regionPath)) {
                // unlike vanilla, do not cache Optional.empty() here: missing regions are tracked in
                // nonExistingRegionFiles so that a read miss cannot evict a live RegionFile from the cache
                this.markNonExisting(key);
                return null;
            }

            if (this.regionCache.size() >= MAX_CACHE_SIZE) {
                final Optional<RegionFile> evicted = this.regionCache.removeLast();
                if (evicted.isPresent()) {
                    evicted.get().close();
                }
            }

            this.createRegionFile(key);

            FileUtil.createDirectoriesSafe(this.folder);

            final RegionFile ret = new RegionFile(this.info, regionPath, this.folder, this.sync);

            this.regionCache.putAndMoveToFirst(key, Optional.of(ret));

            return ret;
        }
    }

    @Override
    public final MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(
            final int chunkX, final int chunkZ, final CompoundTag compound
    ) throws IOException {
        if (compound == null) {
            return new MoonriseRegionFileIO.RegionDataController.WriteData(
                    compound, MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.DELETE,
                    null, null
            );
        }

        final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        final RegionFile regionFile = this.getRegionFile(pos, true);

        // note: not required to keep regionfile loaded after this call, as the write param takes a regionfile as input
        // (and, the regionfile parameter is unused for writing until the write call)
        final MoonriseRegionFileIO.RegionDataController.WriteData writeData = ((ChunkSystemRegionFile)regionFile).moonrise$startWrite(compound, pos);

        try {
            NbtIo.write(compound, writeData.output());
        } finally {
            writeData.output().close();
        }

        return writeData;
    }

    @Override
    public final void moonrise$finishWrite(
            final int chunkX, final int chunkZ, final MoonriseRegionFileIO.RegionDataController.WriteData writeData
    ) throws IOException {
        final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (writeData.result() == MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.DELETE) {
            final RegionFile regionFile = this.moonrise$getRegionFileIfExists(chunkX, chunkZ);
            if (regionFile != null) {
                regionFile.clear(pos);
            } // else: didn't exist

            return;
        }

        writeData.write().run(this.getRegionFile(pos, true));
    }

    @Override
    public final MoonriseRegionFileIO.RegionDataController.ReadData moonrise$readData(
            final int chunkX, final int chunkZ
    ) throws IOException {
        final RegionFile regionFile = this.moonrise$getRegionFileIfExists(chunkX, chunkZ);

        final DataInputStream input = regionFile == null ? null : regionFile.getChunkDataInputStream(new ChunkPos(chunkX, chunkZ));

        if (input == null) {
            return new MoonriseRegionFileIO.RegionDataController.ReadData(
                    MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.NO_DATA, null, null
            );
        }

        final MoonriseRegionFileIO.RegionDataController.ReadData ret = new MoonriseRegionFileIO.RegionDataController.ReadData(
                MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.HAS_DATA, input, null
        );

        if (!(input instanceof ExternalChunkStreamMarker)) {
            // internal stream, which is fully read
            return ret;
        }

        final CompoundTag syncRead = this.moonrise$finishRead(chunkX, chunkZ, ret);

        if (syncRead == null) {
            // need to try again
            return this.moonrise$readData(chunkX, chunkZ);
        }

        return new MoonriseRegionFileIO.RegionDataController.ReadData(
                MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.SYNC_READ, null, syncRead
        );
    }

    // if the return value is null, then the caller needs to re-try with a new call to readData()
    @Override
    public final CompoundTag moonrise$finishRead(
            final int chunkX, final int chunkZ, final MoonriseRegionFileIO.RegionDataController.ReadData readData
    ) throws IOException {
        try {
            return NbtIo.read(readData.input());
        } finally {
            readData.input().close();
        }
    }

    /**
     * @reason Make this method thread-safe
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public void close() throws IOException {
        synchronized (this) {
            final ExceptionCollector<IOException> exceptionCollector = new ExceptionCollector<>();
            for (final Optional<RegionFile> entry : this.regionCache.values()) {
                if (entry.isEmpty()) {
                    continue;
                }
                try {
                    entry.get().close();
                } catch (final IOException ex) {
                    exceptionCollector.add(ex);
                }
            }

            exceptionCollector.throwIfPresent();
        }
    }

    /**
     * @reason Make this method thread-safe
     * @author Spottedleaf
     */
    @Overwrite
    public void flush() throws IOException {
        synchronized (this) {
            final ExceptionCollector<IOException> exceptionCollector = new ExceptionCollector<>();
            for (final Optional<RegionFile> entry : this.regionCache.values()) {
                if (entry.isEmpty()) {
                    continue;
                }
                try {
                    entry.get().flush();
                } catch (final IOException ex) {
                    exceptionCollector.add(ex);
                }
            }

            exceptionCollector.throwIfPresent();
        }
    }

    /**
     * @reason Avoid creating RegionFiles on write when the input value is null (indicating a delete operation)
     * @author Spottedleaf
     */
    @Inject(
            method = "write",
            cancellable = true,
            at = @At(
                    value = "HEAD"
            )
    )
    private void avoidCreatingWriteRegionFile(final ChunkPos chunkPos, final CompoundTag compoundTag, final CallbackInfo ci) throws IOException {
        if (compoundTag == null && this.moonrise$getRegionFileIfExists(chunkPos.x(), chunkPos.z()) == null) {
            ci.cancel();
            return;
        }
        // double reading the RegionFile is fine, as the result is cached
    }
}
