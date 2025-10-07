package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin extends Player implements ChunkSystemServerPlayer {
    public ServerPlayerMixin(final Level p_250508_, final GameProfile p_252153_) {
        super(p_250508_, p_252153_);
    }

    @Unique
    private boolean isRealPlayer;

    @Unique
    private RegionizedPlayerChunkLoader.PlayerChunkLoaderData chunkLoader;

    @Unique
    private RegionizedPlayerChunkLoader.ViewDistanceHolder viewDistanceHolder = new RegionizedPlayerChunkLoader.ViewDistanceHolder();

    @Override
    public final boolean moonrise$isRealPlayer() {
        return this.isRealPlayer;
    }

    @Override
    public final void moonrise$setRealPlayer(final boolean real) {
        this.isRealPlayer = real;
    }

    @Override
    public final RegionizedPlayerChunkLoader.PlayerChunkLoaderData moonrise$getChunkLoader() {
        return this.chunkLoader;
    }

    @Override
    public final void moonrise$setChunkLoader(final RegionizedPlayerChunkLoader.PlayerChunkLoaderData loader) {
        this.chunkLoader = loader;
    }

    @Override
    public final RegionizedPlayerChunkLoader.ViewDistanceHolder moonrise$getViewDistanceHolder() {
        return this.viewDistanceHolder;
    }

    /**
     * @reason Do not process packets while waiting for spawn location
     * @author Spottedleaf
     */
    @Redirect(
        method = "adjustSpawnLocation",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"
        )
    )
    private void blockOnCorrectQueue(final MinecraftServer instance, final BooleanSupplier isDone,
                                     final @Local(ordinal = 0, argsOnly = true) ServerLevel world) {
        world.getChunkSource().mainThreadProcessor.managedBlock(isDone);
    }

    /**
     * @reason Copy player state when respawning
     * @author Spottedleaf
     */
    @Inject(
            method = "restoreFrom",
            at = @At(
                    value = "HEAD"
            )
    )
    private void copyRealPlayer(ServerPlayer from, boolean bl, CallbackInfo ci) {
        this.isRealPlayer = ((ServerPlayerMixin)(Object)from).isRealPlayer;
        this.viewDistanceHolder = ((ServerPlayerMixin)(Object)from).viewDistanceHolder;
    }
}
