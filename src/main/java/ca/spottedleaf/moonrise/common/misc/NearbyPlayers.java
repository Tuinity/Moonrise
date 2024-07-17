package ca.spottedleaf.moonrise.common.misc;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import ca.spottedleaf.moonrise.common.util.ChunkSystem;
import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickConstants;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public final class NearbyPlayers {

    public static enum NearbyMapType {
        GENERAL,
        GENERAL_SMALL,
        GENERAL_REALLY_SMALL,
        TICK_VIEW_DISTANCE,
        VIEW_DISTANCE,
        SPAWN_RANGE, // Moonrise - chunk tick iteration
    }

    private static final NearbyMapType[] MAP_TYPES = NearbyMapType.values();
    public static final int TOTAL_MAP_TYPES = MAP_TYPES.length;

    private static final int GENERAL_AREA_VIEW_DISTANCE = MoonriseConstants.MAX_VIEW_DISTANCE + 1;
    private static final int GENERAL_SMALL_VIEW_DISTANCE = 10;
    private static final int GENERAL_REALLY_SMALL_VIEW_DISTANCE = 3;

    public static final int GENERAL_AREA_VIEW_DISTANCE_BLOCKS = (GENERAL_AREA_VIEW_DISTANCE << 4);
    public static final int GENERAL_SMALL_AREA_VIEW_DISTANCE_BLOCKS = (GENERAL_SMALL_VIEW_DISTANCE << 4);
    public static final int GENERAL_REALLY_SMALL_AREA_VIEW_DISTANCE_BLOCKS = (GENERAL_REALLY_SMALL_VIEW_DISTANCE << 4);

    private final ServerLevel world;
    private final Reference2ReferenceOpenHashMap<ServerPlayer, TrackedPlayer[]> players = new Reference2ReferenceOpenHashMap<>();
    private final Long2ReferenceOpenHashMap<TrackedChunk> byChunk = new Long2ReferenceOpenHashMap<>();

    public NearbyPlayers(final ServerLevel world) {
        this.world = world;
    }

    public void addPlayer(final ServerPlayer player) {
        final TrackedPlayer[] newTrackers = new TrackedPlayer[TOTAL_MAP_TYPES];
        if (this.players.putIfAbsent(player, newTrackers) != null) {
            throw new IllegalStateException("Already have player " + player);
        }

        final ChunkPos chunk = player.chunkPosition();

        for (int i = 0; i < TOTAL_MAP_TYPES; ++i) {
            // use 0 for default, will be updated by tickPlayer
            (newTrackers[i] = new TrackedPlayer(player, MAP_TYPES[i])).add(chunk.x, chunk.z, 0);
        }

        // update view distances
        this.tickPlayer(player);
    }

    public void removePlayer(final ServerPlayer player) {
        final TrackedPlayer[] players = this.players.remove(player);
        if (players == null) {
            return; // May be called during teleportation before the player is actually placed
        }

        for (final TrackedPlayer tracker : players) {
            tracker.remove();
        }
    }

    public void tickPlayer(final ServerPlayer player) {
        final TrackedPlayer[] players = this.players.get(player);
        if (players == null) {
            throw new IllegalStateException("Don't have player " + player);
        }

        final ChunkPos chunk = player.chunkPosition();

        players[NearbyMapType.GENERAL.ordinal()].update(chunk.x, chunk.z, GENERAL_AREA_VIEW_DISTANCE);
        players[NearbyMapType.GENERAL_SMALL.ordinal()].update(chunk.x, chunk.z, GENERAL_SMALL_VIEW_DISTANCE);
        players[NearbyMapType.GENERAL_REALLY_SMALL.ordinal()].update(chunk.x, chunk.z, GENERAL_REALLY_SMALL_VIEW_DISTANCE);
        players[NearbyMapType.TICK_VIEW_DISTANCE.ordinal()].update(chunk.x, chunk.z, ChunkSystem.getTickViewDistance(player));
        players[NearbyMapType.VIEW_DISTANCE.ordinal()].update(chunk.x, chunk.z, ChunkSystem.getLoadViewDistance(player));
        players[NearbyMapType.SPAWN_RANGE.ordinal()].update(chunk.x, chunk.z, ChunkTickConstants.PLAYER_SPAWN_TRACK_RANGE); // Moonrise - chunk tick iteration
    }

    public TrackedChunk getChunk(final ChunkPos pos) {
        return this.byChunk.get(CoordinateUtils.getChunkKey(pos));
    }

    public TrackedChunk getChunk(final BlockPos pos) {
        return this.byChunk.get(CoordinateUtils.getChunkKey(pos));
    }

    public ReferenceList<ServerPlayer> getPlayers(final BlockPos pos, final NearbyMapType type) {
        final TrackedChunk chunk = this.byChunk.get(CoordinateUtils.getChunkKey(pos));

        return chunk == null ? null : chunk.players[type.ordinal()];
    }

    public ReferenceList<ServerPlayer> getPlayers(final ChunkPos pos, final NearbyMapType type) {
        final TrackedChunk chunk = this.byChunk.get(CoordinateUtils.getChunkKey(pos));

        return chunk == null ? null : chunk.players[type.ordinal()];
    }

    public ReferenceList<ServerPlayer> getPlayersByChunk(final int chunkX, final int chunkZ, final NearbyMapType type) {
        final TrackedChunk chunk = this.byChunk.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        return chunk == null ? null : chunk.players[type.ordinal()];
    }

    public ReferenceList<ServerPlayer> getPlayersByBlock(final int blockX, final int blockZ, final NearbyMapType type) {
        final TrackedChunk chunk = this.byChunk.get(CoordinateUtils.getChunkKey(blockX >> 4, blockZ >> 4));

        return chunk == null ? null : chunk.players[type.ordinal()];
    }

    public static final class TrackedChunk {

        private static final ServerPlayer[] EMPTY_PLAYERS_ARRAY = new ServerPlayer[0];

        private final ReferenceList<ServerPlayer>[] players = new ReferenceList[TOTAL_MAP_TYPES];
        private int nonEmptyLists;
        private long updateCount;

        public boolean isEmpty() {
            return this.nonEmptyLists == 0;
        }

        public long getUpdateCount() {
            return this.updateCount;
        }

        public ReferenceList<ServerPlayer> getPlayers(final NearbyMapType type) {
            return this.players[type.ordinal()];
        }

        public void addPlayer(final ServerPlayer player, final NearbyMapType type) {
            ++this.updateCount;

            final int idx = type.ordinal();
            final ReferenceList<ServerPlayer> list = this.players[idx];
            if (list == null) {
                ++this.nonEmptyLists;
                (this.players[idx] = new ReferenceList<>(EMPTY_PLAYERS_ARRAY)).add(player);
                return;
            }

            if (!list.add(player)) {
                throw new IllegalStateException("Already contains player " + player);
            }
        }

        public void removePlayer(final ServerPlayer player, final NearbyMapType type) {
            ++this.updateCount;

            final int idx = type.ordinal();
            final ReferenceList<ServerPlayer> list = this.players[idx];
            if (list == null) {
                throw new IllegalStateException("Does not contain player " + player);
            }

            if (!list.remove(player)) {
                throw new IllegalStateException("Does not contain player " + player);
            }

            if (list.size() == 0) {
                this.players[idx] = null;
                --this.nonEmptyLists;
            }
        }
    }

    private final class TrackedPlayer extends SingleUserAreaMap<ServerPlayer> {

        private final NearbyMapType type;

        public TrackedPlayer(final ServerPlayer player, final NearbyMapType type) {
            super(player);
            this.type = type;
        }

        @Override
        protected void addCallback(final ServerPlayer parameter, final int chunkX, final int chunkZ) {
            final long chunkKey = CoordinateUtils.getChunkKey(chunkX, chunkZ);

            NearbyPlayers.this.byChunk.computeIfAbsent(chunkKey, (final long keyInMap) -> {
                return new TrackedChunk();
            }).addPlayer(parameter, this.type);
        }

        @Override
        protected void removeCallback(final ServerPlayer parameter, final int chunkX, final int chunkZ) {
            final long chunkKey = CoordinateUtils.getChunkKey(chunkX, chunkZ);

            final TrackedChunk chunk = NearbyPlayers.this.byChunk.get(chunkKey);
            if (chunk == null) {
                throw new IllegalStateException("Chunk should exist at " + new ChunkPos(chunkKey));
            }

            chunk.removePlayer(parameter, this.type);

            if (chunk.isEmpty()) {
                NearbyPlayers.this.byChunk.remove(chunkKey);
            }
        }
    }
}
