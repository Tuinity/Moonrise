package ca.spottedleaf.moonrise.mixin.starlight.world;

import ca.spottedleaf.moonrise.common.util.MixinWorkarounds;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine;
import ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData;
import ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

// note: keep in-sync with SaveUtil
@Mixin(SerializableChunkData.class)
abstract class SerializableChunkDataMixin {

    @Shadow
    @Final
    private ChunkStatus chunkStatus;

    @Shadow
    @Final
    private boolean lightCorrect;

    @Shadow
    @Final
    private List<SerializableChunkData.SectionData> sectionData;

    /**
     * @reason Replace light correctness check with our own
     *         Our light check is versioned in case we change the light format OR fix a bug
     * @author Spottedleaf
     */
    @Redirect(
        method = "parse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/nbt/CompoundTag;getBoolean(Ljava/lang/String;)Z",
            ordinal = 0
        )
    )
    private static boolean setLightCorrect(final CompoundTag instance, final String string,
                                           @Local(ordinal = 0, argsOnly = false) final ChunkStatus status) {
        final boolean starlightCorrect = instance.get("isLightOn") != null && instance.getInt(SaveUtil.STARLIGHT_VERSION_TAG) == SaveUtil.STARLIGHT_LIGHT_VERSION;
        return status.isOrAfter(ChunkStatus.LIGHT) && starlightCorrect;
    }

    /**
     * @reason Add starlight block/sky state to SectionData
     * @author Spottedleaf
     */
    @Redirect(
        method = "parse",
        at = @At(
            value = "NEW",
            target = "(ILnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/DataLayer;Lnet/minecraft/world/level/chunk/DataLayer;)Lnet/minecraft/world/level/chunk/storage/SerializableChunkData$SectionData;",
            ordinal = 0
        )
    )
    private static SerializableChunkData.SectionData readStarlightState(final int y, final LevelChunkSection chunkSection,
                                                                        final DataLayer blockLight, final DataLayer skyLight,
                                                                        @Local(ordinal = 3, argsOnly = false) final CompoundTag sectionData) {
        final SerializableChunkData.SectionData ret = new SerializableChunkData.SectionData(
            y, chunkSection, blockLight, skyLight
        );

        if (sectionData.contains(SaveUtil.BLOCKLIGHT_STATE_TAG, Tag.TAG_ANY_NUMERIC)) {
            ((StarlightSectionData)(Object)ret).starlight$setBlockLightState(sectionData.getInt(SaveUtil.BLOCKLIGHT_STATE_TAG));
        }

        if (sectionData.contains(SaveUtil.SKYLIGHT_STATE_TAG, Tag.TAG_ANY_NUMERIC)) {
            ((StarlightSectionData)(Object)ret).starlight$setSkyLightState(sectionData.getInt(SaveUtil.SKYLIGHT_STATE_TAG));
        }

        return ret;
    }

    /**
     * @reason Load light data from the section data and store them in the returned value's SWMRNibbleArrays
     * @author Spottedleaf
     */
    @Inject(
        method = "read",
        at = @At(
            value = "RETURN"
        )
    )
    private void loadStarlightLightData(final ServerLevel world, final PoiManager poiManager,
                                        final RegionStorageInfo regionStorageInfo, final ChunkPos pos,
                                        final CallbackInfoReturnable<ProtoChunk> cir) {
        final ProtoChunk ret = cir.getReturnValue();

        final boolean hasSkyLight = world.dimensionType().hasSkyLight();
        final int minSection = WorldUtil.getMinLightSection(world);

        final SWMRNibbleArray[] blockNibbles = StarLightEngine.getFilledEmptyLight(world);
        final SWMRNibbleArray[] skyNibbles = StarLightEngine.getFilledEmptyLight(world);

        for (final SerializableChunkData.SectionData sectionData : this.sectionData) {
            final int y = sectionData.y();
            final DataLayer blockLight = sectionData.blockLight();
            final DataLayer skyLight = sectionData.skyLight();

            final int blockState = ((StarlightSectionData)(Object)sectionData).starlight$getBlockLightState();
            final int skyState = ((StarlightSectionData)(Object)sectionData).starlight$getSkyLightState();

            if (blockState >= 0) {
                if (blockLight != null) {
                    blockNibbles[y - minSection] = new SWMRNibbleArray(MixinWorkarounds.clone(blockLight.getData()), blockState); // clone for data safety
                } else {
                    blockNibbles[y - minSection] = new SWMRNibbleArray(null, blockState);
                }
            }

            if (skyState >= 0 && hasSkyLight) {
                if (skyLight != null) {
                    skyNibbles[y - minSection] = new SWMRNibbleArray(MixinWorkarounds.clone(skyLight.getData()), skyState); // clone for data safety
                } else {
                    skyNibbles[y - minSection] = new SWMRNibbleArray(null, skyState);
                }
            }
        }

        ((StarlightChunk)ret).starlight$setBlockNibbles(blockNibbles);
        ((StarlightChunk)ret).starlight$setSkyNibbles(skyNibbles);
    }

    /**
     * @reason Rewrite the section copying so that we can store Starlight's data
     * @author Spottedleaf
     */
    @Redirect(
        method = "copyOf",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;getMinLightSection()I",
            ordinal = 0
        )
    )
    private static int rewriteSectionCopy(final LevelLightEngine instance,
                                          @Local(ordinal = 0, argsOnly = true) final ServerLevel world,
                                          @Local(ordinal = 0, argsOnly = true) final ChunkAccess chunk,
                                          @Local(ordinal = 0, argsOnly = false) final List<SerializableChunkData.SectionData> sections) {

        final int minLightSection = WorldUtil.getMinLightSection(world);
        final int maxLightSection = WorldUtil.getMaxLightSection(world);
        final int minBlockSection = WorldUtil.getMinSection(world);

        final LevelChunkSection[] chunkSections = chunk.getSections();
        final SWMRNibbleArray[] blockNibbles = ((StarlightChunk)chunk).starlight$getBlockNibbles();
        final SWMRNibbleArray[] skyNibbles = ((StarlightChunk)chunk).starlight$getSkyNibbles();

        for (int lightSection = minLightSection; lightSection <= maxLightSection; ++lightSection) {
            final int lightSectionIdx = lightSection - minLightSection;
            final int blockSectionIdx = lightSection - minBlockSection;

            final LevelChunkSection chunkSection = (blockSectionIdx >= 0 && blockSectionIdx < chunkSections.length) ? chunkSections[blockSectionIdx].copy() : null;
            final SWMRNibbleArray.SaveState blockNibble = blockNibbles[lightSectionIdx].getSaveState();
            final SWMRNibbleArray.SaveState skyNibble = skyNibbles[lightSectionIdx].getSaveState();

            if (chunkSection == null && blockNibble == null && skyNibble == null) {
                continue;
            }

            final SerializableChunkData.SectionData sectionData = new SerializableChunkData.SectionData(
                lightSection, chunkSection,
                blockNibble == null ? null : (blockNibble.data == null ? null : new DataLayer(blockNibble.data)),
                skyNibble == null ? null : (skyNibble.data == null ? null : new DataLayer(skyNibble.data))
            );

            if (blockNibble != null) {
                ((StarlightSectionData)(Object)sectionData).starlight$setBlockLightState(blockNibble.state);
            }

            if (skyNibble != null) {
                ((StarlightSectionData)(Object)sectionData).starlight$setSkyLightState(skyNibble.state);
            }

            sections.add(sectionData);
        }

        // force the Vanilla loop to never run
        return Integer.MAX_VALUE;
    }

    /**
     * @reason Store the per-section block/sky state from Starlight
     * @author Spottedleaf
     */
    @Inject(
        method = "write",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/chunk/storage/SerializableChunkData$SectionData;chunkSection:Lnet/minecraft/world/level/chunk/LevelChunkSection;",
            ordinal = 0
        )
    )
    private void storeStarlightState(final CallbackInfoReturnable<CompoundTag> cir,
                                     @Local(ordinal = 0, argsOnly = false) final SerializableChunkData.SectionData sectionData,
                                     @Local(ordinal = 1, argsOnly = false) final CompoundTag sectionNBT) {
        final int blockState = ((StarlightSectionData)(Object)sectionData).starlight$getBlockLightState();
        final int skyState = ((StarlightSectionData)(Object)sectionData).starlight$getSkyLightState();

        if (blockState > 0) {
            sectionNBT.putInt(SaveUtil.BLOCKLIGHT_STATE_TAG, blockState);
        }

        if (skyState > 0) {
            sectionNBT.putInt(SaveUtil.SKYLIGHT_STATE_TAG, skyState);
        }
    }

    /**
     * @reason Store Starlight's light version
     * @author Spottedleaf
     */
    @Inject(
        method = "write",
        at = @At(
            value = "RETURN"
        )
    )
    private void writeStarlightCorrectLight(final CallbackInfoReturnable<CompoundTag> cir) {
        if (this.chunkStatus.isBefore(ChunkStatus.LIGHT) || !this.lightCorrect) {
            return;
        }

        final CompoundTag ret = cir.getReturnValue();

        // clobber vanilla value to force vanilla to relight
        ret.putBoolean("isLightOn", false);
        // store our light version
        ret.putInt(SaveUtil.STARLIGHT_VERSION_TAG, SaveUtil.STARLIGHT_LIGHT_VERSION);
    }
}
