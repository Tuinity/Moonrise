package ca.spottedleaf.moonrise.patches.chunk_system.player;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.common.misc.AllocatingRateLimiter;
import ca.spottedleaf.moonrise.common.misc.SingleUserAreaMap;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.util.ParallelSearchRadiusIteration;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import java.lang.invoke.VarHandle;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class RegionizedPlayerChunkLoader {

    public static final TicketType<Long> PLAYER_TICKET         = TicketType.create("chunk_system:player_ticket", Long::compareTo);
    public static final TicketType<Long> PLAYER_TICKET_DELAYED = TicketType.create("chunk_system:player_ticket_delayed", Long::compareTo, 5 * 20);

    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;

    public static final int GENERATED_TICKET_LEVEL = ChunkHolderManager.FULL_LOADED_TICKET_LEVEL;
    public static final int LOADED_TICKET_LEVEL = ChunkTaskScheduler.getTicketLevel(ChunkStatus.EMPTY);
    public static final int TICK_TICKET_LEVEL = ChunkHolderManager.ENTITY_TICKING_TICKET_LEVEL;

    public static final class ViewDistanceHolder {

        private volatile ViewDistances viewDistances;
        private static final VarHandle VIEW_DISTANCES_HANDLE = ConcurrentUtil.getVarHandle(ViewDistanceHolder.class, "viewDistances", ViewDistances.class);

        public ViewDistanceHolder() {
            VIEW_DISTANCES_HANDLE.setVolatile(this, new ViewDistances(-1, -1, -1));
        }

        public ViewDistances getViewDistances() {
            return (ViewDistances)VIEW_DISTANCES_HANDLE.getVolatile(this);
        }

        public ViewDistances compareAndExchangeViewDistance(final ViewDistances expect, final ViewDistances update) {
            return (ViewDistances)VIEW_DISTANCES_HANDLE.compareAndExchange(this, expect, update);
        }

        public void updateViewDistance(final Function<ViewDistances, ViewDistances> update) {
            int failures = 0;
            for (ViewDistances curr = this.getViewDistances();;) {
                for (int i = 0; i < failures; ++i) {
                    ConcurrentUtil.backoff();
                }

                if (curr == (curr = this.compareAndExchangeViewDistance(curr, update.apply(curr)))) {
                    return;
                }
                ++failures;
            }
        }

        public void setTickViewDistance(final int distance) {
            this.updateViewDistance((final ViewDistances param) -> {
                return param.setTickViewDistance(distance);
            });
        }

        public void setLoadViewDistance(final int distance) {
            this.updateViewDistance((final ViewDistances param) -> {
                return param.setLoadViewDistance(distance);
            });
        }

        public void setSendViewDistance(final int distance) {
            this.updateViewDistance((final ViewDistances param) -> {
                return param.setSendViewDistance(distance);
            });
        }

        public JsonObject toJson() {
            return this.getViewDistances().toJson();
        }
    }

    public static final record ViewDistances(
        int tickViewDistance,
        int loadViewDistance,
        int sendViewDistance
    ) {
        public ViewDistances setTickViewDistance(final int distance) {
            return new ViewDistances(distance, this.loadViewDistance, this.sendViewDistance);
        }

        public ViewDistances setLoadViewDistance(final int distance) {
            return new ViewDistances(this.tickViewDistance, distance, this.sendViewDistance);
        }

        public ViewDistances setSendViewDistance(final int distance) {
            return new ViewDistances(this.tickViewDistance, this.loadViewDistance, distance);
        }

        public JsonObject toJson() {
            final JsonObject ret = new JsonObject();

            ret.addProperty("tick-view-distance", this.tickViewDistance);
            ret.addProperty("load-view-distance", this.loadViewDistance);
            ret.addProperty("send-view-distance", this.sendViewDistance);

            return ret;
        }
    }

    public static int getAPITickViewDistance(final ServerPlayer player) {
        final ServerLevel level = player.serverLevel();
        final PlayerChunkLoaderData data = ((ChunkSystemServerPlayer)player).moonrise$getChunkLoader();
        if (data == null) {
            return ((ChunkSystemServerLevel)level).moonrise$getPlayerChunkLoader().getAPITickDistance();
        }
        return data.lastTickDistance;
    }

    public static int getAPIViewDistance(final ServerPlayer player) {
        final ServerLevel level = player.serverLevel();
        final PlayerChunkLoaderData data = ((ChunkSystemServerPlayer)player).moonrise$getChunkLoader();
        if (data == null) {
            return ((ChunkSystemServerLevel)level).moonrise$getPlayerChunkLoader().getAPIViewDistance();
        }
        // view distance = load distance + 1
        return data.lastLoadDistance - 1;
    }

    public static int getLoadViewDistance(final ServerPlayer player) {
        final ServerLevel level = player.serverLevel();
        final PlayerChunkLoaderData data = ((ChunkSystemServerPlayer)player).moonrise$getChunkLoader();
        if (data == null) {
            return ((ChunkSystemServerLevel)level).moonrise$getPlayerChunkLoader().getAPIViewDistance();
        }
        // view distance = load distance + 1
        return data.lastLoadDistance - 1;
    }

    public static int getAPISendViewDistance(final ServerPlayer player) {
        final ServerLevel level = player.serverLevel();
        final PlayerChunkLoaderData data = ((ChunkSystemServerPlayer)player).moonrise$getChunkLoader();
        if (data == null) {
            return ((ChunkSystemServerLevel)level).moonrise$getPlayerChunkLoader().getAPISendViewDistance();
        }
        return data.lastSendDistance;
    }

    private final ServerLevel world;

    public RegionizedPlayerChunkLoader(final ServerLevel world) {
        this.world = world;
    }

    public void addPlayer(final ServerPlayer player) {
        TickThread.ensureTickThread(player, "Cannot add player to player chunk loader async");
        if (!((ChunkSystemServerPlayer)player).moonrise$isRealPlayer()) {
            return;
        }

        if (((ChunkSystemServerPlayer)player).moonrise$getChunkLoader() != null) {
            throw new IllegalStateException("Player is already added to player chunk loader");
        }

        final PlayerChunkLoaderData loader = new PlayerChunkLoaderData(this.world, player);

        ((ChunkSystemServerPlayer)player).moonrise$setChunkLoader(loader);
        loader.add();
    }

    public void updatePlayer(final ServerPlayer player) {
        final PlayerChunkLoaderData loader = ((ChunkSystemServerPlayer)player).moonrise$getChunkLoader();
        if (loader != null) {
            loader.update();
            // update view distances for nearby players
            ((ChunkSystemServerLevel)loader.world).moonrise$getNearbyPlayers().tickPlayer(player);
        }
    }

    public void removePlayer(final ServerPlayer player) {
        TickThread.ensureTickThread(player, "Cannot remove player from player chunk loader async");
        if (!((ChunkSystemServerPlayer)player).moonrise$isRealPlayer()) {
            return;
        }

        final PlayerChunkLoaderData loader = ((ChunkSystemServerPlayer)player).moonrise$getChunkLoader();

        if (loader == null) {
            return;
        }

        loader.remove();
        ((ChunkSystemServerPlayer)player).moonrise$setChunkLoader(null);
    }

    public void setSendDistance(final int distance) {
        ((ChunkSystemServerLevel)this.world).moonrise$getViewDistanceHolder().setSendViewDistance(distance);
    }

    public void setLoadDistance(final int distance) {
        ((ChunkSystemServerLevel)this.world).moonrise$getViewDistanceHolder().setLoadViewDistance(distance);
    }

    public void setTickDistance(final int distance) {
        ((ChunkSystemServerLevel)this.world).moonrise$getViewDistanceHolder().setTickViewDistance(distance);
    }

    // Note: follow the player chunk loader so everything stays consistent...
    public int getAPITickDistance() {
        final ViewDistances distances = ((ChunkSystemServerLevel)this.world).moonrise$getViewDistanceHolder().getViewDistances();
        final int tickViewDistance = PlayerChunkLoaderData.getTickDistance(
                -1, distances.tickViewDistance,
                -1, distances.loadViewDistance
        );
        return tickViewDistance;
    }

    public int getAPIViewDistance() {
        final ViewDistances distances = ((ChunkSystemServerLevel)this.world).moonrise$getViewDistanceHolder().getViewDistances();
        final int tickViewDistance = PlayerChunkLoaderData.getTickDistance(
                -1, distances.tickViewDistance,
                -1, distances.loadViewDistance
        );
        final int loadDistance = PlayerChunkLoaderData.getLoadViewDistance(tickViewDistance, -1, distances.loadViewDistance);

        // loadDistance = api view distance + 1
        return loadDistance - 1;
    }

    public int getAPISendViewDistance() {
        final ViewDistances distances = ((ChunkSystemServerLevel)this.world).moonrise$getViewDistanceHolder().getViewDistances();
        final int tickViewDistance = PlayerChunkLoaderData.getTickDistance(
                -1, distances.tickViewDistance,
                -1, distances.loadViewDistance
        );
        final int loadDistance = PlayerChunkLoaderData.getLoadViewDistance(tickViewDistance, -1, distances.loadViewDistance);
        final int sendViewDistance = PlayerChunkLoaderData.getSendViewDistance(
                loadDistance, -1, -1, distances.sendViewDistance
        );

        return sendViewDistance;
    }

    public boolean isChunkSent(final ServerPlayer player, final int chunkX, final int chunkZ, final boolean borderOnly) {
        return borderOnly ? this.isChunkSentBorderOnly(player, chunkX, chunkZ) : this.isChunkSent(player, chunkX, chunkZ);
    }

    public boolean isChunkSent(final ServerPlayer player, final int chunkX, final int chunkZ) {
        final PlayerChunkLoaderData loader = ((ChunkSystemServerPlayer)player).moonrise$getChunkLoader();
        if (loader == null) {
            return false;
        }

        return loader.sentChunks.contains(CoordinateUtils.getChunkKey(chunkX, chunkZ));
    }

    public boolean isChunkSentBorderOnly(final ServerPlayer player, final int chunkX, final int chunkZ) {
        final PlayerChunkLoaderData loader = ((ChunkSystemServerPlayer)player).moonrise$getChunkLoader();
        if (loader == null) {
            return false;
        }

        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                if (!loader.sentChunks.contains(CoordinateUtils.getChunkKey(dx + chunkX, dz + chunkZ))) {
                    return true;
                }
            }
        }

        return false;
    }

    public void tick() {
        TickThread.ensureTickThread("Cannot tick player chunk loader async");
        long currTime = System.nanoTime();
        for (final ServerPlayer player : new java.util.ArrayList<>(this.world.players())) {
            final PlayerChunkLoaderData loader = ((ChunkSystemServerPlayer)player).moonrise$getChunkLoader();
            if (loader == null || loader.removed || loader.world != this.world) {
                // not our problem anymore
                continue;
            }
            loader.update(); // can't invoke plugin logic
            loader.updateQueues(currTime);
        }
    }

    public static final class PlayerChunkLoaderData {

        private static final AtomicLong ID_GENERATOR = new AtomicLong();
        private final long id = ID_GENERATOR.incrementAndGet();
        private final Long idBoxed = Long.valueOf(this.id);

        private static final long MAX_RATE = 10_000L;

        private final ServerPlayer player;
        private final ServerLevel world;

        private int lastChunkX = Integer.MIN_VALUE;
        private int lastChunkZ = Integer.MIN_VALUE;

        private int lastSendDistance = Integer.MIN_VALUE;
        private int lastLoadDistance = Integer.MIN_VALUE;
        private int lastTickDistance = Integer.MIN_VALUE;

        private int lastSentChunkCenterX = Integer.MIN_VALUE;
        private int lastSentChunkCenterZ = Integer.MIN_VALUE;

        private int lastSentChunkRadius = Integer.MIN_VALUE;
        private int lastSentSimulationDistance = Integer.MIN_VALUE;

        private boolean canGenerateChunks = true;

        private final ArrayDeque<ChunkHolderManager.TicketOperation<?, ?>> delayedTicketOps = new ArrayDeque<>();
        private final LongOpenHashSet sentChunks = new LongOpenHashSet();

        private static final byte CHUNK_TICKET_STAGE_NONE           = 0;
        private static final byte CHUNK_TICKET_STAGE_LOADING        = 1;
        private static final byte CHUNK_TICKET_STAGE_LOADED         = 2;
        private static final byte CHUNK_TICKET_STAGE_GENERATING     = 3;
        private static final byte CHUNK_TICKET_STAGE_GENERATED      = 4;
        private static final byte CHUNK_TICKET_STAGE_TICK           = 5;
        private static final int[] TICKET_STAGE_TO_LEVEL = new int[] {
            ChunkHolderManager.MAX_TICKET_LEVEL + 1,
            LOADED_TICKET_LEVEL,
            LOADED_TICKET_LEVEL,
            GENERATED_TICKET_LEVEL,
            GENERATED_TICKET_LEVEL,
            TICK_TICKET_LEVEL
        };
        private final Long2ByteOpenHashMap chunkTicketStage = new Long2ByteOpenHashMap();
        {
            this.chunkTicketStage.defaultReturnValue(CHUNK_TICKET_STAGE_NONE);
        }

        // rate limiting
        private static final long ALLOCATION_GRANULARITY = TimeUnit.SECONDS.toNanos(1L);
        private final AllocatingRateLimiter chunkSendLimiter = new AllocatingRateLimiter(ALLOCATION_GRANULARITY);
        private final AllocatingRateLimiter chunkLoadTicketLimiter = new AllocatingRateLimiter(ALLOCATION_GRANULARITY);
        private final AllocatingRateLimiter chunkGenerateTicketLimiter = new AllocatingRateLimiter(ALLOCATION_GRANULARITY);

        // queues
        private final LongComparator CLOSEST_MANHATTAN_DIST = (final long c1, final long c2) -> {
            final int c1x = CoordinateUtils.getChunkX(c1);
            final int c1z = CoordinateUtils.getChunkZ(c1);

            final int c2x = CoordinateUtils.getChunkX(c2);
            final int c2z = CoordinateUtils.getChunkZ(c2);

            final int centerX = PlayerChunkLoaderData.this.lastChunkX;
            final int centerZ = PlayerChunkLoaderData.this.lastChunkZ;

            return Integer.compare(
                Math.abs(c1x - centerX) + Math.abs(c1z - centerZ),
                Math.abs(c2x - centerX) + Math.abs(c2z - centerZ)
            );
        };
        private final LongHeapPriorityQueue sendQueue = new LongHeapPriorityQueue(CLOSEST_MANHATTAN_DIST);
        private final LongHeapPriorityQueue tickingQueue = new LongHeapPriorityQueue(CLOSEST_MANHATTAN_DIST);
        private final LongHeapPriorityQueue generatingQueue = new LongHeapPriorityQueue(CLOSEST_MANHATTAN_DIST);
        private final LongHeapPriorityQueue genQueue = new LongHeapPriorityQueue(CLOSEST_MANHATTAN_DIST);
        private final LongHeapPriorityQueue loadingQueue = new LongHeapPriorityQueue(CLOSEST_MANHATTAN_DIST);
        private final LongHeapPriorityQueue loadQueue = new LongHeapPriorityQueue(CLOSEST_MANHATTAN_DIST);

        private volatile boolean removed;

        public PlayerChunkLoaderData(final ServerLevel world, final ServerPlayer player) {
            this.world = world;
            this.player = player;
        }

        private void flushDelayedTicketOps() {
            if (this.delayedTicketOps.isEmpty()) {
                return;
            }
            ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager.performTicketUpdates(this.delayedTicketOps);
            this.delayedTicketOps.clear();
        }

        private void pushDelayedTicketOp(final ChunkHolderManager.TicketOperation<?, ?> op) {
            this.delayedTicketOps.addLast(op);
        }

        private void sendChunk(final int chunkX, final int chunkZ) {
            if (this.sentChunks.add(CoordinateUtils.getChunkKey(chunkX, chunkZ))) {
                ((ChunkSystemChunkHolder)((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager
                        .getChunkHolder(chunkX, chunkZ).vanillaChunkHolder).moonrise$addReceivedChunk(this.player);

                final LevelChunk chunk = ((ChunkSystemLevel)this.world).moonrise$getFullChunkIfLoaded(chunkX, chunkZ);

                PlatformHooks.get().onChunkWatch(this.world, chunk, this.player);
                PlayerChunkSender.sendChunk(this.player.connection, this.world, chunk);
                return;
            }
            throw new IllegalStateException();
        }

        private void sendUnloadChunk(final int chunkX, final int chunkZ) {
            if (!this.sentChunks.remove(CoordinateUtils.getChunkKey(chunkX, chunkZ))) {
                return;
            }
            this.sendUnloadChunkRaw(chunkX, chunkZ);
        }

        private void sendUnloadChunkRaw(final int chunkX, final int chunkZ) {
            PlatformHooks.get().onChunkUnWatch(this.world, new ChunkPos(chunkX, chunkZ), this.player);
            // Note: Check PlayerChunkSender#dropChunk for other logic
            // Note: drop isAlive() check so that chunks properly unload client-side when the player dies
            ((ChunkSystemChunkHolder)((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager
                .getChunkHolder(chunkX, chunkZ).vanillaChunkHolder).moonrise$removeReceivedChunk(this.player);
            this.player.connection.send(new ClientboundForgetLevelChunkPacket(new ChunkPos(chunkX, chunkZ)));
        }

        private final SingleUserAreaMap<PlayerChunkLoaderData> broadcastMap = new SingleUserAreaMap<>(this) {
            @Override
            protected void addCallback(final PlayerChunkLoaderData parameter, final int chunkX, final int chunkZ) {
                // do nothing, we only care about remove
            }

            @Override
            protected void removeCallback(final PlayerChunkLoaderData parameter, final int chunkX, final int chunkZ) {
                parameter.sendUnloadChunk(chunkX, chunkZ);
            }
        };
        private final SingleUserAreaMap<PlayerChunkLoaderData> loadTicketCleanup = new SingleUserAreaMap<>(this) {
            @Override
            protected void addCallback(final PlayerChunkLoaderData parameter, final int chunkX, final int chunkZ) {
                // do nothing, we only care about remove
            }

            @Override
            protected void removeCallback(final PlayerChunkLoaderData parameter, final int chunkX, final int chunkZ) {
                final long chunk = CoordinateUtils.getChunkKey(chunkX, chunkZ);
                final byte ticketStage = parameter.chunkTicketStage.remove(chunk);
                final int level = TICKET_STAGE_TO_LEVEL[ticketStage];
                if (level > ChunkHolderManager.MAX_TICKET_LEVEL) {
                    return;
                }

                parameter.pushDelayedTicketOp(ChunkHolderManager.TicketOperation.addAndRemove(
                        chunk,
                        PLAYER_TICKET_DELAYED, level, parameter.idBoxed,
                        PLAYER_TICKET, level, parameter.idBoxed
                ));
            }
        };
        private final SingleUserAreaMap<PlayerChunkLoaderData> tickMap = new SingleUserAreaMap<>(this) {
            @Override
            protected void addCallback(final PlayerChunkLoaderData parameter, final int chunkX, final int chunkZ) {
                // do nothing, we will detect ticking chunks when we try to load them
            }

            @Override
            protected void removeCallback(final PlayerChunkLoaderData parameter, final int chunkX, final int chunkZ) {
                final long chunk = CoordinateUtils.getChunkKey(chunkX, chunkZ);
                // note: by the time this is called, the tick cleanup should have ran - so, if the chunk is at
                // the tick stage it was deemed in range for loading. Thus, we need to move it to generated
                if (!parameter.chunkTicketStage.replace(chunk, CHUNK_TICKET_STAGE_TICK, CHUNK_TICKET_STAGE_GENERATED)) {
                    return;
                }

                // Since we are possibly downgrading the ticket level, we add the delayed unload ticket so that
                // the level is kept for a short period of time
                parameter.pushDelayedTicketOp(ChunkHolderManager.TicketOperation.addAndRemove(
                        chunk,
                        PLAYER_TICKET_DELAYED, TICK_TICKET_LEVEL, parameter.idBoxed,
                        PLAYER_TICKET, TICK_TICKET_LEVEL, parameter.idBoxed
                ));
                // keep chunk at new generated level
                parameter.pushDelayedTicketOp(ChunkHolderManager.TicketOperation.addOp(
                        chunk, PLAYER_TICKET, GENERATED_TICKET_LEVEL, parameter.idBoxed
                ));
            }
        };

        private static boolean wantChunkLoaded(final int centerX, final int centerZ, final int chunkX, final int chunkZ,
                                               final int sendRadius) {
            // expect sendRadius to be = 1 + target viewable radius
            return ChunkTrackingView.isWithinDistance(centerX, centerZ, sendRadius, chunkX, chunkZ, true);
        }

        private static int getClientViewDistance(final ServerPlayer player) {
            final Integer vd = player.requestedViewDistance();
            return vd == null ? -1 : Math.max(0, vd.intValue());
        }

        private static int getTickDistance(final int playerTickViewDistance, final int worldTickViewDistance,
                                           final int playerLoadViewDistance, final int worldLoadViewDistance) {
            return Math.min(
                    playerTickViewDistance < 0 ? worldTickViewDistance : playerTickViewDistance,
                    playerLoadViewDistance < 0 ? worldLoadViewDistance : playerLoadViewDistance
            );
        }

        private static int getLoadViewDistance(final int tickViewDistance, final int playerLoadViewDistance,
                                               final int worldLoadViewDistance) {
            return Math.max(tickViewDistance + 1, playerLoadViewDistance < 0 ? worldLoadViewDistance : playerLoadViewDistance);
        }

        private static int getSendViewDistance(final int loadViewDistance, final int clientViewDistance,
                                               final int playerSendViewDistance, final int worldSendViewDistance) {
            return Math.min(
                loadViewDistance - 1,
                playerSendViewDistance < 0 ? (!PlatformHooks.get().configAutoConfigSendDistance() || clientViewDistance < 0 ? (worldSendViewDistance < 0 ? (loadViewDistance - 1) : worldSendViewDistance) : clientViewDistance + 1) : playerSendViewDistance
            );
        }

        private Packet<?> updateClientChunkRadius(final int radius) {
            this.lastSentChunkRadius = radius;
            return new ClientboundSetChunkCacheRadiusPacket(radius);
        }

        private Packet<?> updateClientSimulationDistance(final int distance) {
            this.lastSentSimulationDistance = distance;
            return new ClientboundSetSimulationDistancePacket(distance);
        }

        private Packet<?> updateClientChunkCenter(final int chunkX, final int chunkZ) {
            this.lastSentChunkCenterX = chunkX;
            this.lastSentChunkCenterZ = chunkZ;
            return new ClientboundSetChunkCacheCenterPacket(chunkX, chunkZ);
        }

        private boolean canPlayerGenerateChunks() {
            return !this.player.isSpectator() || this.world.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
        }

        private double getMaxChunkLoadRate() {
            final double configRate = PlatformHooks.get().configPlayerMaxLoadRate();

            return configRate <= 0.0 || configRate > (double)MAX_RATE ? (double)MAX_RATE : Math.max(1.0, configRate);
        }

        private double getMaxChunkGenRate() {
            final double configRate = PlatformHooks.get().configPlayerMaxGenRate();

            return configRate <= 0.0 || configRate > (double)MAX_RATE ? (double)MAX_RATE : Math.max(1.0, configRate);
        }

        private double getMaxChunkSendRate() {
            final double configRate = PlatformHooks.get().configPlayerMaxSendRate();

            return configRate <= 0.0 || configRate > (double)MAX_RATE ? (double)MAX_RATE : Math.max(1.0, configRate);
        }

        private long getMaxChunkLoads() {
            final long radiusChunks = (2L * this.lastLoadDistance + 1L) * (2L * this.lastLoadDistance + 1L);
            long configLimit = (long)PlatformHooks.get().configPlayerMaxConcurrentLoads();
            if (configLimit == 0L) {
                // by default, only allow 1/5th of the chunks in the view distance to be concurrently active
                configLimit = Math.max(5L, radiusChunks / 5L);
            } else if (configLimit < 0L) {
                configLimit = Integer.MAX_VALUE;
            } // else: use the value configured
            configLimit = configLimit - this.loadingQueue.size();

            return configLimit;
        }

        private long getMaxChunkGenerates() {
            final long radiusChunks = (2L * this.lastLoadDistance + 1L) * (2L * this.lastLoadDistance + 1L);
            long configLimit = (long)PlatformHooks.get().configPlayerMaxConcurrentGens();
            if (configLimit == 0L) {
                // by default, only allow 1/5th of the chunks in the view distance to be concurrently active
                configLimit = Math.max(5L, radiusChunks / 5L);
            } else if (configLimit < 0L) {
                configLimit = Integer.MAX_VALUE;
            } // else: use the value configured
            configLimit = configLimit - this.generatingQueue.size();

            return configLimit;
        }

        private boolean wantChunkSent(final int chunkX, final int chunkZ) {
            final int dx = this.lastChunkX - chunkX;
            final int dz = this.lastChunkZ - chunkZ;
            return (Math.max(Math.abs(dx), Math.abs(dz)) <= (this.lastSendDistance + 1)) && wantChunkLoaded(
                this.lastChunkX, this.lastChunkZ, chunkX, chunkZ, this.lastSendDistance
            );
        }

        private boolean wantChunkTicked(final int chunkX, final int chunkZ) {
            final int dx = this.lastChunkX - chunkX;
            final int dz = this.lastChunkZ - chunkZ;
            return Math.max(Math.abs(dx), Math.abs(dz)) <= this.lastTickDistance;
        }

        private boolean areNeighboursGenerated(final int chunkX, final int chunkZ, final int radius) {
            for (int dz = -radius; dz <= radius; ++dz) {
                for (int dx = -radius; dx <= radius; ++dx) {
                    if ((dx | dz) == 0) {
                        continue;
                    }

                    final long neighbour = CoordinateUtils.getChunkKey(dx + chunkX, dz + chunkZ);
                    final byte stage = this.chunkTicketStage.get(neighbour);

                    if (stage != CHUNK_TICKET_STAGE_GENERATED && stage != CHUNK_TICKET_STAGE_TICK) {
                        return false;
                    }
                }
            }

            return true;
        }

        void updateQueues(final long time) {
            TickThread.ensureTickThread(this.player, "Cannot tick player chunk loader async");
            if (this.removed) {
                throw new IllegalStateException("Ticking removed player chunk loader");
            }
            // update rate limits
            final double loadRate = this.getMaxChunkLoadRate();
            final double genRate = this.getMaxChunkGenRate();
            final double sendRate = this.getMaxChunkSendRate();

            this.chunkLoadTicketLimiter.tickAllocation(time, loadRate, loadRate);
            this.chunkGenerateTicketLimiter.tickAllocation(time, genRate, genRate);
            this.chunkSendLimiter.tickAllocation(time, sendRate, sendRate);

            // try to progress chunk loads
            while (!this.loadingQueue.isEmpty()) {
                final long pendingLoadChunk = this.loadingQueue.firstLong();
                final int pendingChunkX = CoordinateUtils.getChunkX(pendingLoadChunk);
                final int pendingChunkZ = CoordinateUtils.getChunkZ(pendingLoadChunk);
                final ChunkAccess pending = ((ChunkSystemLevel)this.world).moonrise$getAnyChunkIfLoaded(pendingChunkX, pendingChunkZ);
                if (pending == null) {
                    // nothing to do here
                    break;
                }
                // chunk has loaded, so we can take it out of the queue
                this.loadingQueue.dequeueLong();

                // try to move to generate queue
                final byte prev = this.chunkTicketStage.put(pendingLoadChunk, CHUNK_TICKET_STAGE_LOADED);
                if (prev != CHUNK_TICKET_STAGE_LOADING) {
                    throw new IllegalStateException("Previous state should be " + CHUNK_TICKET_STAGE_LOADING + ", not " + prev);
                }

                if (this.canGenerateChunks || this.isLoadedChunkGeneratable(pending)) {
                    this.genQueue.enqueue(pendingLoadChunk);
                } // else: don't want to generate, so just leave it loaded
            }

            // try to push more chunk loads
            final long maxLoads = Math.max(0L, Math.min(MAX_RATE, Math.min(this.loadQueue.size(), this.getMaxChunkLoads())));
            final int maxLoadsThisTick = (int)this.chunkLoadTicketLimiter.takeAllocation(time, loadRate, maxLoads);
            if (maxLoadsThisTick > 0) {
                final LongArrayList chunks = new LongArrayList(maxLoadsThisTick);
                for (int i = 0; i < maxLoadsThisTick; ++i) {
                    final long chunk = this.loadQueue.dequeueLong();
                    final byte prev = this.chunkTicketStage.put(chunk, CHUNK_TICKET_STAGE_LOADING);
                    if (prev != CHUNK_TICKET_STAGE_NONE) {
                        throw new IllegalStateException("Previous state should be " + CHUNK_TICKET_STAGE_NONE + ", not " + prev);
                    }
                    this.pushDelayedTicketOp(
                        ChunkHolderManager.TicketOperation.addOp(
                            chunk,
                            PLAYER_TICKET, LOADED_TICKET_LEVEL, this.idBoxed
                        )
                    );
                    chunks.add(chunk);
                    this.loadingQueue.enqueue(chunk);
                }

                // here we need to flush tickets, as scheduleChunkLoad requires tickets to be propagated with addTicket = false
                this.flushDelayedTicketOps();
                // we only need to call scheduleChunkLoad because the loaded ticket level is not enough to start the chunk
                // load - only generate ticket levels start anything, but they start generation...
                // propagate levels
                // Note: this CAN call plugin logic, so it is VITAL that our bookkeeping logic is completely done by the time this is invoked
                ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates();

                if (this.removed) {
                    // process ticket updates may invoke plugin logic, which may remove this player
                    return;
                }

                for (int i = 0; i < maxLoadsThisTick; ++i) {
                    final long queuedLoadChunk = chunks.getLong(i);
                    final int queuedChunkX = CoordinateUtils.getChunkX(queuedLoadChunk);
                    final int queuedChunkZ = CoordinateUtils.getChunkZ(queuedLoadChunk);
                    ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().scheduleChunkLoad(
                        queuedChunkX, queuedChunkZ, ChunkStatus.EMPTY, false, Priority.NORMAL, null
                    );
                    if (this.removed) {
                        return;
                    }
                }
            }

            // try to progress chunk generations
            while (!this.generatingQueue.isEmpty()) {
                final long pendingGenChunk = this.generatingQueue.firstLong();
                final int pendingChunkX = CoordinateUtils.getChunkX(pendingGenChunk);
                final int pendingChunkZ = CoordinateUtils.getChunkZ(pendingGenChunk);
                final LevelChunk pending = ((ChunkSystemLevel)this.world).moonrise$getFullChunkIfLoaded(pendingChunkX, pendingChunkZ);
                if (pending == null) {
                    // nothing to do here
                    break;
                }

                // chunk has generated, so we can take it out of queue
                this.generatingQueue.dequeueLong();

                final byte prev = this.chunkTicketStage.put(pendingGenChunk, CHUNK_TICKET_STAGE_GENERATED);
                if (prev != CHUNK_TICKET_STAGE_GENERATING) {
                    throw new IllegalStateException("Previous state should be " + CHUNK_TICKET_STAGE_GENERATING + ", not " + prev);
                }

                // try to move to send queue
                if (this.wantChunkSent(pendingChunkX, pendingChunkZ)) {
                    this.sendQueue.enqueue(pendingGenChunk);
                }
                // try to move to tick queue
                if (this.wantChunkTicked(pendingChunkX, pendingChunkZ)) {
                    this.tickingQueue.enqueue(pendingGenChunk);
                }
            }

            // try to push more chunk generations
            final long maxGens = Math.max(0L, Math.min(MAX_RATE, Math.min(this.genQueue.size(), this.getMaxChunkGenerates())));
            // preview the allocations, as we may not actually utilise all of them
            final long maxGensThisTick = this.chunkGenerateTicketLimiter.previewAllocation(time, genRate, maxGens);
            long ratedGensThisTick = 0L;
            while (!this.genQueue.isEmpty()) {
                final long chunkKey = this.genQueue.firstLong();
                final int chunkX = CoordinateUtils.getChunkX(chunkKey);
                final int chunkZ = CoordinateUtils.getChunkZ(chunkKey);
                final ChunkAccess chunk = ((ChunkSystemLevel)this.world).moonrise$getAnyChunkIfLoaded(chunkX, chunkZ);
                if (chunk.getPersistedStatus() != ChunkStatus.FULL) {
                    // only rate limit actual generations
                    if ((ratedGensThisTick + 1L) > maxGensThisTick) {
                        break;
                    }
                    ++ratedGensThisTick;
                }

                this.genQueue.dequeueLong();

                final byte prev = this.chunkTicketStage.put(chunkKey, CHUNK_TICKET_STAGE_GENERATING);
                if (prev != CHUNK_TICKET_STAGE_LOADED) {
                    throw new IllegalStateException("Previous state should be " + CHUNK_TICKET_STAGE_LOADED + ", not " + prev);
                }
                this.pushDelayedTicketOp(
                    ChunkHolderManager.TicketOperation.addAndRemove(
                            chunkKey,
                            PLAYER_TICKET, GENERATED_TICKET_LEVEL, this.idBoxed,
                            PLAYER_TICKET, LOADED_TICKET_LEVEL, this.idBoxed
                    )
                );
                this.generatingQueue.enqueue(chunkKey);
            }
            // take the allocations we actually used
            this.chunkGenerateTicketLimiter.takeAllocation(time, genRate, ratedGensThisTick);

            // try to pull ticking chunks
            while (!this.tickingQueue.isEmpty()) {
                final long pendingTicking = this.tickingQueue.firstLong();
                final int pendingChunkX = CoordinateUtils.getChunkX(pendingTicking);
                final int pendingChunkZ = CoordinateUtils.getChunkZ(pendingTicking);

                if (!this.areNeighboursGenerated(pendingChunkX, pendingChunkZ,
                    ChunkHolderManager.FULL_LOADED_TICKET_LEVEL - ChunkHolderManager.ENTITY_TICKING_TICKET_LEVEL)) {
                    break;
                }

                // only gets here if all neighbours were marked as generated or ticking themselves
                this.tickingQueue.dequeueLong();
                this.pushDelayedTicketOp(
                    ChunkHolderManager.TicketOperation.addAndRemove(
                            pendingTicking,
                            PLAYER_TICKET, TICK_TICKET_LEVEL, this.idBoxed,
                            PLAYER_TICKET, GENERATED_TICKET_LEVEL, this.idBoxed
                    )
                );
                // note: there is no queue to add after ticking
                final byte prev = this.chunkTicketStage.put(pendingTicking, CHUNK_TICKET_STAGE_TICK);
                if (prev != CHUNK_TICKET_STAGE_GENERATED) {
                    throw new IllegalStateException("Previous state should be " + CHUNK_TICKET_STAGE_GENERATED + ", not " + prev);
                }
            }

            // try to pull sending chunks
            final long maxSends = Math.max(0L, Math.min(MAX_RATE, Integer.MAX_VALUE)); // note: no logic to track concurrent sends
            final int maxSendsThisTick = Math.min((int)this.chunkSendLimiter.takeAllocation(time, sendRate, maxSends), this.sendQueue.size());
            // we do not return sends that we took from the allocation back because we want to limit the max send rate, not target it
            for (int i = 0; i < maxSendsThisTick; ++i) {
                final long pendingSend = this.sendQueue.firstLong();
                final int pendingSendX = CoordinateUtils.getChunkX(pendingSend);
                final int pendingSendZ = CoordinateUtils.getChunkZ(pendingSend);
                final LevelChunk chunk = ((ChunkSystemLevel)this.world).moonrise$getFullChunkIfLoaded(pendingSendX, pendingSendZ);
                if (!this.areNeighboursGenerated(pendingSendX, pendingSendZ, 1) || !TickThread.isTickThreadFor(this.world, pendingSendX, pendingSendZ)) {
                    // nothing to do
                    // the target chunk may not be owned by this region, but this should be resolved in the future
                    break;
                }
                if (!((ChunkSystemLevelChunk)chunk).moonrise$isPostProcessingDone()) {
                    // not yet post-processed, need to do this so that tile entities can properly be sent to clients
                    chunk.postProcessGeneration(this.world);
                    // check if there was any recursive action
                    if (this.removed || this.sendQueue.isEmpty() || this.sendQueue.firstLong() != pendingSend) {
                        return;
                    } // else: good to dequeue and send, fall through
                }
                this.sendQueue.dequeueLong();

                this.sendChunk(pendingSendX, pendingSendZ);

                if (this.removed) {
                    // sendChunk may invoke plugin logic
                    return;
                }
            }

            this.flushDelayedTicketOps();
        }

        void add() {
            TickThread.ensureTickThread(this.player, "Cannot add player asynchronously");
            if (this.removed) {
                throw new IllegalStateException("Adding removed player chunk loader");
            }
            final ViewDistances playerDistances = ((ChunkSystemServerPlayer)this.player).moonrise$getViewDistanceHolder().getViewDistances();
            final ViewDistances worldDistances = ((ChunkSystemServerLevel)this.world).moonrise$getViewDistanceHolder().getViewDistances();
            final int chunkX = this.player.chunkPosition().x;
            final int chunkZ = this.player.chunkPosition().z;

            final int tickViewDistance = getTickDistance(
                    playerDistances.tickViewDistance, worldDistances.tickViewDistance,
                    playerDistances.loadViewDistance, worldDistances.loadViewDistance
            );
            // load view cannot be less-than tick view + 1
            final int loadViewDistance = getLoadViewDistance(tickViewDistance, playerDistances.loadViewDistance, worldDistances.loadViewDistance);
            // send view cannot be greater-than load view
            final int clientViewDistance = getClientViewDistance(this.player);
            final int sendViewDistance = getSendViewDistance(loadViewDistance, clientViewDistance, playerDistances.sendViewDistance, worldDistances.sendViewDistance);

            // send view distances
            this.player.connection.send(this.updateClientChunkRadius(sendViewDistance));
            this.player.connection.send(this.updateClientSimulationDistance(tickViewDistance));

            // add to distance maps
            this.broadcastMap.add(chunkX, chunkZ, sendViewDistance + 1);
            this.loadTicketCleanup.add(chunkX, chunkZ, loadViewDistance + 1);
            this.tickMap.add(chunkX, chunkZ, tickViewDistance);

            // update chunk center
            this.player.connection.send(this.updateClientChunkCenter(chunkX, chunkZ));

            // reset limiters, they will start at a zero allocation
            final long time = System.nanoTime();
            this.chunkLoadTicketLimiter.reset(time);
            this.chunkGenerateTicketLimiter.reset(time);
            this.chunkSendLimiter.reset(time);

            // now we can update
            this.update();
        }

        private boolean isLoadedChunkGeneratable(final int chunkX, final int chunkZ) {
            return this.isLoadedChunkGeneratable(((ChunkSystemLevel)this.world).moonrise$getAnyChunkIfLoaded(chunkX, chunkZ));
        }

        private boolean isLoadedChunkGeneratable(final ChunkAccess chunkAccess) {
            final BelowZeroRetrogen belowZeroRetrogen;
            // see PortalForcer#findPortalAround
            return chunkAccess != null && (
                chunkAccess.getPersistedStatus() == ChunkStatus.FULL ||
                    ((belowZeroRetrogen = chunkAccess.getBelowZeroRetrogen()) != null && belowZeroRetrogen.targetStatus().isOrAfter(ChunkStatus.SPAWN))
            );
        }

        void update() {
            TickThread.ensureTickThread(this.player, "Cannot update player asynchronously");
            if (this.removed) {
                throw new IllegalStateException("Updating removed player chunk loader");
            }
            final ViewDistances playerDistances = ((ChunkSystemServerPlayer)this.player).moonrise$getViewDistanceHolder().getViewDistances();
            final ViewDistances worldDistances = ((ChunkSystemServerLevel)this.world).moonrise$getViewDistanceHolder().getViewDistances();

            final int tickViewDistance = getTickDistance(
                    playerDistances.tickViewDistance, worldDistances.tickViewDistance,
                    playerDistances.loadViewDistance, worldDistances.loadViewDistance
            );
            // load view cannot be less-than tick view + 1
            final int loadViewDistance = getLoadViewDistance(tickViewDistance, playerDistances.loadViewDistance, worldDistances.loadViewDistance);
            // send view cannot be greater-than load view
            final int clientViewDistance = getClientViewDistance(this.player);
            final int sendViewDistance = getSendViewDistance(loadViewDistance, clientViewDistance, playerDistances.sendViewDistance, worldDistances.sendViewDistance);

            final ChunkPos playerPos = this.player.chunkPosition();
            final boolean canGenerateChunks = this.canPlayerGenerateChunks();
            final int currentChunkX = playerPos.x;
            final int currentChunkZ = playerPos.z;

            final int prevChunkX = this.lastChunkX;
            final int prevChunkZ = this.lastChunkZ;

            if (
                // has view distance stayed the same?
                sendViewDistance == this.lastSendDistance
                    && loadViewDistance == this.lastLoadDistance
                    && tickViewDistance == this.lastTickDistance

                    // has our chunk stayed the same?
                    && prevChunkX == currentChunkX
                    && prevChunkZ == currentChunkZ

                    // can we still generate chunks?
                    && this.canGenerateChunks == canGenerateChunks
            ) {
                // nothing we care about changed, so we're not re-calculating
                return;
            }

            // update distance maps
            this.broadcastMap.update(currentChunkX, currentChunkZ, sendViewDistance + 1);
            this.loadTicketCleanup.update(currentChunkX, currentChunkZ, loadViewDistance + 1);
            this.tickMap.update(currentChunkX, currentChunkZ, tickViewDistance);
            if (sendViewDistance > loadViewDistance || tickViewDistance > loadViewDistance) {
                throw new IllegalStateException();
            }

            // update VDs for client
            // this should be after the distance map updates, as they will send unload packets
            if (this.lastSentChunkRadius != sendViewDistance) {
                this.player.connection.send(this.updateClientChunkRadius(sendViewDistance));
            }
            if (this.lastSentSimulationDistance != tickViewDistance) {
                this.player.connection.send(this.updateClientSimulationDistance(tickViewDistance));
            }

            this.sendQueue.clear();
            this.tickingQueue.clear();
            this.generatingQueue.clear();
            this.genQueue.clear();
            this.loadingQueue.clear();
            this.loadQueue.clear();

            this.lastChunkX = currentChunkX;
            this.lastChunkZ = currentChunkZ;
            this.lastSendDistance = sendViewDistance;
            this.lastLoadDistance = loadViewDistance;
            this.lastTickDistance = tickViewDistance;
            this.canGenerateChunks = canGenerateChunks;

            // +1 since we need to load chunks +1 around the load view distance...
            final long[] toIterate = ParallelSearchRadiusIteration.getSearchIteration(loadViewDistance + 1);
            // the iteration order is by increasing manhattan distance - so, we do NOT need to
            // sort anything in the queue!
            for (final long deltaChunk : toIterate) {
                final int dx = CoordinateUtils.getChunkX(deltaChunk);
                final int dz = CoordinateUtils.getChunkZ(deltaChunk);
                final int chunkX = dx + currentChunkX;
                final int chunkZ = dz + currentChunkZ;
                final long chunk = CoordinateUtils.getChunkKey(chunkX, chunkZ);
                final int squareDistance = Math.max(Math.abs(dx), Math.abs(dz));
                final int manhattanDistance = Math.abs(dx) + Math.abs(dz);

                // since chunk sending is not by radius alone, we need an extra check here to account for
                // everything <= sendDistance
                // Note: Vanilla may want to send chunks outside the send view distance, so we do need
                // the dist <= view check
                final boolean sendChunk = (squareDistance <= (sendViewDistance + 1))
                    && wantChunkLoaded(currentChunkX, currentChunkZ, chunkX, chunkZ, sendViewDistance);
                final boolean sentChunk = sendChunk ? this.sentChunks.contains(chunk) : this.sentChunks.remove(chunk);

                if (!sendChunk && sentChunk) {
                    // have sent the chunk, but don't want it anymore
                    // unload it now
                    this.sendUnloadChunkRaw(chunkX, chunkZ);
                }

                final byte stage = this.chunkTicketStage.get(chunk);
                switch (stage) {
                    case CHUNK_TICKET_STAGE_NONE: {
                        // we want the chunk to be at least loaded
                        this.loadQueue.enqueue(chunk);
                        break;
                    }
                    case CHUNK_TICKET_STAGE_LOADING: {
                        this.loadingQueue.enqueue(chunk);
                        break;
                    }
                    case CHUNK_TICKET_STAGE_LOADED: {
                        if (canGenerateChunks || this.isLoadedChunkGeneratable(chunkX, chunkZ)) {
                            this.genQueue.enqueue(chunk);
                        }
                        break;
                    }
                    case CHUNK_TICKET_STAGE_GENERATING: {
                        this.generatingQueue.enqueue(chunk);
                        break;
                    }
                    case CHUNK_TICKET_STAGE_GENERATED: {
                        if (sendChunk && !sentChunk) {
                            this.sendQueue.enqueue(chunk);
                        }
                        if (squareDistance <= tickViewDistance) {
                            this.tickingQueue.enqueue(chunk);
                        }
                        break;
                    }
                    case CHUNK_TICKET_STAGE_TICK: {
                        if (sendChunk && !sentChunk) {
                            this.sendQueue.enqueue(chunk);
                        }
                        break;
                    }
                    default: {
                        throw new IllegalStateException("Unknown stage: " + stage);
                    }
                }
            }

            // update the chunk center
            // this must be done last so that the client does not ignore any of our unload chunk packets above
            if (this.lastSentChunkCenterX != currentChunkX || this.lastSentChunkCenterZ != currentChunkZ) {
                this.player.connection.send(this.updateClientChunkCenter(currentChunkX, currentChunkZ));
            }

            this.flushDelayedTicketOps();
        }

        void remove() {
            TickThread.ensureTickThread(this.player, "Cannot add player asynchronously");
            if (this.removed) {
                throw new IllegalStateException("Removing removed player chunk loader");
            }
            this.removed = true;
            // sends the chunk unload packets
            this.broadcastMap.remove();
            // cleans up loading/generating tickets
            this.loadTicketCleanup.remove();
            // cleans up ticking tickets
            this.tickMap.remove();

            // purge queues
            this.sendQueue.clear();
            this.tickingQueue.clear();
            this.generatingQueue.clear();
            this.genQueue.clear();
            this.loadingQueue.clear();
            this.loadQueue.clear();

            // flush ticket changes
            this.flushDelayedTicketOps();

            // now all tickets should be removed, which is all of our external state
        }

        public LongOpenHashSet getSentChunksRaw() {
            return this.sentChunks;
        }
    }
}
