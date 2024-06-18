package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements ChunkSystemServerPlayer {
    public ServerPlayerMixin(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Unique
    private boolean isRealPlayer;

    @Unique
    private RegionizedPlayerChunkLoader.PlayerChunkLoaderData chunkLoader;

    @Unique
    private RegionizedPlayerChunkLoader.ViewDistanceHolder viewDistanceHolder;

    /**
     * @reason Initialise fields
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(final CallbackInfo ci) {
        this.viewDistanceHolder = new RegionizedPlayerChunkLoader.ViewDistanceHolder();
    }

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
