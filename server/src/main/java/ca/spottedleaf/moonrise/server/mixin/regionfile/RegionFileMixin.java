package ca.spottedleaf.moonrise.server.mixin.regionfile;

import net.minecraft.world.chunk.storage.RegionFile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;

@Mixin(RegionFile.class)
abstract class RegionFileMixin {

	@Shadow
	@Final
	private int[] chunkBlockInfo;

	@Shadow
	private RandomAccessFile randomAccessFile;

	@Shadow
	@Final
	private int[] chunkSaveTimes;

	@Shadow
	private ArrayList<Boolean> blockEmptyFlags;

	@Shadow
	private int bytesWritten;

	@Shadow
	@Final
	private static byte[] BLOCK_BUFFER;

	@Shadow
	protected abstract int getChunkBlockInfo(int chunkX, int chunkZ);



	@Unique
	private static final boolean FLUSH_ON_SAVE = false;

	@Unique
	private void syncRegionFile() throws IOException {
		if (!FLUSH_ON_SAVE) {
			return;
		}
		this.randomAccessFile.getFD().sync(); // rethrow exception as we want to avoid corrupting a regionfile
	}

	@Unique
	private final ByteBuffer scratchBuffer = ByteBuffer.allocate(8);

	@Unique
	private void writeInt(final int value) throws IOException {
		this.scratchBuffer.putInt(0, value);
		this.randomAccessFile.write(this.scratchBuffer.array(), 0, 4);
	}

	// writes v1 then v2
	@Unique
	private void writeIntAndByte(final int v1, final byte v2) throws IOException {
		this.scratchBuffer.putInt(0, v1);
		this.scratchBuffer.put(4, v2);
		this.randomAccessFile.write(this.scratchBuffer.array(), 0, 5);
	}

	@Unique
	private void writeChunk(final int x, final int z, final int chunkHeaderData,
							final int chunkOffset, final byte[] chunkData, final int chunkDataLength) throws IOException {
		this.writeChunkData(chunkOffset, chunkData, chunkDataLength);
		this.syncRegionFile(); // Sync is required to ensure the previous data is written successfully
		this.writeChunkSaveTime(x, z, (int)(System.currentTimeMillis() / 1000L));
		this.writeChunkBlockInfo(x, z, chunkHeaderData);
		this.syncRegionFile(); // Ensure header changes go through
	}

	/**
	 * @reason Re-order internal header write to be after IO
	 * @author Spottedleaf
	 */
	@Overwrite
	protected void writeChunkSaveTime(final int chunkX, final int chunkZ, final int timeSeconds) throws IOException {
		this.randomAccessFile.seek((long)(4096 + (chunkX + chunkZ * 32) * 4));
		this.writeInt(timeSeconds);
		this.chunkSaveTimes[chunkX + chunkZ * 32] = timeSeconds;
	}

	/**
	 * @reason Re-order internal header write to be after IO
	 * @author Spottedleaf
	 */
	@Overwrite
	protected void writeChunkBlockInfo(final int chunkX, final int chunkZ, final int blockInfo) throws IOException {
		this.randomAccessFile.seek((long)((chunkX + chunkZ * 32) * 4));
		this.writeInt(blockInfo);
		this.chunkBlockInfo[chunkX + chunkZ * 32] = blockInfo;
	}

	/**
	 * @reason Avoid unnecessary IO write
	 * @author Spottedleaf
	 */
	@Overwrite
	protected void writeChunkData(final int blockOffset, final byte[] data, final int size) throws IOException {
		this.randomAccessFile.seek((long)(blockOffset * 4096));
		this.writeIntAndByte(size + 1, (byte)2); // avoid 1 extra io writes
		this.randomAccessFile.write(data, 0, size);
	}

	/**
	 * @reason Change the order of operations such that if interrupted (by unexpected IO issue or shutdown) that
	 *         no chunk data is corrupted.
	 * @author Spottedleaf
	 */
	@Overwrite
	protected synchronized void writeChunkData(final int chunkX, final int chunkZ, final byte[] data, final int size) {
		try {
			final int blockInfo = this.getChunkBlockInfo(chunkX, chunkZ);
			final int oldBlockOffset = blockInfo >> 8;
			final int oldBlockSize = blockInfo & 255;
			final int newBlockSize = (size + 5) / 4096 + 1;
			if (newBlockSize >= 256) {
				// lmao no logging
				return;
			}

			// find existing space
			int existingFreeBlock = this.blockEmptyFlags.indexOf(Boolean.TRUE);
			int existingFreeBlockSize = 0;
			if (existingFreeBlock != -1) {
				for (int i = existingFreeBlock; i < this.blockEmptyFlags.size(); ++i) {
					if (existingFreeBlockSize != 0) {
						if (this.blockEmptyFlags.get(i).booleanValue()) {
							++existingFreeBlockSize;
						} else {
							existingFreeBlockSize = 0;
						}
					} else if (this.blockEmptyFlags.get(i).booleanValue()) {
						existingFreeBlock = i;
						existingFreeBlockSize = 1;
					}

					if (existingFreeBlockSize >= newBlockSize) {
						break;
					}
				}
			}

			// allocate or mark existing space
			if (existingFreeBlockSize >= newBlockSize) {
				for (int i = 0; i < newBlockSize; ++i) {
					this.blockEmptyFlags.set(existingFreeBlock + i, Boolean.FALSE);
				}
			} else {
				this.randomAccessFile.seek(this.randomAccessFile.length());
				existingFreeBlock = this.blockEmptyFlags.size();

				for (int i = 0; i < newBlockSize; ++i) {
					this.randomAccessFile.write(BLOCK_BUFFER);
					this.blockEmptyFlags.add(Boolean.FALSE);
				}

				this.bytesWritten += 4096 * newBlockSize;
			}

			// use new write function which is reliable
			this.writeChunk(chunkX, chunkZ, existingFreeBlock << 8 | newBlockSize, existingFreeBlock, data, size);

			// only free old allocation after finishing write to new data
			for (int i = 0; i < oldBlockSize; ++i) {
				this.blockEmptyFlags.set(oldBlockOffset + i, Boolean.TRUE);
			}
		} catch (final IOException ex) {
			ex.printStackTrace();
		}
	}
}
