package ca.spottedleaf.moonrise.patches.command;

import ca.spottedleaf.moonrise.patches.profiler.ProfilerMinecraft;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.JsonUtil;
import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightLightingProvider;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MoonriseCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("moonrise").requires((final CommandSourceStack src) -> {
                    return src.hasPermission(src.getServer().getOperatorUserPermissionLevel());
                }).then(
                        literal("holderinfo")
                                .executes(MoonriseCommand::holderInfo)
                ).then(
                        literal("chunkinfo")
                                .executes(MoonriseCommand::chunkInfo)
                ).then(
                        literal("reload")
                                .executes(MoonriseCommand::reload)
                ).then(
                        literal("relight")
                                .executes((final CommandContext<CommandSourceStack> ctx) -> {
                                    return MoonriseCommand.relight(ctx, 10);
                                })
                                .then(
                                        argument("radius", IntegerArgumentType.integer(0, MoonriseConstants.MAX_VIEW_DISTANCE))
                                                .executes((final CommandContext<CommandSourceStack> ctx) -> {
                                                    return MoonriseCommand.relight(ctx, IntegerArgumentType.getInteger(ctx, "radius"));
                                                })
                                )
                ).then(
                        literal("debug")
                                .then(
                                        literal("chunks")
                                                .executes(MoonriseCommand::debugChunks)
                                )
                )
        );
    }

    public static void registerClient(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("moonrise")
                .then(literal("client")
                    .then(literal("profiler")
                        .then(literal("threshold")
                            .then(literal("set")
                                .then(argument("tick_ms", doubleArg(-1))
                                    .then(argument("render_ms", doubleArg(-1))
                                        .executes(ctx -> {
                                            return MoonriseCommand.setProfilerThresholds(
                                                ctx, DoubleArgumentType.getDouble(ctx, "tick_ms"), DoubleArgumentType.getDouble(ctx, "render_ms"));
                                        }))))
                            .then(literal("disable")
                                .executes(MoonriseCommand::clearProfilerThresholds)))))
        );
    }

    private static int clearProfilerThresholds(final CommandContext<CommandSourceStack> ctx) {
        ((ProfilerMinecraft) Minecraft.getInstance()).moonrise$profilerInstance().clearThresholds();
        return Command.SINGLE_SUCCESS;
    }

    private static int setProfilerThresholds(final CommandContext<CommandSourceStack> ctx, final double tickMs, final double renderMs) {
        if (tickMs < 0 && renderMs < 0) {
            ctx.getSource().sendFailure(Component.literal("Tick and render threshold cannot both be <0"));
            return 0;
        }
        ((ProfilerMinecraft) Minecraft.getInstance()).moonrise$profilerInstance().setThresholds(tickMs, renderMs);
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

        ctx.getSource().sendSystemMessage(MutableComponent.create(
                new PlainTextContents.LiteralContents(
                        "Total: " + total + " Unloadable: " + canUnload +
                             " Null: " + nullChunks + " ReadOnly: " + readOnly +
                             " Proto: " + protoChunk + " Full: " + fullChunk
                )
        ));

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

        ctx.getSource().sendSystemMessage(MutableComponent.create(
                new PlainTextContents.LiteralContents(
                        "Total: " + total + " Inactive: " + inactive +
                                " Full: " + full + " Block Ticking: " + blockTicking +
                                " Entity Ticking: " + entityTicking
                )
        ));

        return total;
    }

    public static int reload(final CommandContext<CommandSourceStack> ctx) {
        if (MoonriseCommon.reloadConfig()) {
            ctx.getSource().sendSuccess(() -> {
                return MutableComponent.create(
                        new PlainTextContents.LiteralContents(
                                "Reloaded Moonrise config."
                        )
                );
            }, true);
        } else {
            ctx.getSource().sendFailure(
                    MutableComponent.create(
                            new PlainTextContents.LiteralContents(
                                    "Failed to reload Moonrise config."
                            )
                    )
            );
        }

        return 0;
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
            return MutableComponent.create(
                    new PlainTextContents.LiteralContents(
                            "Relighting " + ret + " chunks"
                    )
            );
        }, true);

        return ret;
    }

    public static int debugChunks(final CommandContext<CommandSourceStack> ctx) {
        final File file = ChunkTaskScheduler.getChunkDebugFile();

        ctx.getSource().sendSuccess(() -> {
            return MutableComponent.create(
                    new PlainTextContents.LiteralContents(
                            "Writing chunk information dump to '" + file + "'"
                    )
            );
        }, true);
        try {
            JsonUtil.writeJson(ChunkTaskScheduler.debugAllWorlds(ctx.getSource().getServer()), file);

            ctx.getSource().sendSuccess(() -> {
                return MutableComponent.create(
                        new PlainTextContents.LiteralContents(
                                "Wrote chunk information dump to '" + file + "'"
                        )
                );
            }, true);
        } catch (final Throwable throwable) {
            LOGGER.error("Failed to dump chunk information to file '" + file.getAbsolutePath() + "'", throwable);
            ctx.getSource().sendFailure(MutableComponent.create(
                    new PlainTextContents.LiteralContents(
                            "Failed to dump chunk information, see console"
                    )
            ));
        }

        return 0;
    }
}
