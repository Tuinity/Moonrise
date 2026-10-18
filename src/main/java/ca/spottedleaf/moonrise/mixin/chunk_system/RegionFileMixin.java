package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkBuffer;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemRegionFile;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.RegionFileRecovery;
import ca.spottedleaf.moonrise.patches.chunk_system.util.stream.ExternalChunkStreamMarker;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTParser;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionBitmap;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
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
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(RegionFile.class)
abstract class RegionFileMixin implements ChunkSystemRegionFile {

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    private static int EXTERNAL_STREAM_FLAG;

    @Shadow
    @Final
    RegionFileVersion version;

    @Shadow
    @Final
    private RegionStorageInfo info;

    @Shadow
    @Final
    private Path path;

    @Shadow
    @Final
    private FileChannel file;

    @Shadow
    @Final
    private Path externalFileDir;

    @Shadow
    @Final
    private IntBuffer offsets;

    @Shadow
    @Final
    private IntBuffer timestamps;

    @Shadow
    @Final
    protected RegionBitmap usedSectors;

    @Shadow
    protected abstract int packSectorOffset(final int index, final int size);

    @Shadow
    private static int getNumSectors(final int offset) {
        throw new UnsupportedOperationException();
    }

    @Shadow
    private static int getSectorNumber(final int offset) {
        throw new UnsupportedOperationException();
    }

    @Shadow
    private static int getTimestamp() {
        throw new UnsupportedOperationException();
    }

    @Shadow
    private static boolean isExternalStreamChunk(final byte version) {
        throw new UnsupportedOperationException();
    }

    @Shadow
    private static byte getExternalChunkVersion(final byte version) {
        throw new UnsupportedOperationException();
    }

    @Shadow
    private static ByteArrayInputStream createStream(final ByteBuffer buffer, final int length) {
        throw new UnsupportedOperationException();
    }

    @Shadow
    protected abstract int getOffset(final ChunkPos pos);

    @Shadow
    protected abstract DataInputStream createChunkInputStream(final ChunkPos pos, final byte versionId, final InputStream chunkStream) throws IOException;

    @Shadow
    protected abstract DataInputStream createExternalChunkInputStream(final ChunkPos pos, final byte versionId) throws IOException;

    @Shadow
    protected abstract void writeHeader() throws IOException;

    @Shadow
    protected abstract Path getExternalChunkPath(final ChunkPos pos);


    // the header can only store sector counts up to this value, larger chunks must be stored externally
    @Unique
    private static final int MAX_INTERNAL_SECTORS = 255;

    @Unique
    private static final CompoundTag EXTERNAL_STUB_COMPOUND = new CompoundTag();

    @Unique
    private final AtomicInteger recalculateCount = new AtomicInteger();

    /**
     * @reason Vanilla does not synchronize header modifications in clear, which may race with header recalculation
     * @author jpenilla
     */
    @WrapMethod(
            method = "clear"
    )
    private void synchronizeClear(final ChunkPos pos, final Operation<Void> original) {
        synchronized (this) {
            original.call(pos);
        }
    }

    /**
     * @reason Prevent closing the file during header recalculation
     * @author jpenilla
     */
    @WrapMethod(
            method = "close"
    )
    private void synchronizeClose(final Operation<Void> original) {
        synchronized (this) {
            original.call();
        }
    }

    /**
     * @reason Avoid reading the header during header recalculation
     * @author jpenilla
     */
    @WrapMethod(
            method = "doesChunkExist"
    )
    private boolean synchronizeDoesChunkExist(final ChunkPos pos, final Operation<Boolean> original) {
        synchronized (this) {
            return original.call(pos);
        }
    }

    /**
     * @reason Avoid reading the header during header recalculation
     * @author jpenilla
     */
    @WrapMethod(
            method = "hasChunk"
    )
    private boolean synchronizeHasChunk(final ChunkPos pos, final Operation<Boolean> original) {
        synchronized (this) {
            return original.call(pos);
        }
    }

    /**
     * @reason Vanilla computes the sector position as an int, which overflows for regionfiles larger than 2GiB
     * @author jpenilla
     */
    @Redirect(
            method = "doesChunkExist",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/nio/channels/FileChannel;read(Ljava/nio/ByteBuffer;J)I"
            )
    )
    private int fixDoesChunkExistReadOverflow(final FileChannel instance, final ByteBuffer dst, final long position,
                                              final ChunkPos pos) throws IOException {
        return instance.read(dst, getSectorNumber(this.getOffset(pos)) * 4096L);
    }


    @Override
    public final MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(final CompoundTag data, final ChunkPos pos) throws IOException {
        final RegionFile.ChunkBuffer buffer = ((RegionFile)(Object)this).new ChunkBuffer(pos);
        ((ChunkSystemChunkBuffer)buffer).moonrise$setWriteOnClose(false);

        final DataOutputStream out = new DataOutputStream(this.version.wrap(buffer));

        return new MoonriseRegionFileIO.RegionDataController.WriteData(
                data, MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.WRITE,
                out, ((ChunkSystemChunkBuffer)buffer)::moonrise$write
        );
    }

    /**
     * @reason Wrap external streams so that callers on read methods can determine whether the data is stored externally or not
     * @author Spottedleaf
     */
    @Inject(
            method = "createExternalChunkInputStream",
            cancellable = true,
            at = @At(
                    value = "RETURN"
            )
    )
    private void markExternalChunkStreams(final CallbackInfoReturnable<DataInputStream> cir) {
        final DataInputStream is = cir.getReturnValue();
        if (is == null) {
            return;
        }
        cir.setReturnValue(new ExternalChunkStreamMarker(is));
    }

    @Unique
    private static long roundToSectors(final long bytes) {
        return (bytes + 4095L) >>> 12;
    }

    @Unique
    private boolean canRecalcHeader() {
        // only chunk data has an embedded position and timestamp we can use to locate data
        return RegionFileRecovery.isChunkStorage(this.info) && RegionFileRecovery.getRegionFileCoordinates(this.path) != null;
    }

    @Unique
    private int readFully(final ByteBuffer buffer, long position) throws IOException {
        int total = 0;
        while (buffer.hasRemaining()) {
            final int read = this.file.read(buffer, position);
            if (read < 0) {
                break;
            }
            total += read;
            position += read;
        }
        return total;
    }

    @Unique
    private int readLength(final long sector) throws IOException {
        final ByteBuffer length = ByteBuffer.allocate(4);
        if (4 != this.readFully(length, sector * 4096L)) {
            return -1;
        }

        return length.getInt(0);
    }

    // returns null if the data is not valid, or EXTERNAL_STUB_COMPOUND if the data is a stub for an external chunk
    @Unique
    private CompoundTag attemptRead(final long sector, final int chunkDataLength, final long fileLength) {
        try {
            if (chunkDataLength <= 0) {
                return null;
            }

            final long offset = sector * 4096L + 4L; // skip length field

            if ((offset + chunkDataLength) > fileLength) {
                return null;
            }

            final ByteBuffer chunkData = ByteBuffer.allocate(chunkDataLength);
            if (chunkDataLength != this.readFully(chunkData, offset)) {
                return null;
            }

            chunkData.flip();

            final byte compressionType = chunkData.get();
            if (isExternalStreamChunk(compressionType)) {
                return EXTERNAL_STUB_COMPOUND;
            }

            final RegionFileVersion compression = RegionFileVersion.fromId(compressionType);
            if (compression == null) {
                return null;
            }

            try (final DataInputStream input = new DataInputStream(compression.wrap(createStream(chunkData, chunkData.remaining())))) {
                return FastNBTParser.nbtIoRead(input);
            }
        } catch (final Exception ex) {
            return null;
        }
    }

    // moves internal chunk data which is too large to be referenced by the header to its external file,
    // returns the compression type of the data or null if the move failed
    @Unique
    private RegionFileVersion moveToExternalFile(final ChunkPos pos, final int sectorOffset, final int rawLength) {
        final Path externalFile = this.getExternalChunkPath(pos);
        try {
            // skip length field
            final ByteBuffer chunkData = ByteBuffer.allocate(rawLength - 4);
            if (chunkData.remaining() != this.readFully(chunkData, sectorOffset * 4096L + 4L)) {
                throw new IOException("Unexpected end of file");
            }
            chunkData.flip();

            final RegionFileVersion compression = RegionFileVersion.fromId(chunkData.get());

            final Path tmp = Files.createTempFile(this.externalFileDir, "tmp", null);
            try {
                try (final FileChannel out = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                    while (chunkData.hasRemaining()) {
                        out.write(chunkData);
                    }
                    out.force(true);
                }
                Files.move(tmp, externalFile, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }

            return compression;
        } catch (final IOException ex) {
            LOGGER.error("Failed to move oversized chunk data for local chunk ({},{}) in regionfile {} to external file {}", pos.x() & 31, pos.z() & 31, this.path.toAbsolutePath(), externalFile.toAbsolutePath(), ex);
            return null;
        }
    }

    @Unique
    private synchronized void backupRegionFile() {
        final Path backup = this.path.resolveSibling(this.path.getFileName() + "." + Long.toUnsignedString(ThreadLocalRandom.current().nextLong()) + ".backup");
        try {
            this.file.force(true);
            LOGGER.warn("Backing up regionfile \"{}\" to {}", this.path.toAbsolutePath(), backup.toAbsolutePath());
            Files.copy(this.path, backup, StandardCopyOption.COPY_ATTRIBUTES);
            LOGGER.warn("Backed up the regionfile to {}", backup.toAbsolutePath());
        } catch (final IOException ex) {
            LOGGER.error("Failed to backup to {}", backup.toAbsolutePath(), ex);
        }
    }

    @Unique
    private void setUsedSectors(final BitSet allocated) {
        this.usedSectors.free(0, Integer.MAX_VALUE);
        for (int start = allocated.nextSetBit(0); start >= 0;) {
            final int end = allocated.nextClearBit(start);
            this.usedSectors.force(start, end - start);
            start = allocated.nextSetBit(end);
        }
    }

    @Override
    public final int moonrise$getRecalculateCount() {
        return this.recalculateCount.get();
    }

    @Override
    public final Boolean moonrise$tryRecalcHeader(final int expectedRecalcCount) throws IOException {
        if (!this.canRecalcHeader()) {
            return Boolean.FALSE;
        }

        synchronized (this) {
            if (this.recalculateCount.get() != expectedRecalcCount) {
                return null;
            }

            return Boolean.valueOf(this.moonrise$recalculateHeader());
        }
    }

    /*
     * Instead of trying to relocate the chunk, which seems to never
     * be the correct choice, so we end up duplicating or swapping chunks,
     * we instead drop the current regionfile header and recalculate -
     * hoping that at least then we don't swap chunks, and maybe recover
     * them all.
     */
    @Override
    public final boolean moonrise$recalculateHeader() throws IOException {
        if (!this.canRecalcHeader()) {
            return false;
        }

        final ChunkPos regionPos = RegionFileRecovery.getRegionFileCoordinates(this.path);

        synchronized (this) {
            this.recalculateCount.getAndIncrement();

            LOGGER.warn("Corrupt regionfile header detected! Attempting to re-calculate header offsets for regionfile {}", this.path.toAbsolutePath(), new Throwable());

            // try to backup file so maybe it could be sent to us for further investigation
            this.backupRegionFile();

            final boolean[] hasInternal = new boolean[32 * 32];
            final long[] saveTimes = new long[32 * 32];
            final int[] rawLengths = new int[32 * 32]; // length of chunk data including 4 byte length field, bytes
            final int[] sectorOffsets = new int[32 * 32]; // in sectors

            final long fileLength = this.file.size();
            final long totalSectors = roundToSectors(fileLength);

            // search the regionfile from start to finish for the most up-to-date chunk data
            for (long i = 2, maxSector = Math.min((long)(Integer.MAX_VALUE >>> 8), totalSectors); i < maxSector; ++i) { // first two sectors are header, skip
                final int chunkDataLength = this.readLength(i);
                final CompoundTag compound = this.attemptRead(i, chunkDataLength, fileLength);
                if (compound == null || compound == EXTERNAL_STUB_COMPOUND) {
                    continue;
                }

                // the data is valid, so skip over it for the next iteration
                final int rawLength = chunkDataLength + 4;
                final long sector = i;
                i += roundToSectors(rawLength) - 1L;

                final ChunkPos chunkPos = RegionFileRecovery.getChunkCoordinate(compound);
                if (!RegionFileRecovery.inSameRegionFile(regionPos, chunkPos)) {
                    LOGGER.error("Ignoring absolute chunk {} in regionfile as it is not contained in the bounds of the regionfile '{}'. It should be in regionfile ({},{})", chunkPos, this.path.toAbsolutePath(), chunkPos.x() >> 5, chunkPos.z() >> 5);
                    continue;
                }

                final int location = (chunkPos.x() & 31) | ((chunkPos.z() & 31) << 5);
                final long saveTime = RegionFileRecovery.getLastWorldSaveTime(compound);

                if (hasInternal[location] && saveTimes[location] > saveTime) {
                    continue; // don't overwrite newer data
                }

                hasInternal[location] = true;
                saveTimes[location] = saveTime;
                rawLengths[location] = rawLength;
                sectorOffsets[location] = (int)sector;
            }

            // now search external chunk data, which is newer if its save time is greater than the internal data
            final boolean[] useExternal = new boolean[32 * 32];
            final RegionFileVersion[] externalCompressionTypes = new RegionFileVersion[32 * 32];

            // only check the external files that could belong to this regionfile, so that the work is bounded
            // regardless of how many files are in the directory
            for (int location = 0; location < 32 * 32; ++location) {
                final ChunkPos externalPos = new ChunkPos(regionPos.x() | (location & 31), regionPos.z() | (location >>> 5));
                final Path externalFile = this.getExternalChunkPath(externalPos);
                if (!Files.isRegularFile(externalFile)) {
                    continue;
                }

                final byte[] chunkData;
                try {
                    chunkData = Files.readAllBytes(externalFile);
                } catch (final Exception ex) {
                    LOGGER.error("Failed to read external chunk data in file {}, data will be lost", externalFile.toAbsolutePath(), ex);
                    continue;
                }

                // the compression type is stored in the regionfile, so we need to try all of them
                CompoundTag compound = null;
                RegionFileVersion compression = null;
                for (final RegionFileVersion compressionType : RegionFileVersion.VERSIONS.values()) {
                    final CompoundTag read;
                    try (final DataInputStream input = new DataInputStream(compressionType.wrap(new ByteArrayInputStream(chunkData)))) {
                        read = FastNBTParser.nbtIoRead(input);
                    } catch (final Exception ex) {
                        continue;
                    }
                    compound = read;
                    compression = compressionType;
                    break;
                }

                if (compound == null) {
                    LOGGER.error("Failed to read external chunk data in file {}, it's corrupt. Its data will be lost", externalFile.toAbsolutePath());
                    continue;
                }

                final ChunkPos storedPos = RegionFileRecovery.getChunkCoordinate(compound);
                if (!storedPos.equals(externalPos)) {
                    LOGGER.error("Can't use external chunk stored in {}, got absolute chunkpos: {}, expected {}", externalFile.toAbsolutePath(), storedPos, externalPos);
                    continue;
                }

                if (!hasInternal[location] || RegionFileRecovery.getLastWorldSaveTime(compound) > saveTimes[location]) {
                    useExternal[location] = true;
                    externalCompressionTypes[location] = compression;
                }
            }

            // now we need to calculate a new offset header

            final int[] calculatedOffsets = new int[32 * 32];
            final BitSet newSectorAllocations = new BitSet();
            newSectorAllocations.set(0, 2); // make space for header

            // allocate sectors for internal chunks
            for (int location = 0; location < 32 * 32; ++location) {
                if (useExternal[location] || !hasInternal[location]) {
                    continue;
                }

                final int sectorOffset = sectorOffsets[location];
                final int sectorLength = (int)roundToSectors(rawLengths[location]);

                if (sectorLength > MAX_INTERNAL_SECTORS) {
                    // cannot be referenced by the header, so store it externally like Vanilla would
                    final ChunkPos pos = new ChunkPos(regionPos.x() | (location & 31), regionPos.z() | (location >>> 5));
                    final RegionFileVersion compression = this.moveToExternalFile(pos, sectorOffset, rawLengths[location]);
                    if (compression != null) {
                        LOGGER.warn("Moved oversized data for local chunk ({},{}) in regionfile {} to external file", location & 31, location >>> 5, this.path.toAbsolutePath());
                        useExternal[location] = true;
                        externalCompressionTypes[location] = compression;
                    } // else: chunk will be regenerated
                    continue;
                }

                if (RegionFileRecovery.tryAllocate(newSectorAllocations, sectorOffset, sectorLength)) {
                    calculatedOffsets[location] = this.packSectorOffset(sectorOffset, sectorLength);
                } else {
                    LOGGER.error("Failed to allocate space for local chunk (overlapping data??) at ({},{}) in regionfile {}, chunk will be regenerated", location & 31, location >>> 5, this.path.toAbsolutePath());
                }
            }

            // allocate sectors for external chunk stubs
            for (int location = 0; location < 32 * 32; ++location) {
                if (!useExternal[location]) {
                    continue;
                }

                final int sectorOffset = RegionFileRecovery.allocate(newSectorAllocations, 1);

                final ByteBuffer stub = ByteBuffer.allocate(5);
                stub.putInt(1);
                stub.put((byte)(externalCompressionTypes[location].getId() | EXTERNAL_STREAM_FLAG));
                stub.flip();

                try {
                    this.file.write(stub, sectorOffset * 4096L);
                    // only allocate in the new offsets if the write succeeds
                    calculatedOffsets[location] = this.packSectorOffset(sectorOffset, 1);
                } catch (final IOException ex) {
                    newSectorAllocations.clear(sectorOffset);
                    LOGGER.error("Failed to write new external chunk stub, local chunk at ({},{}) in regionfile {} will be regenerated", location & 31, location >>> 5, this.path.toAbsolutePath(), ex);
                }
            }

            this.setUsedSectors(newSectorAllocations);

            // print a summary of the chunks that got changed

            LOGGER.info("Starting summary of changes for regionfile {}", this.path.toAbsolutePath());

            for (int location = 0; location < 32 * 32; ++location) {
                final int oldOffset = this.offsets.get(location);
                final int newOffset = calculatedOffsets[location];

                if (oldOffset == newOffset) {
                    continue;
                }

                this.offsets.put(location, newOffset); // overwrite incorrect offset

                if (oldOffset == 0) {
                    LOGGER.info("Found missing data for local chunk ({},{}) in regionfile {}", location & 31, location >>> 5, this.path.toAbsolutePath());
                } else if (newOffset == 0) {
                    LOGGER.warn("Data for local chunk ({},{}) could not be recovered in regionfile {}, it will be regenerated", location & 31, location >>> 5, this.path.toAbsolutePath());
                } else {
                    LOGGER.info("Local chunk ({},{}) changed to point to newer data or correct chunk in regionfile {}", location & 31, location >>> 5, this.path.toAbsolutePath());
                }
            }

            LOGGER.info("End of change summary for regionfile {}", this.path.toAbsolutePath());

            // the timestamp header is not used, so simply write a valid timestamp for valid chunks
            final int timestamp = getTimestamp();
            for (int location = 0; location < 32 * 32; ++location) {
                this.timestamps.put(location, calculatedOffsets[location] != 0 ? timestamp : 0);
            }

            // write new header
            try {
                this.writeHeader();
                this.file.force(true);
                LOGGER.info("Successfully wrote new header to disk for regionfile {}", this.path.toAbsolutePath());
            } catch (final IOException ex) {
                LOGGER.error("Failed to write new header to disk for regionfile {}", this.path.toAbsolutePath(), ex);
            }
        }

        return true;
    }

    /**
     * @reason Header validation is performed in {@link #validateHeader(CallbackInfo)}, as invalid entries must be
     *         kept in the header for the recalculation change summary
     * @author Spottedleaf
     */
    @Redirect(
            method = "<init>(Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/level/chunk/storage/RegionFileVersion;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/nio/IntBuffer;put(II)Ljava/nio/IntBuffer;"
            )
    )
    private IntBuffer skipVanillaHeaderClear(final IntBuffer instance, final int index, final int value) {
        return instance;
    }

    /**
     * @reason Sector allocation is performed in {@link #validateHeader(CallbackInfo)}, so that overlapping
     *         allocations can be detected
     * @author Spottedleaf
     */
    @Redirect(
            method = "<init>(Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/level/chunk/storage/RegionFileVersion;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/RegionBitmap;force(II)V"
            )
    )
    private void skipVanillaSectorAllocation(final RegionBitmap instance, final int position, final int size) {}

    /**
     * @reason Recalculate the header if it contains invalid or overlapping entries. If the header cannot be
     *         recalculated, then remove the bad entries.
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/level/chunk/storage/RegionFileVersion;Z)V",
            at = @At(
                    value = "RETURN"
            )
    )
    private void validateHeader(final CallbackInfo ci) throws IOException {
        final long size = this.file.size();
        final boolean canRecalc = this.canRecalcHeader();

        final BitSet allocated = new BitSet();
        allocated.set(0, 2); // header

        boolean needsRecalc = false;
        boolean hasBackedUp = false;

        for (int location = 0; location < 32 * 32; ++location) {
            final int offset = this.offsets.get(location);
            if (offset == 0) {
                continue;
            }

            final int sectorNumber = getSectorNumber(offset);
            final int numSectors = getNumSectors(offset);

            final boolean invalid = sectorNumber < 2 || numSectors == 0 || (long)sectorNumber * 4096L > size;
            if (!invalid && RegionFileRecovery.tryAllocate(allocated, sectorNumber, numSectors)) {
                continue;
            }

            if (!invalid) {
                LOGGER.error("Overlapping allocation by local chunk ({},{}) in regionfile {}", location & 31, location >>> 5, this.path.toAbsolutePath());
            } else {
                LOGGER.error("Invalid allocation by local chunk ({},{}) in regionfile {}: sector {}, length {} sectors, file size {} bytes", location & 31, location >>> 5, this.path.toAbsolutePath(), sectorNumber, numSectors, size);
            }

            if (canRecalc) {
                needsRecalc = true;
                continue;
            }

            LOGGER.error("Detected invalid header for regionfile {}! Cannot recalculate, removing local chunk ({},{}) from header", this.path.toAbsolutePath(), location & 31, location >>> 5);
            if (!hasBackedUp) {
                hasBackedUp = true;
                this.backupRegionFile();
            }
            this.timestamps.put(location, 0); // be consistent, delete the timestamp too
            this.offsets.put(location, 0); // delete the entry from header
        }

        this.setUsedSectors(allocated);

        if (!needsRecalc) {
            return;
        }

        LOGGER.error("Recalculating regionfile {}, header gave erroneous offsets & locations", this.path.toAbsolutePath());
        try {
            this.moonrise$recalculateHeader();
        } catch (final IOException | RuntimeException ex) {
            try {
                this.file.close();
            } catch (final IOException suppressed) {
                ex.addSuppressed(suppressed);
            }
            throw ex;
        }
    }

    /**
     * @reason Attempt to recalculate the header if the data it points to is corrupt
     * @author Spottedleaf
     */
    @Overwrite
    public synchronized DataInputStream getChunkDataInputStream(final ChunkPos pos) throws IOException {
        return this.getChunkDataInputStream(pos, true);
    }

    @Unique
    private DataInputStream recalculateAndRetry(final ChunkPos pos, final boolean allowRecalc) throws IOException {
        if (allowRecalc && this.moonrise$recalculateHeader()) {
            return this.getChunkDataInputStream(pos, false);
        }
        return null;
    }

    @Unique
    private DataInputStream getChunkDataInputStream(final ChunkPos pos, final boolean allowRecalc) throws IOException {
        synchronized (this) {
            final int offset = this.getOffset(pos);
            if (offset == 0) {
                return null;
            }

            final int sectorNumber = getSectorNumber(offset);
            final int numSectors = getNumSectors(offset);
            final int sectorsLength = numSectors * 4096;
            final ByteBuffer buffer = ByteBuffer.allocate(sectorsLength);
            this.file.read(buffer, sectorNumber * 4096L);
            buffer.flip();
            if (buffer.remaining() < 5) {
                LOGGER.error("Chunk {} in {} header is truncated: expected {} but read {}", pos, this.path.toAbsolutePath(), sectorsLength, buffer.remaining());
                return this.recalculateAndRetry(pos, allowRecalc);
            }

            final int length = buffer.getInt();
            final byte versionId = buffer.get();
            if (length == 0) {
                LOGGER.warn("Chunk {} in {} is allocated, but stream is missing", pos, this.path.toAbsolutePath());
                return this.recalculateAndRetry(pos, allowRecalc);
            }

            final int streamLength = length - 1;
            if (isExternalStreamChunk(versionId)) {
                if (streamLength != 0) {
                    LOGGER.warn("Chunk {} in {} has both internal and external streams", pos, this.path.toAbsolutePath());
                    if (allowRecalc && this.moonrise$recalculateHeader()) {
                        return this.getChunkDataInputStream(pos, false);
                    }
                }

                final DataInputStream ret = this.createExternalChunkInputStream(pos, getExternalChunkVersion(versionId));
                return ret != null ? ret : this.recalculateAndRetry(pos, allowRecalc);
            } else if (streamLength > buffer.remaining()) {
                LOGGER.error("Chunk {} in {} stream is truncated: expected {} but read {}", pos, this.path.toAbsolutePath(), streamLength, buffer.remaining());
                return this.recalculateAndRetry(pos, allowRecalc);
            } else if (streamLength < 0) {
                LOGGER.error("Declared size {} of chunk {} in {} is negative", length, pos, this.path.toAbsolutePath());
                return this.recalculateAndRetry(pos, allowRecalc);
            } else {
                JvmProfiler.INSTANCE.onRegionFileRead(this.info, pos, this.version, streamLength);
                final DataInputStream ret = this.createChunkInputStream(pos, versionId, createStream(buffer, streamLength));
                // do not recalculate for custom compression, as the data is likely valid but unsupported
                return ret != null || versionId == RegionFileVersion.VERSION_CUSTOM.getId() ? ret : this.recalculateAndRetry(pos, allowRecalc);
            }
        }
    }
}
