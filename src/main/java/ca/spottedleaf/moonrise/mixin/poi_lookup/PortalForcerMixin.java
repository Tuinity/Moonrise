package ca.spottedleaf.moonrise.mixin.poi_lookup;

import ca.spottedleaf.moonrise.patches.poi_lookup.PoiAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypeIds;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RetroGen;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(PortalForcer.class)
abstract class PortalForcerMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Unique
    private static boolean isGeneratedChunk(final ChunkAccess chunk) {
        if (chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL)) {
            // Already fully generated; no retrogen exception is needed.
            return true;
        }

        final RetroGen retroGen = chunk.getRetroGen();
        if (retroGen == null) {
            // An incomplete chunk without retrogen has not previously reached FULL.
            return false;
        }

        if (retroGen.targetStatus().isOrAfter(ChunkStatus.FULL)) {
            // Retrogen lowered the persisted status of a previously FULL chunk.
            return true;
        }

        if (!retroGen.hasBelowZeroRetroGen()) {
            // Only the legacy below-zero upgrade needs the SPAWN exception. Other upgrades of FULL chunks
            // retain FULL as the target (last checked: 26.4-snapshot-2).
            return false;
        }

        // pre-1.18 FULL chunks get a retrogen target of HEIGHTMAPS, later remapped to SPAWN
        // there is no way to distinguish a true pre-1.18 SPAWN chunk from a pre-1.18 FULL chunk
        return retroGen.targetStatus().isOrAfter(ChunkStatus.SPAWN);
    }

    /**
     * @reason Route to use PoiAccess
     * @author Spottedleaf
     */
    @Overwrite
    public Optional<BlockPos> findClosestPortalPosition(final BlockPos approximateExitPos, final boolean toNether, final WorldBorder worldBorder) {
        final PoiManager poiManager = this.level.getPoiManager();
        final int radius = toNether ? 16 : 128;

        final List<PoiRecord> records = new ArrayList<>();
        PoiAccess.findClosestPoiDataRecords(
            poiManager, type -> type.is(PoiTypeIds.NETHER_PORTAL),
            (final Holder<PoiType> type, final BlockPos pos) -> {
                if (!worldBorder.isWithinBounds(pos)) {
                    return false;
                }

                final ChunkAccess lowest = this.level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.EMPTY);

                if (!isGeneratedChunk(lowest)) {
                    // why would we generate the chunk?
                    return false;
                }

                return lowest.getBlockState(pos).hasProperty(BlockStateProperties.HORIZONTAL_AXIS);
            },
            approximateExitPos, radius, Double.MAX_VALUE, PoiManager.Occupancy.ANY, true, records
        );

        // this gets us most of the way there, but Vanilla biases lower y values.
        PoiRecord lowestYRecord = null;
        for (PoiRecord record : records) {
            if (lowestYRecord == null) {
                lowestYRecord = record;
            } else if (lowestYRecord.getPos().getY() > record.getPos().getY()) {
                lowestYRecord = record;
            }
        }
        // now we're done
        return Optional.ofNullable(lowestYRecord == null ? null : lowestYRecord.getPos());
    }
}
