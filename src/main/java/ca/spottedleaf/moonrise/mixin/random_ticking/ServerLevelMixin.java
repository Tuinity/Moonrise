package ca.spottedleaf.moonrise.mixin.random_ticking;


import ca.spottedleaf.moonrise.common.config.moonrise.MoonriseConfig;
import ca.spottedleaf.moonrise.common.list.IBlockDataList;
import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements WorldGenLevel {

    protected ServerLevelMixin(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder<DimensionType> holder, Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l, int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, supplier, bl, bl2, l, i);
    }

    @Unique
    private static final LevelChunkSection[] EMPTY_SECTION_ARRAY = new LevelChunkSection[0];

    /**
     * @reason Optimise random ticking so that it will not retrieve BlockStates unnecessarily, as well as
     *         optionally avoiding double ticking fluid blocks.
     * @author Spottedleaf
     */
    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;",
                    ordinal = 0
            )
    )
    private LevelChunkSection[] optimiseRandomTick(final LevelChunk chunk,
                                                   @Local(ordinal = 0, argsOnly = true) final int tickSpeed) {
        final LevelChunkSection[] sections = chunk.getSections();
        final int minSection = WorldUtil.getMinSection((ServerLevel)(Object)this);
        final RandomSource random = this.random;
        final boolean tickFluids = !MoonriseCommon.getConfig().bugFixes.fixMC224294;

        final ChunkPos cpos = chunk.getPos();
        final int offsetX = cpos.x << 4;
        final int offsetZ = cpos.z << 4;

        for (int sectionIndex = 0, sectionsLen = sections.length; sectionIndex < sectionsLen; sectionIndex++) {
            final int offsetY = (sectionIndex + minSection) << 4;
            final LevelChunkSection section = sections[sectionIndex];
            final PalettedContainer<BlockState> states = section.states;
            if (section == null || !section.isRandomlyTickingBlocks()) {
                continue;
            }

            final IBlockDataList tickList = ((BlockCountingChunkSection)section).moonrise$getTickingBlockList();
            if (tickList.size() == 0) {
                continue;
            }

            for (int i = 0; i < tickSpeed; ++i) {
                final int tickingBlocks = tickList.size();
                final int index = random.nextInt() & ((16 * 16 * 16) - 1);

                if (index >= tickingBlocks) {
                    // most of the time we fall here
                    continue;
                }

                final long raw = tickList.getRaw(index);
                final int location = IBlockDataList.getLocationFromRaw(raw);
                final int randomX = (location & 15);
                final int randomY = ((location >>> (4 + 4)) & 255);
                final int randomZ = ((location >>> 4) & 15);
                final BlockState state = states.get(randomX | (randomZ << 4) | (randomY << 8));

                // do not use a mutable pos, as some random tick implementations store the input without calling immutable()!
                final BlockPos pos = new BlockPos(randomX | offsetX, randomY | offsetY, randomZ | offsetZ);

                state.randomTick((ServerLevel)(Object)this, pos, random);
                if (tickFluids) {
                    final FluidState fluidState = state.getFluidState();
                    if (fluidState.isRandomlyTicking()) {
                        fluidState.randomTick((ServerLevel)(Object)this, pos, random);
                    }
                }
            }
        }

        return EMPTY_SECTION_ARRAY;
    }
}
