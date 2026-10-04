package ca.spottedleaf.moonrise.mixin.getblock;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.StructureAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
abstract class ChunkAccessMixin implements BlockGetter, BiomeResolver, LightChunk, StructureAccess {

    @Shadow
    @Final
    protected LevelChunkSection[] sections;


    @Unique
    private int minY;

    @Unique
    private int height;

    @Unique
    private int maxY;

    @Unique
    private int minSectionY;

    @Unique
    private int maxSectionY;

    @Unique
    private int sectionsCount;

    @Unique
    private int minSection;

    @Unique
    private int maxSection;

    /**
     * @reason Initialises the min/max section
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                value = "CTOR_HEAD",
                unsafe = true
            )
    )
    public void onConstruct(final CallbackInfo ci,
                            @Local(ordinal = 0, argsOnly = true) LevelHeightAccessor world) {
        this.minY = world.getMinY();
        this.height = world.getHeight();
        this.maxY = this.minY + this.height - 1;
        this.minSectionY = this.minY >> 4;
        this.maxSectionY = this.maxY >> 4;
        this.sectionsCount = this.maxSectionY - this.minSectionY + 1;

        this.minSection = WorldUtil.getMinSection(world);
        this.maxSection = WorldUtil.getMaxSection(world);
    }

    /**
     * @reason Optimise implementation
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public Holder<Biome> getNoiseBiome(final int biomeX, final int biomeY, final int biomeZ) {
        int sectionY = (biomeY >> QuartPos.BITS) - this.minSection;
        int rel = biomeY & QuartPos.MASK;

        final LevelChunkSection[] sections = this.sections;

        if (sectionY < 0) {
            sectionY = 0;
            rel = 0;
        } else if (sectionY >= sections.length) {
            sectionY = sections.length - 1;
            rel = QuartPos.MASK;
        }

        return sections[sectionY].getNoiseBiome(biomeX & QuartPos.MASK, rel, biomeZ & QuartPos.MASK);
    }

    /**
     * @reason Avoid using the height accessor
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public int getMinY() {
        return this.minY;
    }

    /**
     * @reason Avoid using the height accessor
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public int getMaxY() {
        return this.maxY;
    }

    @Override
    public int getSectionsCount() {
        return this.sectionsCount;
    }

    @Override
    public int getMinSectionY() {
        return this.minSectionY;
    }

    @Override
    public int getMaxSectionY() {
        return this.maxSectionY;
    }

    @Override
    public boolean isInsideBuildHeight(final int blockY) {
        return blockY >= this.minY && blockY <= this.maxY;
    }

    @Override
    public boolean isOutsideBuildHeight(final BlockPos pos) {
        return this.isOutsideBuildHeight(pos.getY());
    }

    @Override
    public boolean isOutsideBuildHeight(final int blockY) {
        return blockY < this.minY || blockY > this.maxY;
    }

    @Override
    public int getSectionIndex(final int blockY) {
        return (blockY >> 4) - this.minSectionY;
    }

    @Override
    public int getSectionIndexFromSectionY(final int sectionY) {
        return sectionY - this.minSectionY;
    }

    @Override
    public int getSectionYFromSectionIndex(final int sectionIdx) {
        return sectionIdx + this.minSectionY;
    }
}
