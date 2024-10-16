package ca.spottedleaf.moonrise.common.util;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.DimensionType;

public final class WorldUtil {

    // min, max are inclusive

    public static int getMaxSection(final LevelHeightAccessor world) {
        return world.getMaxSection() - 1; // getMaxSection() is exclusive
    }

    public static int getMinSection(final LevelHeightAccessor world) {
        return world.getMinSection();
    }

    public static int getMinSection(final DimensionType dimensionType) {
        return SectionPos.blockToSectionCoord(dimensionType.minY());
    }

    public static int getMaxSection(final DimensionType dimensionType) {
        return SectionPos.blockToSectionCoord(dimensionType.minY() + dimensionType.height() - 1);
    }

    public static int getMaxLightSection(final LevelHeightAccessor world) {
        return getMaxSection(world) + 1;
    }

    public static int getMinLightSection(final LevelHeightAccessor world) {
        return getMinSection(world) - 1;
    }



    public static int getTotalSections(final LevelHeightAccessor world) {
        return getMaxSection(world) - getMinSection(world) + 1;
    }

    public static int getTotalLightSections(final LevelHeightAccessor world) {
        return getMaxLightSection(world) - getMinLightSection(world) + 1;
    }

    public static int getMinBlockY(final LevelHeightAccessor world) {
        return getMinSection(world) << 4;
    }

    public static int getMaxBlockY(final LevelHeightAccessor world) {
        return (getMaxSection(world) << 4) | 15;
    }

    public static String getWorldName(final Level world) {
        if (world == null) {
            return "null world";
        }
        return world.dimension().toString();
    }

    private WorldUtil() {
        throw new RuntimeException();
    }
}
