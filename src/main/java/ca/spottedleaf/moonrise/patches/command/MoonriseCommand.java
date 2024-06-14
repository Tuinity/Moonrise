package ca.spottedleaf.moonrise.patches.command;

import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class MoonriseCommand {

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("moonrise").requires((final CommandSourceStack src) -> {
                    return src.hasPermission(2);
                }).then(
                        Commands.literal("holderinfo")
                                .executes((final CommandContext<CommandSourceStack> ctx) -> {
                                        return MoonriseCommand.holderInfo(ctx);
                                })
                ).then(
                        Commands.literal("chunkinfo")
                                .executes((final CommandContext<CommandSourceStack> ctx) -> {
                                    return MoonriseCommand.chunkInfo(ctx);
                                })
                ).then(
                        Commands.literal("reload")
                                .executes((final CommandContext<CommandSourceStack> ctx) -> {
                                    return MoonriseCommand.reload(ctx);
                                })
                )
        );
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
}
