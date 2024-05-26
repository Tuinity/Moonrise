package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.io.ChunkSystemRegionFileStorage;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(RegionFileStorage.class)
public abstract class RegionFileStorageMixin implements ChunkSystemRegionFileStorage, AutoCloseable {

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
    private static final int REGION_SHIFT = 5;

    @Unique
    private static final int MAX_NON_EXISTING_CACHE = 1024 * 64;

    @Unique
    private final LongLinkedOpenHashSet nonExistingRegionFiles = new LongLinkedOpenHashSet(MAX_NON_EXISTING_CACHE+1);

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
        return !this.doesRegionFilePossiblyExist(ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfLoaded(final int chunkX, final int chunkZ) {
        return this.regionCache.getAndMoveToFirst(ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfExists(final int chunkX, final int chunkZ) throws IOException {
        final long key = ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);

        RegionFile ret = this.regionCache.getAndMoveToFirst(key);
        if (ret != null) {
            return ret;
        }

        if (!this.doesRegionFilePossiblyExist(key)) {
            return null;
        }

        if (this.regionCache.size() >= MAX_CACHE_SIZE) {
            this.regionCache.removeLast().close();
        }

        final Path regionPath = this.folder.resolve(getRegionFileName(chunkX, chunkZ));

        if (!Files.exists(regionPath)) {
            this.markNonExisting(key);
            return null;
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
            final long key = ChunkPos.asLong(chunkPos.x >> REGION_SHIFT, chunkPos.z >> REGION_SHIFT);

            RegionFile ret = this.regionCache.getAndMoveToFirst(key);
            if (ret != null) {
                return ret;
            }

            if (this.regionCache.size() >= MAX_CACHE_SIZE) {
                this.regionCache.removeLast().close();
            }

            final Path regionPath = this.folder.resolve(getRegionFileName(chunkPos.x, chunkPos.z));

            this.createRegionFile(key);

            FileUtil.createDirectoriesSafe(this.folder);

            ret = new RegionFile(this.info, regionPath, this.folder, this.sync);

            this.regionCache.putAndMoveToFirst(key, ret);

            return ret;
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
        return ((RegionFileStorageMixin)(Object)instance).moonrise$getRegionFileIfExists(chunkPos.x, chunkPos.z);
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
        return ((RegionFileStorageMixin)(Object)instance).moonrise$getRegionFileIfExists(chunkPos.x, chunkPos.z);
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
        if (compoundTag == null && this.moonrise$getRegionFileIfExists(chunkPos.x, chunkPos.z) == null) {
            ci.cancel();
            return;
        }
        // double reading the RegionFile is fine, as the result is cached
    }
}
