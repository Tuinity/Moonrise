package ca.spottedleaf.moonrise.patches.starlight;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.patches.starlight.data.StarlightNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.world.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.world.StarlightWorld;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StarlightEngine {

	private static final int AIR_BLOCK_STATE = 0;

	private static enum AxisDirection {

		// Declaration order is important and relied upon. Do not change without modifying propagation code.
		POSITIVE_X(1, 0, 0), NEGATIVE_X(-1, 0, 0),
		POSITIVE_Z(0, 0, 1), NEGATIVE_Z(0, 0, -1),
		POSITIVE_Y(0, 1, 0), NEGATIVE_Y(0, -1, 0);

		static {
			POSITIVE_X.opposite = NEGATIVE_X; NEGATIVE_X.opposite = POSITIVE_X;
			POSITIVE_Z.opposite = NEGATIVE_Z; NEGATIVE_Z.opposite = POSITIVE_Z;
			POSITIVE_Y.opposite = NEGATIVE_Y; NEGATIVE_Y.opposite = POSITIVE_Y;
		}

		private AxisDirection opposite;

		public final int x;
		public final int y;
		public final int z;
		public final long everythingButThisDirection;
		public final long everythingButTheOppositeDirection;

		AxisDirection(final int x, final int y, final int z) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.everythingButThisDirection = (long)(ALL_DIRECTIONS_BITSET ^ (1 << this.ordinal()));
			// positive is always even, negative is always odd. Flip the 1 bit to get the negative direction.
			this.everythingButTheOppositeDirection = (long)(ALL_DIRECTIONS_BITSET ^ (1 << (this.ordinal() ^ 1)));
		}

		public AxisDirection getOpposite() {
			return this.opposite;
		}
	}

	private static final AxisDirection[] DIRECTIONS = AxisDirection.values();
	private static final AxisDirection[] AXIS_DIRECTIONS = DIRECTIONS;
	private static final AxisDirection[] ONLY_HORIZONTAL_DIRECTIONS = new AxisDirection[] {
		AxisDirection.POSITIVE_X, AxisDirection.NEGATIVE_X,
		AxisDirection.POSITIVE_Z, AxisDirection.NEGATIVE_Z
	};

	private static final AxisDirection[][] OLD_CHECK_DIRECTIONS = new AxisDirection[1 << 6][];
	private static final int ALL_DIRECTIONS_BITSET = (1 << 6) - 1;
	static {
		for (int i = 0; i < OLD_CHECK_DIRECTIONS.length; ++i) {
			final List<AxisDirection> directions = new ArrayList<>();
			for (int bitset = i, len = Integer.bitCount(i), index = 0; index < len; ++index, bitset ^= (-bitset & bitset)) {
				directions.add(AXIS_DIRECTIONS[Integer.numberOfTrailingZeros(bitset)]);
			}
			OLD_CHECK_DIRECTIONS[i] = directions.toArray(new AxisDirection[0]);
		}
	}

	// always initialsed during start of lighting.
	// index = x + (z * 5)
	private final StarlightChunk[] chunkCache = new StarlightChunk[5 * 5];
	private final StarlightNibbleArray[] nibbleCache = new StarlightNibbleArray[5 * 5];

	private int encodeOffsetX;
	private int encodeOffsetY;
	private int encodeOffsetZ;

	private int coordinateOffset;

	private int chunkOffsetX;
	private int chunkOffsetY;
	private int chunkOffsetZ;

	private int chunkIndexOffset;
	private int chunkSectionIndexOffset;

	private final boolean skylightPropagator;
	private final int emittedLightMask;

	private final StarlightWorld world;

	public StarlightEngine(final boolean skylightPropagator, final StarlightWorld world) {
		this.skylightPropagator = skylightPropagator;
		this.emittedLightMask = skylightPropagator ? 0 : 0xF;
		this.world = world;
	}

	private void setupEncodeOffset(final int centerX, final int centerY, final int centerZ) {
		// 31 = center + encodeOffset
		this.encodeOffsetX = 31 - centerX;
		this.encodeOffsetY = (-(-1 - 1) << 4); // we want 0 to be the smallest encoded value
		this.encodeOffsetZ = 31 - centerZ;

		// coordinateIndex = x | (z << 6) | (y << 12)
		this.coordinateOffset = this.encodeOffsetX + (this.encodeOffsetZ << 6) + (this.encodeOffsetY << 12);

		// 2 = (centerX >> 4) + chunkOffset
		this.chunkOffsetX = 2 - (centerX >> 4);
		this.chunkOffsetY = -(-1 - 1); // lowest should be 0
		this.chunkOffsetZ = 2 - (centerZ >> 4);

		// chunk index = x + (5 * z)
		this.chunkIndexOffset = this.chunkOffsetX + (5 * this.chunkOffsetZ);

		// chunk section index = x + (5 * z) + ((5*5) * y)
		this.chunkSectionIndexOffset = this.chunkIndexOffset + ((5 * 5) * this.chunkOffsetY);
	}

	private void setupCaches(final StarlightWorld chunkProvider, final int centerX, final int centerY, final int centerZ,
							 final boolean relaxed) {
		final int centerChunkX = centerX >> 4;
		final int centerChunkY = centerY >> 4;
		final int centerChunkZ = centerZ >> 4;

		this.setupEncodeOffset(centerChunkX * 16 + 7, centerChunkY * 16 + 7, centerChunkZ * 16 + 7);

		final int radius = 1;

		for (int dz = -radius; dz <= radius; ++dz) {
			for (int dx = -radius; dx <= radius; ++dx) {
				final int cx = centerChunkX + dx;
				final int cz = centerChunkZ + dz;
				final StarlightChunk chunk = chunkProvider.starlight$getChunkIfLoaded(cx, cz);

				if (chunk == null) {
					if (relaxed) {
						continue;
					}
					throw new IllegalArgumentException("Trying to propagate light update before 1 radius neighbours ready");
				}

				this.setChunkInCache(cx, cz, chunk);
			}
		}
	}

	private StarlightChunk getChunkInCache(final int chunkX, final int chunkZ) {
		return this.chunkCache[chunkX + 5*chunkZ + this.chunkIndexOffset];
	}

	private void setChunkInCache(final int chunkX, final int chunkZ, final StarlightChunk chunk) {
		final int index = chunkX + 5*chunkZ + this.chunkIndexOffset;
		this.chunkCache[index] = chunk;
		this.nibbleCache[index] = chunk == null ? null : (this.skylightPropagator ? chunk.starlight$getSkyLight() : chunk.starlight$getBlockLight());
	}

	private StarlightNibbleArray getNibbleFromCache(final int chunkX, final int chunkZ) {
		return this.nibbleCache[chunkX + 5*chunkZ + this.chunkIndexOffset];
	}

	private int getBlockState(final int worldX, final int worldY, final int worldZ) {
		final StarlightChunk chunk = this.getChunkInCache(worldX >> 4, worldZ >> 4);

		if (chunk == null || worldY < 0 || worldY > 127) {
			return AIR_BLOCK_STATE;
		}

		return chunk.starlight$getBlock(worldX & 15, worldY & 127, worldZ & 15);
	}


	private int getLightLevel(final int worldX, final int worldY, final int worldZ) {
		final StarlightNibbleArray nibble = this.getNibbleFromCache(worldX >> 4, worldZ >> 4);

		if (nibble != null && worldY >= 0 && worldY <= 127) {
			return nibble.starlight$getData(worldX & 15, worldY & 127, worldZ & 15);
		}

		if (this.skylightPropagator) {
			return worldY > 127 ? 15 : (nibble == null ? 15 : Math.max(0, nibble.starlight$getData(worldX & 15, 0, worldZ & 15) - worldY)); // best approximation
		} else {
			return 0;
		}
	}

	private void destroyCaches() {
		for (final StarlightChunk chunk : this.chunkCache) {
			if (chunk == null) {
				continue;
			}

			final StarlightNibbleArray sky = chunk.starlight$getSkyLight();
			final StarlightNibbleArray block = chunk.starlight$getBlockLight();

			if (sky.starlight$isDirty() || block.starlight$isDirty()) {
				sky.starlight$setDirty(false);
				block.starlight$setDirty(false);

				chunk.starlight$markDirty();
			}
		}
		Arrays.fill(this.chunkCache, null);
		Arrays.fill(this.nibbleCache, null);
	}

	private void setLightLevel(final int worldX, final int worldY, final int worldZ, final int level) {
		final StarlightNibbleArray nibble = this.getNibbleFromCache(worldX >> 4, worldZ >> 4);

		if (nibble != null && worldY >= 0 && worldY <= 127) {
			nibble.starlight$setData(worldX & 15, worldY & 127, worldZ & 15, level);
		}
	}

	private void postLightUpdate(final int worldX, final int worldY, final int worldZ) {
		// not needed server side
		// TODO client side
	}

	// contains:
	// lower (6 + 6 + 16) = 28 bits: encoded coordinate position (x | (z << 6) | (y << (6 + 6))))
	// next 4 bits: propagated light level (0, 15]
	// next 6 bits: propagation direction bitset
	// next 24 bits: unused
	// last 3 bits: state flags
	// state flags:
	// whether the increase propagator needs to write the propagated level to the position, used to avoid cascading light
	// updates for block sources
	private static final long FLAG_WRITE_LEVEL = Long.MIN_VALUE >>> 2;
	// whether the propagation needs to check if its current level is equal to the expected level
	// used only in increase propagation
	private static final long FLAG_RECHECK_LEVEL = Long.MIN_VALUE >>> 1;
	// whether the propagation needs to consider if its block is conditionally transparent
	private static final long FLAG_HAS_SIDED_TRANSPARENT_BLOCKS = Long.MIN_VALUE;

	private long[] increaseQueue = new long[16 * 16 * 16];
	private int increaseQueueInitialLength;
	private long[] decreaseQueue = new long[16 * 16 * 16];
	private int decreaseQueueInitialLength;

	private long[] resizeIncreaseQueue() {
		return this.increaseQueue = Arrays.copyOf(this.increaseQueue, this.increaseQueue.length * 2);
	}

	private long[] resizeDecreaseQueue() {
		return this.decreaseQueue = Arrays.copyOf(this.decreaseQueue, this.decreaseQueue.length * 2);
	}

	private void appendToIncreaseQueue(final long value) {
		final int idx = this.increaseQueueInitialLength++;
		long[] queue = this.increaseQueue;
		if (idx >= queue.length) {
			queue = this.resizeIncreaseQueue();
			queue[idx] = value;
		} else {
			queue[idx] = value;
		}
	}

	private void appendToDecreaseQueue(final long value) {
		final int idx = this.decreaseQueueInitialLength++;
		long[] queue = this.decreaseQueue;
		if (idx >= queue.length) {
			queue = this.resizeDecreaseQueue();
			queue[idx] = value;
		} else {
			queue[idx] = value;
		}
	}

	private void performLightIncrease() {
		long[] queue = this.increaseQueue;
		int queueReadIndex = 0;
		int queueLength = this.increaseQueueInitialLength;
		this.increaseQueueInitialLength = 0;
		final int decodeOffsetX = -this.encodeOffsetX;
		final int decodeOffsetY = -this.encodeOffsetY;
		final int decodeOffsetZ = -this.encodeOffsetZ;
		final int encodeOffset = this.coordinateOffset;

		while (queueReadIndex < queueLength) {
			final long queueValue = queue[queueReadIndex++];

			final int posX = ((int)queueValue & 63) + decodeOffsetX;
			final int posZ = (((int)queueValue >>> 6) & 63) + decodeOffsetZ;
			final int posY = (((int)queueValue >>> 12) & ((1 << 16) - 1)) + decodeOffsetY;
			final int propagatedLightLevel = (int)((queueValue >>> (6 + 6 + 16)) & 0xFL);
			final AxisDirection[] checkDirections = OLD_CHECK_DIRECTIONS[(int)((queueValue >>> (6 + 6 + 16 + 4)) & 63L)];

			if ((queueValue & FLAG_RECHECK_LEVEL) != 0L) {
				if (this.getLightLevel(posX, posY, posZ) != propagatedLightLevel) {
					// not at the level we expect, so something changed.
					continue;
				}
			} else if ((queueValue & FLAG_WRITE_LEVEL) != 0L) {
				// these are used to restore block sources after a propagation decrease
				this.setLightLevel(posX, posY, posZ, propagatedLightLevel);
			}

			// we don't need to worry about our state here.
			for (final AxisDirection propagate : checkDirections) {
				final int offX = posX + propagate.x;
				final int offY = posY + propagate.y;
				final int offZ = posZ + propagate.z;

				if (offY < 0 || offY > 127) {
					continue;
				}

				final StarlightNibbleArray currentNibble = this.getNibbleFromCache(offX >> 4, offZ >> 4);
				final int currentLevel;
				if (currentNibble == null || (currentLevel = currentNibble.starlight$getData(offX & 15, offY & 127, offZ & 15)) >= (propagatedLightLevel - 1)) {
					continue; // already at the level we want or unloaded
				}

				final int blockState = this.getBlockState(offX, offY, offZ);
				final int opacityCached = PlatformHooks.get().getLightBlock(blockState);

				final int targetLevel = propagatedLightLevel - Math.max(1, opacityCached);
				if (targetLevel > currentLevel) {
					currentNibble.starlight$setData(offX & 15, offY & 127, offZ & 15, targetLevel);
					this.postLightUpdate(offX, offY, offZ);

					if (targetLevel > 1) {
						if (queueLength >= queue.length) {
							queue = this.resizeIncreaseQueue();
						}
						queue[queueLength++] =
							((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
								| ((targetLevel & 0xFL) << (6 + 6 + 16))
								| (propagate.everythingButTheOppositeDirection << (6 + 6 + 16 + 4));
						continue;
					}
				}
			}
		}
	}

	private void performLightDecrease() {
		long[] queue = this.decreaseQueue;
		long[] increaseQueue = this.increaseQueue;
		int queueReadIndex = 0;
		int queueLength = this.decreaseQueueInitialLength;
		this.decreaseQueueInitialLength = 0;
		int increaseQueueLength = this.increaseQueueInitialLength;
		final int decodeOffsetX = -this.encodeOffsetX;
		final int decodeOffsetY = -this.encodeOffsetY;
		final int decodeOffsetZ = -this.encodeOffsetZ;
		final int encodeOffset = this.coordinateOffset;
		final int emittedMask = this.emittedLightMask;

		while (queueReadIndex < queueLength) {
			final long queueValue = queue[queueReadIndex++];

			final int posX = ((int)queueValue & 63) + decodeOffsetX;
			final int posZ = (((int)queueValue >>> 6) & 63) + decodeOffsetZ;
			final int posY = (((int)queueValue >>> 12) & ((1 << 16) - 1)) + decodeOffsetY;
			final int propagatedLightLevel = (int)((queueValue >>> (6 + 6 + 16)) & 0xF);
			final AxisDirection[] checkDirections = OLD_CHECK_DIRECTIONS[(int)((queueValue >>> (6 + 6 + 16 + 4)) & 63)];

			// we don't need to worry about our state here.
			for (final AxisDirection propagate : checkDirections) {
				final int offX = posX + propagate.x;
				final int offY = posY + propagate.y;
				final int offZ = posZ + propagate.z;

				if (offY < 0 || offY > 127) {
					continue;
				}

				final StarlightNibbleArray currentNibble = this.getNibbleFromCache(offX >> 4, offZ >> 4);
				final int lightLevel;

				if (currentNibble == null || (lightLevel = currentNibble.starlight$getData(offX & 15, offY & 127, offZ & 15)) == 0) {
					// already at lowest (or unloaded), nothing we can do
					continue;
				}

				final int blockState = this.getBlockState(offX, offY, offZ);
				final int opacityCached = PlatformHooks.get().getLightBlock(blockState);

				final int targetLevel = Math.max(0, propagatedLightLevel - Math.max(1, opacityCached));
				if (lightLevel > targetLevel) {
					// it looks like another source propagated here, so re-propagate it
					if (increaseQueueLength >= increaseQueue.length) {
						increaseQueue = this.resizeIncreaseQueue();
					}
					increaseQueue[increaseQueueLength++] =
						((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
							| ((lightLevel & 0xFL) << (6 + 6 + 16))
							| (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
							| FLAG_RECHECK_LEVEL;
					continue;
				}
				final int emittedLight = PlatformHooks.get().getLightEmitted(blockState) & emittedMask;
				if (emittedLight != 0) {
					// re-propagate source
					// note: do not set recheck level, or else the propagation will fail
					if (increaseQueueLength >= increaseQueue.length) {
						increaseQueue = this.resizeIncreaseQueue();
					}
					increaseQueue[increaseQueueLength++] =
						((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
							| ((emittedLight & 0xFL) << (6 + 6 + 16))
							| (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
							| (FLAG_WRITE_LEVEL);
				}

				currentNibble.starlight$setData(offX & 15, offY & 127, offZ & 15, 0);
				this.postLightUpdate(offX, offY, offZ);

				if (targetLevel > 0) { // we actually need to propagate 0 just in case we find a neighbour...
					if (queueLength >= queue.length) {
						queue = this.resizeDecreaseQueue();
					}
					queue[queueLength++] =
						((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
							| ((targetLevel & 0xFL) << (6 + 6 + 16))
							| ((propagate.everythingButTheOppositeDirection) << (6 + 6 + 16 + 4));
					continue;
				}
				continue;
			}
		}

		// propagate sources we clobbered
		this.increaseQueueInitialLength = increaseQueueLength;
		this.performLightIncrease();
	}

	public void checkBlockEmittance(final int worldX, final int worldY, final int worldZ) {
		this.setupCaches(this.world, worldX, worldY, worldZ, true);
		try {
			// blocks can change opacity
			// blocks can change emitted light
			// blocks can change direction of propagation

			final int encodeOffset = this.coordinateOffset;
			final int emittedMask = this.emittedLightMask;

			final int currentLevel = this.getLightLevel(worldX, worldY, worldZ);
			final int blockState = this.getBlockState(worldX, worldY, worldZ);
			final int emittedLevel = PlatformHooks.get().getLightEmitted(blockState) & emittedMask;

			this.setLightLevel(worldX, worldY, worldZ, emittedLevel);
			// this accounts for change in emitted light that would cause an increase
			if (emittedLevel != 0) {
				this.appendToIncreaseQueue(
					((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
						| (emittedLevel & 0xFL) << (6 + 6 + 16)
						| (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
				);
			}
			// this also accounts for a change in emitted light that would cause a decrease
			// this also accounts for the change of direction of propagation (i.e old block was full transparent, new block is full opaque or vice versa)
			// as it checks all neighbours (even if current level is 0)
			this.appendToDecreaseQueue(
				((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
					| (currentLevel & 0xFL) << (6 + 6 + 16)
					| (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
				// always keep sided transparent false here, new block might be conditionally transparent which would
				// prevent us from decreasing sources in the directions where the new block is opaque
				// if it turns out we were wrong to de-propagate the source, the re-propagate logic WILL always
				// catch that and fix it.
			);
			// re-propagating neighbours (done by the decrease queue) will also account for opacity changes in this block

			this.performLightDecrease();
		} finally {
			this.destroyCaches();
		}
	}

	public void checkSkyEmittance(final int worldX, final int worldY, final int worldZ) {
		this.setupCaches(this.world, worldX, worldY, worldZ, true);
		try {
			// try and propagate from the above y
			// delay light set until after processing all sources to setup
			final int maxPropagationY = this.tryPropagateSkylight(this.world, worldX, worldY, worldZ);

			// maxPropagationY is now the highest block that could not be propagated to

			// remove all sources below that are 15
			final long propagateDirection = AxisDirection.POSITIVE_Y.everythingButThisDirection;
			final int encodeOffset = this.coordinateOffset;

			if (this.getLightLevel(worldX, maxPropagationY, worldZ) == 15) {
				final StarlightNibbleArray nibble = this.getNibbleFromCache(worldX >> 4, worldZ >> 4);
				for (int currY = maxPropagationY; currY >= 0; --currY) {
					if (nibble.starlight$getData(worldX & 15, currY & 127, worldZ & 15) != 15) {
						break;
					}

					this.appendToDecreaseQueue(
						((worldX + (worldZ << 6) + (currY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
							| (15L << (6 + 6 + 16))
							| (propagateDirection << (6 + 6 + 16 + 4))
						// do not set transparent blocks for the same reason we don't in the checkBlock method
					);

					nibble.starlight$setData(worldX & 15, currY & 127, worldZ & 15, 0);
				}
			}

			// inlined checkBlock

			// blocks can change opacity
			// blocks can change direction of propagation

			// same logic applies from BlockStarLightEngine#checkBlock

			final int currentLevel = this.getLightLevel(worldX, worldY, worldZ);

			if (currentLevel == 15) {
				// must re-propagate clobbered source
				this.appendToIncreaseQueue(
					((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
						| (currentLevel & 0xFL) << (6 + 6 + 16)
						| (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
						| FLAG_HAS_SIDED_TRANSPARENT_BLOCKS // don't know if the block is conditionally transparent
				);
			} else {
				this.setLightLevel(worldX, worldY, worldZ, 0);
			}

			this.appendToDecreaseQueue(
				((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
					| (currentLevel & 0xFL) << (6 + 6 + 16)
					| (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
			);

			this.performLightDecrease();
		} finally {
			this.destroyCaches();
		}
	}

	// delaying the light set is useful for block changes since they need to worry about initialising nibblearrays
	// while also queueing light at the same time (initialising nibblearrays might depend on nibbles above, so
	// clobbering the light values will result in broken propagation)
	private int tryPropagateSkylight(final StarlightWorld world, final int worldX, int startY, final int worldZ) {
		final int encodeOffset = this.coordinateOffset;
		final long propagateDirection = AxisDirection.POSITIVE_Y.everythingButThisDirection; // just don't check upwards.

		if (this.getLightLevel(worldX, startY + 1, worldZ) != 15) {
			return startY;
		}

		for (;startY >= 0; --startY) {
			int current = this.getBlockState(worldX, startY, worldZ);

			final int opacityIfCached = PlatformHooks.get().getLightBlock(current);
			// does light propagate from the top down?
			if (opacityIfCached != 0) {
				// we cannot propagate 15 through this
				break;
			}

			// most of the time it falls here.
			// add to propagate
			// light set delayed until we determine if this nibble section is null
			this.appendToIncreaseQueue(
				((worldX + (worldZ << 6) + (startY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
					| (15L << (6 + 6 + 16)) // we know we're at full lit here
					| (propagateDirection << (6 + 6 + 16 + 4))
			);

			this.setLightLevel(worldX, startY, worldZ, 15);
		}

		return startY;
	}

	public void initSkylight(final int chunkX, final int chunkZ) {
		this.setupCaches(this.world, (chunkX << 4) | 7, 64, (chunkZ << 4) | 7, true);
		try {
			// now setup sources
			final int worldChunkX = chunkX << 4;
			final int worldChunkZ = chunkZ << 4;

			for (int currZ = 0; currZ <= 15; ++currZ) {
				for (int currX = 0; currX <= 15; ++currX) {
					final int worldX = currX | worldChunkX;
					final int worldZ = currZ | worldChunkZ;
					this.tryPropagateSkylight(this.world, worldX, 127, worldZ);
				}
			}

			this.propagateNeighbourLevels(chunkX, chunkZ);

			this.performLightIncrease();
		} finally {
			this.destroyCaches();
		}
	}

	public void initBlockLight(final int chunkX, final int chunkZ) {
		this.setupCaches(this.world, (chunkX << 4) | 7, 64, (chunkZ << 4) | 7, true);
		try {
			if (this.world.starlight$hasGeneratedBlockLight()) {
				final StarlightChunk chunk = this.getChunkInCache(chunkX, chunkZ);
				final StarlightNibbleArray nibble = this.getNibbleFromCache(chunkX, chunkZ);
				final int encodeOffset = this.coordinateOffset;

				for (int x = 0; x <= 15; ++x) {
					for (int z = 0; z <= 15; ++z) {
						for (int y = 0; y <= 127; ++y) {
							final int blockState = chunk.starlight$getBlock(x, y, z);
							if (blockState == AIR_BLOCK_STATE) {
								continue;
							}
							final int emittedLight = PlatformHooks.get().getLightEmitted(blockState);

							if (emittedLight != 0) {
								nibble.starlight$setData(x, y, z, emittedLight);
								this.appendToIncreaseQueue(
									(((x | (chunkX << 4)) + ((z | (chunkZ << 4)) << 6) + (y << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
										| ((emittedLight & 0xFL) << (6 + 6 + 16))
										| ((long)ALL_DIRECTIONS_BITSET << (6 + 6 + 16 + 4))
										| FLAG_HAS_SIDED_TRANSPARENT_BLOCKS // don't know if the current block is transparent, must check.
								);
							}
						}
					}
				}
			}

			this.propagateNeighbourLevels(chunkX, chunkZ);

			this.performLightIncrease();
		} finally {
			this.destroyCaches();
		}
	}

	private void propagateNeighbourLevels(final int chunkX, final int chunkZ) {
		for (int currSectionY = (127 >> 4); currSectionY >= 0; --currSectionY) {
			for (final AxisDirection direction : ONLY_HORIZONTAL_DIRECTIONS) {
				final int neighbourOffX = direction.x;
				final int neighbourOffZ = direction.z;

				final StarlightNibbleArray neighbourNibble = this.getNibbleFromCache(chunkX + neighbourOffX,
					chunkZ + neighbourOffZ);

				if (neighbourNibble == null) {
					// can't pull from 0
					continue;
				}

				// neighbour chunk
				final int incX;
				final int incZ;
				final int startX;
				final int startZ;

				if (neighbourOffX != 0) {
					// x direction
					incX = 0;
					incZ = 1;

					if (direction.x < 0) {
						// negative
						startX = (chunkX << 4) - 1;
					} else {
						startX = (chunkX << 4) + 16;
					}
					startZ = chunkZ << 4;
				} else {
					// z direction
					incX = 1;
					incZ = 0;

					if (neighbourOffZ < 0) {
						// negative
						startZ = (chunkZ << 4) - 1;
					} else {
						startZ = (chunkZ << 4) + 16;
					}
					startX = chunkX << 4;
				}

				final long propagateDirection = 1L << direction.getOpposite().ordinal(); // we only want to check in this direction towards this chunk
				final int encodeOffset = this.coordinateOffset;

				for (int currY = currSectionY << 4, maxY = currY | 15; currY <= maxY; ++currY) {
					for (int i = 0, currX = startX, currZ = startZ; i < 16; ++i, currX += incX, currZ += incZ) {
						final int level = neighbourNibble.starlight$getData(currX & 15, currY & 127, currZ & 15);

						if (level <= 1) {
							// nothing to propagate
							continue;
						}

						this.appendToIncreaseQueue(
							((currX + (currZ << 6) + (currY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
								| ((level & 0xFL) << (6 + 6 + 16))
								| (propagateDirection << (6 + 6 + 16 + 4))
								| FLAG_HAS_SIDED_TRANSPARENT_BLOCKS // don't know if the current block is transparent, must check.
						);
					}
				}
			}
		}
	}
}
