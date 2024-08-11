package ca.spottedleaf.moonrise.patches.command;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.JsonUtil;
import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.profiler.client.ProfilerMinecraft;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightLightingProvider;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
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

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MoonriseCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("moonrise").requires((final CommandSourceStack src) -> {
                return src.hasPermission(src.getServer().getOperatorUserPermissionLevel()) || !(src.getServer() instanceof DedicatedServer);
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
        if (MoonriseCommon.reloadConfig()) {
            ctx.getSource().sendSuccess(() -> {
                return Component.literal("Reloaded Moonrise config.")
                    .withStyle(ChatFormatting.BLUE);
            }, true);
            return Command.SINGLE_SUCCESS;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("Reloaded Moonrise config.")
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
}
