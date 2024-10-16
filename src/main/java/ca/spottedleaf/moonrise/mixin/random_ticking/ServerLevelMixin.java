package ca.spottedleaf.moonrise.mixin.random_ticking;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.common.list.ShortList;
import ca.spottedleaf.moonrise.common.util.SimpleRandom;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin extends Level implements WorldGenLevel {

    protected ServerLevelMixin(final WritableLevelData writableLevelData, final ResourceKey<Level> resourceKey, final RegistryAccess registryAccess, final Holder<DimensionType> holder, final boolean bl, final boolean bl2, final long l, final int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, bl, bl2, l, i);
    }

    @Unique
    private static final LevelChunkSection[] EMPTY_SECTION_ARRAY = new LevelChunkSection[0];

    @Unique
    private final SimpleRandom simpleRandom = new SimpleRandom(0L);

    /**
     * @reason Use faster random
     * @author Spottedleaf
     */
    @Redirect(
        method = "tickChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"
        )
    )
    private int nextInt(final RandomSource instance, final int bound) {
        return this.simpleRandom.nextInt(bound);
    }

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
        final SimpleRandom simpleRandom = this.simpleRandom;
        final boolean doubleTickFluids = !PlatformHooks.get().configFixMC224294();

        final ChunkPos cpos = chunk.getPos();
        final int offsetX = cpos.x << 4;
        final int offsetZ = cpos.z << 4;

        for (int sectionIndex = 0, sectionsLen = sections.length; sectionIndex < sectionsLen; sectionIndex++) {
            final int offsetY = (sectionIndex + minSection) << 4;
            final LevelChunkSection section = sections[sectionIndex];
            final PalettedContainer<BlockState> states = section.states;
            if (!section.isRandomlyTickingBlocks()) {
                continue;
            }

            final ShortList tickList = ((BlockCountingChunkSection)section).moonrise$getTickingBlockList();

            for (int i = 0; i < tickSpeed; ++i) {
                final int tickingBlocks = tickList.size();
                final int index = simpleRandom.nextInt() & ((16 * 16 * 16) - 1);

                if (index >= tickingBlocks) {
                    // most of the time we fall here
                    continue;
                }

                final int location = (int)tickList.getRaw(index) & 0xFFFF;
                final BlockState state = states.get(location);

                // do not use a mutable pos, as some random tick implementations store the input without calling immutable()!
                final BlockPos pos = new BlockPos((location & 15) | offsetX, ((location >>> (4 + 4)) & 15) | offsetY, ((location >>> 4) & 15) | offsetZ);

                state.randomTick((ServerLevel)(Object)this, pos, simpleRandom);
                if (doubleTickFluids) {
                    final FluidState fluidState = state.getFluidState();
                    if (fluidState.isRandomlyTicking()) {
                        fluidState.randomTick((ServerLevel)(Object)this, pos, simpleRandom);
                    }
                }
            }
        }

        return EMPTY_SECTION_ARRAY;
    }
}
