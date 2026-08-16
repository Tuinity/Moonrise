package ca.spottedleaf.moonrise.mixin.random_ticking;

import ca.spottedleaf.moonrise.patches.random_ticking.RandomTickChunkSection;
import ca.spottedleaf.moonrise.patches.random_ticking.RandomTickLevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;

@Mixin(LevelChunk.class)
abstract class LevelChunkMixin extends ChunkAccess implements RandomTickLevelChunk {

    public LevelChunkMixin(final ChunkPos chunkPos, final UpgradeData upgradeData, final LevelHeightAccessor levelHeightAccessor,
                           final PalettedContainerFactory palettedContainerFactory, final long l, final LevelChunkSection[] levelChunkSections, final BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, palettedContainerFactory, l, levelChunkSections, blendingData);
    }

    @Unique
    private long[] moonrise$randomTickSectionMask;

    @Unique
    private int moonrise$randomTickEligibleCount;

    @Unique
    private LevelChunkSection[] moonrise$boundSectionRefs;

    @Override
    public final void moonrise$noteRandomTickSection(final int index, final boolean ticking) {
        final long[] mask = this.moonrise$randomTickSectionMask;
        if (mask == null || index < 0) {
            return;
        }
        final int word = index >>> 6;
        if (word >= mask.length) {
            return;
        }
        final long bit = 1L << (index & 63);
        final boolean was = (mask[word] & bit) != 0L;
        if (was == ticking) {
            return;
        }
        if (ticking) {
            mask[word] |= bit;
            this.moonrise$randomTickEligibleCount++;
        } else {
            mask[word] &= ~bit;
            this.moonrise$randomTickEligibleCount--;
        }
    }

    @Unique
    private boolean moonrise$sectionRefsChanged(final LevelChunkSection[] sections) {
        final LevelChunkSection[] bound = this.moonrise$boundSectionRefs;
        if (bound == null || bound.length != sections.length) {
            return true;
        }
        for (int i = 0; i < sections.length; i++) {
            if (bound[i] != sections[i]) {
                return true;
            }
        }
        return false;
    }

    @Override
    public final void moonrise$bindRandomTickSections() {
        final LevelChunkSection[] sections = this.getSections();
        final LevelChunkSection[] oldRefs = this.moonrise$boundSectionRefs;
        if (oldRefs != null) {
            for (int i = 0; i < oldRefs.length; i++) {
                final LevelChunkSection old = oldRefs[i];
                if (old != null && (i >= sections.length || old != sections[i])) {
                    ((RandomTickChunkSection)old).moonrise$unbindRandomTickChunk();
                }
            }
        }

        final int words = (sections.length + 63) >> 6;
        if (this.moonrise$randomTickSectionMask == null || this.moonrise$randomTickSectionMask.length != words) {
            this.moonrise$randomTickSectionMask = new long[Math.max(words, 1)];
        } else {
            Arrays.fill(this.moonrise$randomTickSectionMask, 0L);
        }
        this.moonrise$randomTickEligibleCount = 0;
        for (int i = 0; i < sections.length; i++) {
            final LevelChunkSection section = sections[i];
            if (section != null) {
                ((RandomTickChunkSection)section).moonrise$bindRandomTickChunk((LevelChunk)(Object)this, i);
                if (section.isRandomlyTickingBlocks()) {
                    this.moonrise$noteRandomTickSection(i, true);
                }
            }
        }
        this.moonrise$boundSectionRefs = Arrays.copyOf(sections, sections.length);
    }

    @Override
    public final void moonrise$ensureRandomTickSections() {
        final LevelChunkSection[] sections = this.getSections();
        if (this.moonrise$randomTickSectionMask == null || this.moonrise$sectionRefsChanged(sections)) {
            this.moonrise$bindRandomTickSections();
        }
    }

    @Override
    public final int moonrise$randomTickEligibleCount() {
        return this.moonrise$randomTickEligibleCount;
    }

    @Override
    public final long[] moonrise$randomTickSectionMask() {
        return this.moonrise$randomTickSectionMask;
    }

    /**
     * @reason Bind the live random-tick section bitset after construction.
     *         Empty chunks are skipped; they are never randomly ticked.
     * @author HabsW
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
            at = @At("TAIL")
    )
    private void moonrise$bindRandomTickSectionsOnConstruct(final Level level, final ChunkPos chunkPos, final UpgradeData upgradeData,
                                                            final LevelChunkTicks levelChunkTicks, final LevelChunkTicks levelChunkTicks2,
                                                            final long l, final LevelChunkSection[] levelChunkSections,
                                                            final LevelChunk.PostLoadProcessor postLoadProcessor,
                                                            final BlendingData blendingData, final CallbackInfo ci) {
        if ((Object)this instanceof EmptyLevelChunk) {
            return;
        }
        this.moonrise$bindRandomTickSections();
    }

    /**
     * @reason Post-load processors may replace section objects after the constructor bind.
     * @author HabsW
     */
    @Inject(
            method = "runPostLoad",
            at = @At("RETURN")
    )
    private void moonrise$bindRandomTickSectionsAfterPostLoad(final CallbackInfo ci) {
        if ((Object)this instanceof EmptyLevelChunk) {
            return;
        }
        this.moonrise$bindRandomTickSections();
    }
}
