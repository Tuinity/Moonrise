package ca.spottedleaf.moonrise.mixin.random_ticking;

import ca.spottedleaf.moonrise.patches.random_ticking.RandomTickLevelChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkAccess.class)
abstract class ChunkAccessMixin {

    @Shadow
    @Final
    protected LevelChunkSection[] sections;

    /**
     * @reason FAWE/WorldEdit replace sections via {@code getSections()[i] = newSection}.
     *         Mark the chunk so the next random tick rebinds. Random-tick's own
     *         getSections is suppressed by begin/end on the chunk.
     * @author HabsW
     */
    @Inject(
            method = "getSections",
            at = @At("HEAD")
    )
    private void moonrise$noteSectionArrayBorrowed(final CallbackInfoReturnable<LevelChunkSection[]> cir) {
        if ((Object)this instanceof RandomTickLevelChunk chunk) {
            chunk.moonrise$noteSectionArrayBorrowed();
        }
    }

    /**
     * @reason Avoid routing {@code getSection} through {@code getSections}, which would
     *         mark every block lookup as a FAWE-style array borrow.
     * @author HabsW
     */
    @Overwrite
    public LevelChunkSection getSection(final int sectionIndex) {
        return this.sections[sectionIndex];
    }
}
