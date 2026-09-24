package ca.spottedleaf.moonrise.patches.command;

import ca.spottedleaf.common.time.TickData;
import ca.spottedleaf.common.util.DecimalFormats;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.util.ConfigHolder;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.JsonUtil;
import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketType;
import ca.spottedleaf.moonrise.patches.profiler.client.ProfilerMinecraft;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightLightingProvider;
import ca.spottedleaf.moonrise.patches.tick_loop.TickLoopMinecraftServer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import javax.management.MBeanServer;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MoonriseCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.0");
    });

    private static final TextColor LIGHT_RED = TextColor.fromRgb(0xFF8080);

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("moonrise").requires((final CommandSourceStack src) -> {
                return Commands.hasPermission(Commands.LEVEL_ADMINS).test(src) || !(src.getServer() instanceof DedicatedServer);
            }).then(literal("holderinfo")
                .executes(MoonriseCommand::holderInfo)
            ).then(literal("chunkinfo")
                .executes(MoonriseCommand::chunkInfo)
            ).then(literal("reload")
                .executes(MoonriseCommand::reload)
            ).then(literal("relight")
                .executes((final CommandContext<CommandSourceStack> ctx) -> {
                    return MoonriseCommand.relight(ctx, 10);
                })
                .then(argument("radius", IntegerArgumentType.integer(0, MoonriseConstants.MAX_VIEW_DISTANCE))
                    .executes((final CommandContext<CommandSourceStack> ctx) -> {
                        return MoonriseCommand.relight(ctx, IntegerArgumentType.getInteger(ctx, "radius"));
                    })
                )
            ).then(literal("debug")
                .then(literal("chunks")
                    .executes(MoonriseCommand::debugChunks)
                )
            ).then(literal("tps")
                .executes(MoonriseCommand::tps)
            ).then(literal("genarea")
                .then(
                    argument("world", DimensionArgument.dimension())
                        .then(
                            argument("center", ColumnPosArgument.columnPos())
                                .then(
                                    argument("radius", IntegerArgumentType.integer(0, Integer.MAX_VALUE))
                                        .then(
                                            argument("max_loaded", IntegerArgumentType.integer(4, Integer.MAX_VALUE))
                                                .executes((final CommandContext<CommandSourceStack> ctx) -> {
                                                    return MoonriseCommand.genArea(
                                                        ctx,
                                                        DimensionArgument.getDimension(ctx, "world"),
                                                        ColumnPosArgument.getColumnPos(ctx, "center"),
                                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                                        IntegerArgumentType.getInteger(ctx, "max_loaded")
                                                    );
                                                })
                                        )
                                )
                        )
                )
            ).then(literal("vm")
                .then(literal("gc")
                    .executes(MoonriseCommand::gc)
                ).then(literal("heap")
                    .executes(MoonriseCommand::heap)
                )
            )
        );
    }

    public static void registerClient(final CommandDispatcher<CommandClientCommandSource> dispatcher) {
        dispatcher.register(
            LiteralArgumentBuilder.<CommandClientCommandSource>literal("moonrisec")
                .then(LiteralArgumentBuilder.<CommandClientCommandSource>literal("profiler")
                    .then(LiteralArgumentBuilder.<CommandClientCommandSource>literal("start")
                        .executes((final CommandContext<CommandClientCommandSource> ctx) -> {
                            return MoonriseCommand.startClientProfiler(ctx, -1.0);
                        })
                        .then(RequiredArgumentBuilder.<CommandClientCommandSource, Double>argument("record_threshold", DoubleArgumentType.doubleArg(0.0, 10_000.0))
                            .executes((final CommandContext<CommandClientCommandSource> ctx) -> {
                                return MoonriseCommand.startClientProfiler(ctx, DoubleArgumentType.getDouble(ctx, "record_threshold"));
                            })
                        )
                    )
                    .then(LiteralArgumentBuilder.<CommandClientCommandSource>literal("stop")
                        .executes(MoonriseCommand::stopClientProfiler)
                    )
                )
                .then(LiteralArgumentBuilder.<CommandClientCommandSource>literal("vm")
                    .then(LiteralArgumentBuilder.<CommandClientCommandSource>literal("gc")
                        .executes(MoonriseCommand::gcClient)
                    ).then(LiteralArgumentBuilder.<CommandClientCommandSource>literal("heap")
                        .executes(MoonriseCommand::heapClient)
                    )
                )
        );
    }

    private static int startClientProfiler(final CommandContext<CommandClientCommandSource> ctx, final double recordThreshold) {
        final boolean started = ((ProfilerMinecraft)Minecraft.getInstance()).moonrise$profilerInstance().startSession(
            0L, recordThreshold < 0.0 ? -1L : (long)Math.round(recordThreshold * 1.0E6)
        );

        if (!started) {
            ctx.getSource().moonrise$sendFailure(Component.literal("Profiler is already running").withStyle(ChatFormatting.RED));
            return 0;
        }

        ctx.getSource().moonrise$sendSuccess(Component.literal("Started client profiler").withStyle(ChatFormatting.BLUE));

        return Command.SINGLE_SUCCESS;
    }

    private static int stopClientProfiler(final CommandContext<CommandClientCommandSource> ctx) {
        final boolean ended = ((ProfilerMinecraft)Minecraft.getInstance()).moonrise$profilerInstance().endSession();

        if (!ended) {
            ctx.getSource().moonrise$sendFailure(Component.literal("Profiler is not running").withStyle(ChatFormatting.RED));
            return 0;
        }

        ctx.getSource().moonrise$sendSuccess(Component.literal("Stopped client profiler").withStyle(ChatFormatting.BLUE));

        return Command.SINGLE_SUCCESS;
    }

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS");

    private static String heapInternal() {
        final Path path = Path.of(".", "heapdumps", DATE_TIME_FORMAT.format(LocalDateTime.now()));

        try {
            final Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            final String nameSuggestion = path.getFileName().toString();

            final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

            Path heapPath;

            try {
                final Class<?> hotSpotClass = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
                final String selectedName = nameSuggestion.endsWith(".hprof") ? nameSuggestion : nameSuggestion.concat(".hprof");
                hotSpotClass.getMethod("dumpHeap", String.class, boolean.class)
                    .invoke(
                        ManagementFactory.newPlatformMXBeanProxy(server, "com.sun.management:type=HotSpotDiagnostic", hotSpotClass),
                        (heapPath = path.resolveSibling(selectedName)).toString(), true
                    );
            } catch (final ClassNotFoundException ex) {
                final Class<?> j9Class = Class.forName("openj9.lang.management.OpenJ9DiagnosticsMXBean");
                final String selectedName = nameSuggestion.endsWith(".phd") ? nameSuggestion : nameSuggestion.concat(".phd");
                j9Class.getMethod("triggerDumpToFile", String.class, String.class)
                    .invoke(
                        ManagementFactory.newPlatformMXBeanProxy(server, "openj9.lang.management:type=OpenJ9Diagnostics", j9Class),
                        "heap", (heapPath = path.resolveSibling(selectedName)).toString()
                    );
            }

            return heapPath.toString();
        } catch (final Throwable thr) {
            LOGGER.error("Failed to create heap dump at '" + path.toAbsolutePath() + "': ", thr);
            return null;
        }
    }

    private static int heap(final CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> {
            return Component.literal("Beginning VM heap dump...").withStyle(ChatFormatting.BLUE);
        }, true);

        final String heapFile = heapInternal();
        if (heapFile != null) {
            ctx.getSource().sendSuccess(() -> {
                return Component.literal("Wrote VM heap dump to file '").withStyle(ChatFormatting.BLUE)
                    .append(Component.literal(heapFile).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("'").withStyle(ChatFormatting.BLUE));
            }, true);
        } else {
            ctx.getSource().sendFailure(Component.literal("Failed to create heap dump, see logs").withStyle(ChatFormatting.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int heapClient(final CommandContext<CommandClientCommandSource> ctx) {
        ctx.getSource().moonrise$sendSuccess(
            Component.literal("Beginning VM heap dump...").withStyle(ChatFormatting.BLUE)
        );

        final String heapFile = heapInternal();
        if (heapFile != null) {
            ctx.getSource().moonrise$sendSuccess(
                Component.literal("Wrote VM heap dump to file '").withStyle(ChatFormatting.BLUE)
                    .append(Component.literal(heapFile).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("'").withStyle(ChatFormatting.BLUE))
            );
        } else {
            ctx.getSource().moonrise$sendFailure(Component.literal("Failed to create heap dump, see logs").withStyle(ChatFormatting.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int gc(final CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSystemMessage(
            Component.literal("Issuing").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(" System.gc() ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("call...").withStyle(ChatFormatting.BLUE))
        );
        System.gc();
        ctx.getSource().sendSystemMessage(
            Component.literal("Issued").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(" System.gc() ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("call").withStyle(ChatFormatting.BLUE))
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int gcClient(final CommandContext<CommandClientCommandSource> ctx) {
        ctx.getSource().moonrise$sendSuccess(
            Component.literal("Issuing").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(" System.gc() ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("call...").withStyle(ChatFormatting.BLUE))
        );
        System.gc();
        ctx.getSource().moonrise$sendSuccess(
            Component.literal("Issued").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(" System.gc() ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("call").withStyle(ChatFormatting.BLUE))
        );

        return Command.SINGLE_SUCCESS;
    }

    private static final AtomicLong GEN_AREA_ID_GENERATOR = new AtomicLong();
    private static final TicketType GEN_AREA_TICKET = ChunkSystemTicketType.create("chunk_system:gen_area", Long::compareTo, TicketType.FLAG_LOADING);

    private static final class SquareIterator implements PrimitiveIterator.OfLong {

        private final int centerX;
        private final int centerZ;
        private final int radius;

        private int dx;
        private int dz;

        public SquareIterator(final int centerX, final int centerZ, final int radius) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.dx = -radius;
            this.dz = -radius;
        }


        @Override
        public long nextLong() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            final int dx = this.dx;
            final int dz = this.dz;

            if (++this.dx > this.radius) {
                this.dx = -this.radius;
                ++this.dz;
            }

            return CoordinateUtils.getChunkKey(dx + this.centerX, dz + this.centerZ);
        }

        @Override
        public boolean hasNext() {
            return this.dz <= this.radius;
        }
    }

    private static final class SquareDividedIterator implements PrimitiveIterator.OfLong {

        private final int centerX;
        private final int centerZ;
        private final int radius;
        private final int divisor;
        private final int totalDivs;

        private int dz;
        private int div;
        private int divStart;
        private int divV;

        public SquareDividedIterator(final int centerX, final int centerZ, final int radius, final int max) {
            if (max < 2) {
                throw new IllegalArgumentException("Max must be > 1");
            }

            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.divisor = Math.min(2*radius+1, max);
            this.totalDivs = (2*radius+1+(this.divisor - 1)) / this.divisor;

            this.dz = -radius;
            this.div = 0;
            this.divStart = 0;
            this.divV = 0;
        }

        @Override
        public long nextLong() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            final int dz = this.dz;
            final int dx = this.divV - this.radius;

            if (++this.divV >= Math.min(this.divStart+this.divisor, 2*this.radius+1)) {
                if (++this.dz > this.radius) {
                    ++this.div;
                    this.divStart += this.divisor;
                    this.dz = -this.radius;
                }
                this.divV = this.divStart;
            }


            return CoordinateUtils.getChunkKey(dx + this.centerX, dz + this.centerZ);
        }

        @Override
        public boolean hasNext() {
            return this.div < this.totalDivs;
        }
    }

    private static final boolean USE_DIVIDED_STRAT = true;

    private static final record AreaGenTask(
        ServerLevel world, PrimitiveIterator.OfLong chunkIterator, Long ticketId,
        long startNS, int maxWorking, LongOpenHashSet generating,
        AtomicInteger generated, int total,
        AtomicLong lastLog
    ) {
        private static final long LOG_INTERVAL = TimeUnit.SECONDS.toNanos(1L);

        public void start() {
            while (this.tryFetchNext());
        }

        private void tryLog(final int generated) {
            final long now = System.nanoTime();
            final long lastLog = this.lastLog.get();
            if ((now - lastLog < LOG_INTERVAL && generated != this.total) || !this.lastLog.compareAndSet(lastLog, now)) {
                return;
            }

            final double rate = (double)generated / ((double)(now - this.startNS) / (double)TimeUnit.SECONDS.toNanos(1L));
            final double progress = 100.0 * ((double)generated / (double)(this.total));
            final double timeS = (double)(now - this.startNS) / (double)TimeUnit.SECONDS.toNanos(1L);

            this.world.getServer().sendSystemMessage(
                Component.literal("Generated ").withStyle(ChatFormatting.BLUE)
                    .append(Component.literal(DecimalFormats.NO_DECIMAL_PLACES.get().format((long)this.generated.get())).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal(DecimalFormats.NO_DECIMAL_PLACES.get().format((long)this.total)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(", rate=").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal(DecimalFormats.ONE_DECIMAL_PLACES.get().format(rate)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("chunks/s, progress=").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal(DecimalFormats.TWO_DECIMAL_PLACES.get().format(progress)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("%, time=").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal(DecimalFormats.ONE_DECIMAL_PLACES.get().format(timeS)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal( "s").withStyle(ChatFormatting.BLUE))
            );
        }

        public void finish(final int x, final int z) {
            final int generated = this.generated.incrementAndGet();

            synchronized (this) {
                this.generating.remove(CoordinateUtils.getChunkKey(x, z));
            }

            this.tryLog(generated);

            while (this.tryFetchNext());

            ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager
                .removeTicketAtLevel(GEN_AREA_TICKET, x, z, ChunkHolderManager.FULL_LOADED_TICKET_LEVEL, this.ticketId);
        }

        private boolean tryFetchNext() {
            final long toGen;
            synchronized (this) {
                if (this.generating.size() >= this.maxWorking) {
                    return false;
                }

                if (!this.chunkIterator.hasNext()) {
                    return false;
                }

                toGen = this.chunkIterator.next();

                this.generating.add(toGen);
            }

            final int chunkX = CoordinateUtils.getChunkX(toGen);
            final int chunkZ = CoordinateUtils.getChunkZ(toGen);

            ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager
                .addTicketAtLevel(GEN_AREA_TICKET, chunkX, chunkZ, ChunkHolderManager.FULL_LOADED_TICKET_LEVEL, this.ticketId);

            final Consumer<ChunkAccess> onComplete = (final ChunkAccess chunk) -> {
                AreaGenTask.this.finish(chunkX, chunkZ);
                AreaGenTask.this.world.getServer().emptyTicks = 0;
            };

            // avoid recursion for loaded chunks by scheduling a later chunk task (this also serves to batch ticket additions)
            ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler()
                .scheduleChunkTask(chunkX, chunkZ, () -> {
                    ((ChunkSystemServerLevel)AreaGenTask.this.world).moonrise$getChunkTaskScheduler()
                        .scheduleChunkLoad(
                            chunkX, chunkZ, ChunkStatus.FULL, true, Priority.NORMAL,
                            onComplete
                        );
                }, Priority.NORMAL);

            return true;
        }
    }

    private static int genArea(final CommandContext<CommandSourceStack> ctx, final ServerLevel world, final ColumnPos center, final int radius, final int maxLoaded) {
        final int centerX = center.x() >> 4;
        final int centerZ = center.z() >> 4;

        final long minBlockX = ((long)centerX << 4) - ((long)radius << 4);
        final long maxBlockX = ((long)centerX << 4) + ((long)radius << 4) + 15L;
        final long minBlockZ = ((long)centerZ << 4) - ((long)radius << 4);
        final long maxBlockZ = ((long)centerZ << 4) + ((long)radius << 4) + 15L;

        if (minBlockX <= -Level.MAX_LEVEL_SIZE || maxBlockX >= Level.MAX_LEVEL_SIZE
            || minBlockZ <= -Level.MAX_LEVEL_SIZE || maxBlockZ >= Level.MAX_LEVEL_SIZE) {
            ctx.getSource().sendFailure(
                Component.literal("Generated area ").withStyle(ChatFormatting.RED)
                        .append(Component.literal("[" + minBlockX + "," + minBlockZ + "]").withColor(LIGHT_RED))
                        .append(Component.literal(" -> ").withStyle(ChatFormatting.RED))
                        .append(Component.literal("[" + maxBlockX + "," + maxBlockZ + "]").withColor(LIGHT_RED))
                        .append(Component.literal(" is out of world bounds").withStyle(ChatFormatting.RED))
            );
            return 0;
        }

        ctx.getSource().sendSuccess(() -> {
            return Component.literal("Generating area ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal("[" + minBlockX + "," + minBlockZ + "]").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" -> ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("[" + maxBlockX + "," + maxBlockZ + "]").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(", check logs for progress").withStyle(ChatFormatting.BLUE));
        }, true);

        final Long id = Long.valueOf(GEN_AREA_ID_GENERATOR.getAndIncrement());

        final PrimitiveIterator.OfLong iterator = USE_DIVIDED_STRAT ? new SquareDividedIterator(centerX, centerZ, radius, maxLoaded) : new SquareIterator(centerX, centerZ, radius);

        final long start = System.nanoTime();
        new AreaGenTask(
            world, iterator, id, start, maxLoaded,
            new LongOpenHashSet(maxLoaded), new AtomicInteger(), (2*radius+1)*(2*radius+1),
            new AtomicLong(start)
        ).start();

        return Command.SINGLE_SUCCESS;
    }

    public static int holderInfo(final CommandContext<CommandSourceStack> ctx) {
        int total = 0;
        int canUnload = 0;
        int nullChunks = 0;
        int readOnly = 0;
        int protoChunk = 0;
        int fullChunk = 0;

        for (final NewChunkHolder holder : ((ChunkSystemServerLevel)ctx.getSource().getLevel()).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolders()) {
            final NewChunkHolder.ChunkCompletion completion = holder.getLastChunkCompletion();
            final ChunkAccess chunk = completion == null ? null : completion.chunk();

            ++total;

            if (chunk == null) {
                ++nullChunks;
            } else if (chunk instanceof ImposterProtoChunk) {
                ++readOnly;
            } else if (chunk instanceof ProtoChunk) {
                ++protoChunk;
            } else if (chunk instanceof LevelChunk) {
                ++fullChunk;
            }

            if (holder.isSafeToUnload() == null) {
                ++canUnload;
            }
        }

        ctx.getSource().sendSystemMessage(
            Component.literal("Total: ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(Integer.toString(total)).withStyle(ChatFormatting.DARK_AQUA))

                .append(Component.literal(" Unloadable: ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(Integer.toString(canUnload)).withStyle(ChatFormatting.DARK_AQUA))

                .append(Component.literal(" Null: ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(Integer.toString(nullChunks)).withStyle(ChatFormatting.DARK_AQUA))

                .append(Component.literal(" ReadOnly: ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(Integer.toString(readOnly)).withStyle(ChatFormatting.DARK_AQUA))

                .append(Component.literal(" Proto: ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(Integer.toString(protoChunk)).withStyle(ChatFormatting.DARK_AQUA))

                .append(Component.literal(" Full: ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(Integer.toString(fullChunk)).withStyle(ChatFormatting.DARK_AQUA))
        );

        return total;
    }

    public static int chunkInfo(final CommandContext<CommandSourceStack> ctx) {
        int total = 0;
        int inactive = 0;
        int full = 0;
        int blockTicking = 0;
        int entityTicking = 0;

        for (final NewChunkHolder holder : ((ChunkSystemServerLevel)ctx.getSource().getLevel()).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolders()) {
            final NewChunkHolder.ChunkCompletion completion = holder.getLastChunkCompletion();
            final ChunkAccess chunk = completion == null ? null : completion.chunk();

            if (!(chunk instanceof LevelChunk fullChunk)) {
                continue;
            }

            ++total;

            switch (holder.getChunkStatus()) {
                case INACCESSIBLE: {
                    ++inactive;
                    break;
                }
                case FULL: {
                    ++full;
                    break;
                }
                case BLOCK_TICKING: {
                    ++blockTicking;
                    break;
                }
                case ENTITY_TICKING: {
                    ++entityTicking;
                    break;
                }
            }
        }

        ctx.getSource().sendSystemMessage(
            Component.literal("Total: ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(Integer.toString(total)).withStyle(ChatFormatting.DARK_AQUA))

                .append(Component.literal(" Inactive: ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(Integer.toString(inactive)).withStyle(ChatFormatting.DARK_AQUA))

                .append(Component.literal(" Full: ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(Integer.toString(full)).withStyle(ChatFormatting.DARK_AQUA))

                .append(Component.literal(" Block Ticking: ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(Integer.toString(blockTicking)).withStyle(ChatFormatting.DARK_AQUA))

                .append(Component.literal(" Entity Ticking: ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(Integer.toString(entityTicking)).withStyle(ChatFormatting.DARK_AQUA))
        );

        return total;
    }

    public static int reload(final CommandContext<CommandSourceStack> ctx) {
        if (ConfigHolder.reloadConfig()) {
            ctx.getSource().sendSuccess(() -> {
                return Component.literal("Reloaded Moonrise config.")
                    .withStyle(ChatFormatting.BLUE);
            }, true);
            return Command.SINGLE_SUCCESS;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("Failed to reload Moonrise config, see logs.")
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }
    }

    public static int relight(final CommandContext<CommandSourceStack> ctx, final int radius) {
        final Vec3 center = ctx.getSource().getPosition();

        final int centerChunkX = Mth.floor(center.x) >> 4;
        final int centerChunkZ = Mth.floor(center.z) >> 4;

        final List<ChunkPos> chunks = new ArrayList<>();

        final LongOpenHashSet seen = new LongOpenHashSet();
        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        final long zero = CoordinateUtils.getChunkKey(0, 0);

        seen.add(zero);
        queue.enqueue(zero);
        chunks.add(new ChunkPos(centerChunkX, centerChunkZ));

        final int[][] offsets = new int[][] {
                new int[] { -1, 0  },
                new int[] {  1, 0  },
                new int[] {  0, -1 },
                new int[] {  0, 1  }
        };

        while (!queue.isEmpty()) {
            final long chunk = queue.dequeueLong();
            final int chunkX = CoordinateUtils.getChunkX(chunk);
            final int chunkZ = CoordinateUtils.getChunkZ(chunk);

            for (final int[] offset : offsets) {
                final int neighbourX = chunkX + offset[0];
                final int neighbourZ = chunkZ + offset[1];
                final long neighbour = CoordinateUtils.getChunkKey(neighbourX, neighbourZ);

                final int dist = Math.max(Math.abs(neighbourX), Math.abs(neighbourZ));

                if (dist > radius || !seen.add(neighbour)) {
                    continue;
                }

                queue.enqueue(neighbour);
                chunks.add(new ChunkPos(neighbourX + centerChunkX, neighbourZ + centerChunkZ));
            }
        }


        final int ret = ((StarLightLightingProvider)ctx.getSource().getLevel().getLightEngine()).starlight$serverRelightChunks(
                chunks,
                null,
                null
        );

        ctx.getSource().sendSuccess(() -> {
            return Component.literal("Relighting ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(Integer.toString(ret)).withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal(" chunks").withStyle(ChatFormatting.BLUE));
        }, true);

        return ret;
    }

    public static int debugChunks(final CommandContext<CommandSourceStack> ctx) {
        final File file = ChunkTaskScheduler.getChunkDebugFile();

        ctx.getSource().sendSuccess(() -> {
            return Component.literal("Writing chunk information dump to '").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(file.toString()).withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("'").withStyle(ChatFormatting.BLUE));
        }, true);
        try {
            JsonUtil.writeJson(ChunkTaskScheduler.debugAllWorlds(ctx.getSource().getServer()), file);

            ctx.getSource().sendSuccess(() -> {
                return Component.literal("Wrote chunk information dump to '").withStyle(ChatFormatting.BLUE)
                    .append(Component.literal(file.toString()).withStyle(ChatFormatting.DARK_AQUA))
                    .append(Component.literal("'").withStyle(ChatFormatting.BLUE));
            }, true);
            return Command.SINGLE_SUCCESS;
        } catch (final Throwable throwable) {
            LOGGER.error("Failed to dump chunk information to file '" + file.getAbsolutePath() + "'", throwable);
            ctx.getSource().sendFailure(Component.literal("Failed to dump chunk information, see console").withStyle(ChatFormatting.RED));
            return 0;
        }
    }


    // https://en.wikipedia.org/wiki/HSL_and_HSV#HSL_to_RGB
    private static int colorFromHSV(final double h, final double s, final double v) {
        final double c = v * s;
        final double hh = h / 60.0;
        final double x = c * (1 - Math.abs((hh % 2) - 1));

        final double m = v - c;
        final double cm = c + m;
        final double xm = x + m;

        final double r, g, b;
        if (hh >= 0.0 && hh < 1.0) {
            r = cm;
            g = xm;
            b = m;
        } else if (hh >= 1.0 && hh < 2.0) {
            r = xm;
            g = cm;
            b = m;
        } else if (hh >= 2.0 && hh < 3.0) {
            r = m;
            g = cm;
            b = xm;
        } else if (hh >= 3.0 && hh < 4.0) {
            r = m;
            g = xm;
            b = cm;
        } else if (hh >= 4.0 && hh < 5.0) {
            r = xm;
            g = m;
            b = cm;
        } else if (hh >= 5.0 && hh < 6.0) {
            r = cm;
            g = m;
            b = xm;
        } else {
            // out of range
            r = g = b = m;
        }

        return (Math.toIntExact(Math.round(r * 255)) << 16) |
               (Math.toIntExact(Math.round(g * 255)) << 8) |
                Math.toIntExact(Math.round(b * 255));
    }

    // reference tps: 20
    private static int getColourForTPS(final double tps) {
        final double difference = Math.min(Math.abs(20.0 - tps), 20.0);
        final double coordinate;
        if (difference <= 2.0) {
            // >= 18 tps
            coordinate = 70.0 + ((140.0 - 70.0)/(0.0 - 2.0)) * (difference - 2.0);
        } else if (difference <= 5.0) {
            // >= 15 tps
            coordinate = 30.0 + ((70.0 - 30.0)/(2.0 - 5.0)) * (difference - 5.0);
        } else if (difference <= 10.0) {
            // >= 10 tps
            coordinate = 10.0 + ((30.0 - 10.0)/(5.0 - 10.0)) * (difference - 10.0);
        } else {
            // >= 0.0 tps
            coordinate = 0.0 + ((10.0 - 0.0)/(10.0 - 20.0)) * (difference - 20.0);
        }

        return colorFromHSV(coordinate, 85.0 / 100.0, 80.0 / 100.0);
    }

    private static int getTPSColour(final double tps, final double expectedTps) {
        return getColourForTPS(tps * (20.0 / expectedTps));
    }

    private static int getColourForMSPT(final double mspt) {
        final double clamped = Math.min(Math.abs(mspt), 50.0);
        final double coordinate;
        if (clamped <= 15.0) {
            coordinate = 130.0 + ((140.0 - 130.0)/(0.0 - 15.0)) * (clamped - 15.0);
        } else if (clamped <= 25.0) {
            coordinate = 90.0 + ((130.0 - 90.0)/(15.0 - 25.0)) * (clamped - 25.0);
        } else if (clamped <= 35.0) {
            coordinate = 30.0 + ((90.0 - 30.0)/(25.0 - 35.0)) * (clamped - 35.0);
        } else if (clamped <= 40.0) {
            coordinate = 15.0 + ((30.0 - 15.0)/(35.0 - 40.0)) * (clamped - 40.0);
        } else {
            coordinate = 0.0 + ((15.0 - 0.0)/(40.0 - 50.0)) * (clamped - 50.0);
        }

        return colorFromHSV(coordinate, 85.0 / 100.0f, 80.0 / 100.0f);
    }

    private static int getMSPTColour(final double mspt, final double expectedMaxMSPT) {
        return getColourForMSPT(mspt * (50.0 / expectedMaxMSPT));
    }

    private static MutableComponent formatMSPTReport(final TickData.TickReportData report, final long tickIntervalNS) {
        final TickData.SegmentedAverage timePerTickData = report.timePerTickData();

        final TickData.SegmentData all = timePerTickData.segmentAll();
        final TickData.SegmentData percentile = timePerTickData.segment5PercentWorst();

        final double minMS = all.least() / 1.0E6;
        final double medMS = all.median() / 1.0E6;
        final double avgMS = all.average() / 1.0E6;
        final double percent = percentile.least() / 1.0E6; // show the smallest value in the 95% percentile
        final double max = all.greatest() / 1.0E6;
        final double expectedMSPT = (double)tickIntervalNS / 1.0E6;

        return Component.literal("").withStyle(ChatFormatting.BLUE)
            .append(Component.literal(ONE_DECIMAL_PLACES.get().format(minMS)).withColor(getMSPTColour(minMS, expectedMSPT)))
            .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
            .append(Component.literal(ONE_DECIMAL_PLACES.get().format(medMS)).withColor(getMSPTColour(medMS, expectedMSPT)))
            .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
            .append(Component.literal(ONE_DECIMAL_PLACES.get().format(avgMS)).withColor(getMSPTColour(avgMS, expectedMSPT)))
            .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
            .append(Component.literal(ONE_DECIMAL_PLACES.get().format(percent)).withColor(getMSPTColour(percent, expectedMSPT)))
            .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
            .append(Component.literal(ONE_DECIMAL_PLACES.get().format(max)).withColor(getMSPTColour(max, expectedMSPT)));
    }

    public static int tps(final CommandContext<CommandSourceStack> ctx) {
        final MinecraftServer server = ctx.getSource().getServer();

        final TickData tickTimes5s = ((TickLoopMinecraftServer)server).moonrise$getTickData5s();
        final TickData tickTimes10s = ((TickLoopMinecraftServer)server).moonrise$getTickData10s();
        final TickData tickTimes1m = ((TickLoopMinecraftServer)server).moonrise$getTickData1m();
        final TickData tickTimes5m = ((TickLoopMinecraftServer)server).moonrise$getTickData5m();
        final TickData tickTimes15m = ((TickLoopMinecraftServer)server).moonrise$getTickData15m();

        final long now = Util.getNanos();
        final long tickIntervalNS = server.tickRateManager().nanosecondsPerTick();

        final TickData.TickReportData report5s = tickTimes5s.generateTickReport(null, now, tickIntervalNS);
        final TickData.TickReportData report10s = tickTimes10s.generateTickReport(null, now, tickIntervalNS);
        final TickData.TickReportData report1m = tickTimes1m.generateTickReport(null, now, tickIntervalNS);
        final TickData.TickReportData report5m = tickTimes5m.generateTickReport(null, now, tickIntervalNS);
        final TickData.TickReportData report15m = tickTimes15m.generateTickReport(null, now, tickIntervalNS);

        if (report5s == null || report10s == null || report1m == null || report5m == null || report15m == null) {
            ctx.getSource().sendSuccess(() -> {
                return Component.literal("No tick data to report").withStyle(ChatFormatting.RED);
            }, false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> {
            return Component.literal("TPS ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal("5s").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("10s").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("1m").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("5m").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("15m").withStyle(ChatFormatting.DARK_AQUA));
        }, false);
        ctx.getSource().sendSuccess(() -> {
            final double tps5s = report5s.tpsData().segmentAll().average();
            final double tps10s = report10s.tpsData().segmentAll().average();
            final double tps1m = report1m.tpsData().segmentAll().average();
            final double tps5m = report5m.tpsData().segmentAll().average();
            final double tps15m = report15m.tpsData().segmentAll().average();
            final double expectedTps = (1.0E9 / tickIntervalNS);

            return Component.literal(" ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(ONE_DECIMAL_PLACES.get().format(tps5s)).withColor(getTPSColour(tps5s, expectedTps)))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(ONE_DECIMAL_PLACES.get().format(tps10s)).withColor(getTPSColour(tps10s, expectedTps)))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(ONE_DECIMAL_PLACES.get().format(tps1m)).withColor(getTPSColour(tps1m, expectedTps)))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(ONE_DECIMAL_PLACES.get().format(tps5m)).withColor(getTPSColour(tps5m, expectedTps)))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(ONE_DECIMAL_PLACES.get().format(tps15m)).withColor(getTPSColour(tps15m, expectedTps)));
        }, false);


        ctx.getSource().sendSuccess(() -> {
            return Component.literal("");
        }, false);


        ctx.getSource().sendSuccess(() -> {
            return Component.literal("MSPT ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal("min").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("med").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("avg").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("95%ile").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("max").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal(" for ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("10s").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("; ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("1m").withStyle(ChatFormatting.DARK_AQUA));
        }, false);
        ctx.getSource().sendSuccess(() -> {
            return Component.literal(" ").withStyle(ChatFormatting.BLUE)
                .append(formatMSPTReport(report10s, tickIntervalNS))
                .append(Component.literal(";  ").withStyle(ChatFormatting.BLUE))
                .append(formatMSPTReport(report1m, tickIntervalNS));
        }, false);

        return Command.SINGLE_SUCCESS;
    }
}
