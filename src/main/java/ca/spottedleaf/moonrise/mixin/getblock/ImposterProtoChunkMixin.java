package ca.spottedleaf.moonrise.mixin.getblock;

import ca.spottedleaf.moonrise.patches.getblock.GetBlockChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ImposterProtoChunk.class)
abstract class ImposterProtoChunkMixin extends ProtoChunk implements GetBlockChunk {

    @Shadow
    @Final
    private LevelChunk wrapped;

    public ImposterProtoChunkMixin(final ChunkPos chunkPos, final UpgradeData upgradeData, final LevelHeightAccessor levelHeightAccessor, final PalettedContainerFactory containerFactory, @Nullable final BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, containerFactory, blendingData);
    }

    @Override
    public BlockState moonrise$getBlock(final int x, final int y, final int z) {
        return ((GetBlockChunk)this.wrapped).moonrise$getBlock(x, y, z);
    }

    @Override
    public FluidState moonrise$getFluid(final int x, final int y, final int z) {
        return ((GetBlockChunk)this.wrapped).moonrise$getFluid(x, y, z);
    }
}
