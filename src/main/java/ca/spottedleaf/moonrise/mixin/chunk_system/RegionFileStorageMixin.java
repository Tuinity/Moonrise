package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.io.ChunkSystemRegionFileStorage;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemRegionFile;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.RegionFileRecovery;
import ca.spottedleaf.moonrise.patches.chunk_system.util.stream.ExternalChunkStreamMarker;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTParser;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.util.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(RegionFileStorage.class)
abstract class RegionFileStorageMixin implements ChunkSystemRegionFileStorage, AutoCloseable {

    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<RegionFile> regionCache;

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
    private static final Logger LOGGER = LogUtils.getLogger();

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
        return this.regionCache.getAndMoveToFirst(ChunkPos.pack(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfExists(final int chunkX, final int chunkZ) throws IOException {
        final long key = ChunkPos.pack(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);

        RegionFile ret = this.regionCache.getAndMoveToFirst(key);
        if (ret != null) {
            return ret;
        }

        if (!this.doesRegionFilePossiblyExist(key)) {
            return null;
        }

        final Path regionPath = this.folder.resolve(getRegionFileName(chunkX, chunkZ));

        if (!Files.exists(regionPath)) {
            this.markNonExisting(key);
            return null;
        }

        if (this.regionCache.size() >= MAX_CACHE_SIZE) {
            this.regionCache.removeLast().close();
        }

        this.createRegionFile(key);

        FileUtil.createDirectoriesSafe(this.folder);

        ret = new RegionFile(this.info, regionPath, this.folder, this.sync);

        this.regionCache.putAndMoveToFirst(key, ret);

        return ret;
    }

    /**
     * @reason Make this method thread-safe, and add in support for storing when regionfiles do not exist
     * @author Spottedleaf
     */
    @Overwrite
    public final RegionFile getRegionFile(final ChunkPos chunkPos) throws IOException {
        synchronized (this) {
            final long key = ChunkPos.pack(chunkPos.x() >> REGION_SHIFT, chunkPos.z() >> REGION_SHIFT);

            RegionFile ret = this.regionCache.getAndMoveToFirst(key);
            if (ret != null) {
                return ret;
            }

            if (this.regionCache.size() >= MAX_CACHE_SIZE) {
                this.regionCache.removeLast().close();
            }

            final Path regionPath = this.folder.resolve(getRegionFileName(chunkPos.x(), chunkPos.z()));

            this.createRegionFile(key);

            FileUtil.createDirectoriesSafe(this.folder);

            ret = new RegionFile(this.info, regionPath, this.folder, this.sync);

            this.regionCache.putAndMoveToFirst(key, ret);

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
        final RegionFile regionFile = this.getRegionFile(pos);

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

        writeData.write().run(this.getRegionFile(pos));
    }

    @Override
    public final MoonriseRegionFileIO.RegionDataController.ReadData moonrise$readData(
            final int chunkX, final int chunkZ
    ) throws IOException {
        final RegionFile regionFile = this.moonrise$getRegionFileIfExists(chunkX, chunkZ);

        // retrieve the count before reading, so that any recalculation during or after the read is detected in finishRead
        final int recalculateCount;
        final DataInputStream input;
        if (regionFile == null) {
            recalculateCount = 0;
            input = null;
        } else {
            synchronized (regionFile) {
                input = regionFile.getChunkDataInputStream(new ChunkPos(chunkX, chunkZ));
                recalculateCount = ((ChunkSystemRegionFile)regionFile).moonrise$getRecalculateCount();
            }
        }

        if (input == null) {
            return new MoonriseRegionFileIO.RegionDataController.ReadData(
                    MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.NO_DATA, null, null, recalculateCount
            );
        }

        final MoonriseRegionFileIO.RegionDataController.ReadData ret = new MoonriseRegionFileIO.RegionDataController.ReadData(
                MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.HAS_DATA, input, null, recalculateCount
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
                MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.SYNC_READ, null, syncRead, recalculateCount
        );
    }

    // if the return value is null, then the caller needs to re-try with a new call to readData()
    @Override
    public final CompoundTag moonrise$finishRead(
            final int chunkX, final int chunkZ, final MoonriseRegionFileIO.RegionDataController.ReadData readData
    ) throws IOException {
        final CompoundTag ret;
        try {
            ret = FastNBTParser.nbtIoRead(readData.input());
        } finally {
            readData.input().close();
        }

        if (!RegionFileRecovery.isChunkStorage(this.info)) {
            return ret;
        }

        final RegionFile regionFile = this.moonrise$getRegionFileIfExists(chunkX, chunkZ);
        if (regionFile == null || ((ChunkSystemRegionFile)regionFile).moonrise$getRecalculateCount() != readData.recalculateCount()) {
            // header was recalculated (or the regionfile was removed), so the data may be stale
            return null;
        }

        final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        final ChunkPos storedPos = RegionFileRecovery.getChunkCoordinate(ret);
        if (storedPos.equals(pos)) {
            return ret;
        }

        LOGGER.error("Attempting to read chunk data at {} but got chunk data for {} instead! Attempting regionfile recalculation for regionfile {}", pos, storedPos, regionFile.getPath().toAbsolutePath());
        final Boolean recalc = ((ChunkSystemRegionFile)regionFile).moonrise$tryRecalcHeader(readData.recalculateCount());
        if (recalc == null || recalc.booleanValue()) {
            // some other thread re-calculated or we re-calculated
            return null;
        }

        LOGGER.error("Can't recalculate regionfile header for regionfile {}", regionFile.getPath().toAbsolutePath());
        return ret;
    }

    /**
     * @reason Attempt to recalculate the regionfile header if the read chunk data does not belong to the requested position
     * @author Spottedleaf
     */
    @Inject(
            method = "read",
            cancellable = true,
            at = @At(
                    value = "RETURN"
            )
    )
    private void checkReadPosition(final ChunkPos pos, final CallbackInfoReturnable<CompoundTag> cir) throws IOException {
        final CompoundTag read = cir.getReturnValue();
        if (read == null || !RegionFileRecovery.isChunkStorage(this.info)) {
            return;
        }

        final ChunkPos storedPos = RegionFileRecovery.getChunkCoordinate(read);
        if (storedPos.equals(pos)) {
            return;
        }

        final RegionFile regionFile = this.moonrise$getRegionFileIfExists(pos.x(), pos.z());
        if (regionFile == null) {
            return;
        }

        LOGGER.error("Attempting to read chunk data at {} but got chunk data for {} instead! Attempting regionfile recalculation for regionfile {}", pos, storedPos, regionFile.getPath().toAbsolutePath());
        if (!((ChunkSystemRegionFile)regionFile).moonrise$recalculateHeader()) {
            LOGGER.error("Can't recalculate regionfile header for regionfile {}", regionFile.getPath().toAbsolutePath());
            return;
        }

        // re-read directly, as the recalculated header only points to data at the correct position
        try (final DataInputStream input = regionFile.getChunkDataInputStream(pos)) {
            cir.setReturnValue(input == null ? null : FastNBTParser.nbtIoRead(input));
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
            for (final RegionFile regionFile : this.regionCache.values()) {
                try {
                    regionFile.close();
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
            for (final RegionFile regionFile : this.regionCache.values()) {
                try {
                    regionFile.flush();
                } catch (final IOException ex) {
                    exceptionCollector.add(ex);
                }
            }

            exceptionCollector.throwIfPresent();
        }
    }

    /**
     * @reason Avoid creating RegionFiles on read when they do not exist
     * @author Spottedleaf
     */
    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/RegionFileStorage;getRegionFile(Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/storage/RegionFile;"
            )
    )
    private RegionFile avoidCreatingReadRegionFile(final RegionFileStorage instance, final ChunkPos chunkPos) throws IOException {
        return ((RegionFileStorageMixin)(Object)instance).moonrise$getRegionFileIfExists(chunkPos.x(), chunkPos.z());
    }

    /**
     * @reason Avoid creating RegionFiles on read when they do not exist, this hook is required to exit early when
     *         the RegionFile does not exist.
     * @author Spottedleaf
     */
    @Inject(
            method = "read",
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILHARD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/RegionFile;getChunkDataInputStream(Lnet/minecraft/world/level/ChunkPos;)Ljava/io/DataInputStream;"
            )
    )
    private void avoidCreatingReadRegionFileExit(final ChunkPos chunkPos, final CallbackInfoReturnable<CompoundTag> cir,
                                                 final RegionFile regionFile) {
        if (regionFile == null) {
            cir.setReturnValue(null);
            return;
        }
    }

    /**
     * @reason Avoid creating RegionFiles on scan when they do not exist
     * @author Spottedleaf
     */
    @Redirect(
            method = "scanChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/RegionFileStorage;getRegionFile(Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/storage/RegionFile;"
            )
    )
    private RegionFile avoidCreatingScanRegionFile(final RegionFileStorage instance, final ChunkPos chunkPos) throws IOException {
        return ((RegionFileStorageMixin)(Object)instance).moonrise$getRegionFileIfExists(chunkPos.x(), chunkPos.z());
    }

    /**
     * @reason Avoid creating RegionFiles on scan when they do not exist, this hook is required to exit early when
     *         the RegionFile does not exist.
     * @author Spottedleaf
     */
    @Inject(
            method = "scanChunk",
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILHARD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/RegionFile;getChunkDataInputStream(Lnet/minecraft/world/level/ChunkPos;)Ljava/io/DataInputStream;"
            )
    )
    private void avoidCreatingScanRegionFileExit(final ChunkPos chunkPos, final StreamTagVisitor streamTagVisitor,
                                                 final CallbackInfo ci, final RegionFile regionFile) {
        if (regionFile == null) {
            ci.cancel();
            return;
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
