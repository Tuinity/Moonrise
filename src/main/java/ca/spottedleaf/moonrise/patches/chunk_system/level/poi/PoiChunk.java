package ca.spottedleaf.moonrise.patches.chunk_system.level.poi;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

public final class PoiChunk {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoiChunk.class);

    public final ServerLevel world;
    public final int chunkX;
    public final int chunkZ;
    public final int minSection;
    public final int maxSection;

    private final PoiSection[] sections;

    private boolean isDirty;
    private boolean loaded;

    public PoiChunk(final ServerLevel world, final int chunkX, final int chunkZ, final int minSection, final int maxSection) {
        this(world, chunkX, chunkZ, minSection, maxSection, new PoiSection[maxSection - minSection + 1]);
    }

    public PoiChunk(final ServerLevel world, final int chunkX, final int chunkZ, final int minSection, final int maxSection, final PoiSection[] sections) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minSection = minSection;
        this.maxSection = maxSection;
        this.sections = sections;
        if (this.sections.length != (maxSection - minSection + 1)) {
            throw new IllegalStateException("Incorrect length used, expected " + (maxSection - minSection + 1) + ", got " + this.sections.length);
        }
    }

    public void load() {
        TickThread.ensureTickThread(this.world, this.chunkX, this.chunkZ, "Loading in poi chunk off-main");
        if (this.loaded) {
            return;
        }
        this.loaded = true;
        ((ChunkSystemPoiManager)this.world.getChunkSource().getPoiManager()).moonrise$loadInPoiChunk(this);
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public boolean isEmpty() {
        for (final PoiSection section : this.sections) {
            if (section != null && !((ChunkSystemPoiSection)section).moonrise$isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public PoiSection getOrCreateSection(final int chunkY) {
        if (chunkY >= this.minSection && chunkY <= this.maxSection) {
            final int idx = chunkY - this.minSection;
            final PoiSection ret = this.sections[idx];
            if (ret != null) {
                return ret;
            }

            final PoiManager poiManager = this.world.getPoiManager();
            final long key = CoordinateUtils.getChunkSectionKey(this.chunkX, chunkY, this.chunkZ);

            return this.sections[idx] = new PoiSection(() -> {
                poiManager.setDirty(key);
            });
        }
        throw new IllegalArgumentException("chunkY is out of bounds, chunkY: " + chunkY + " outside [" + this.minSection + "," + this.maxSection + "]");
    }

    public PoiSection getSection(final int chunkY) {
        if (chunkY >= this.minSection && chunkY <= this.maxSection) {
            return this.sections[chunkY - this.minSection];
        }
        return null;
    }

    public Optional<PoiSection> getSectionForVanilla(final int chunkY) {
        if (chunkY >= this.minSection && chunkY <= this.maxSection) {
            final PoiSection ret = this.sections[chunkY - this.minSection];
            return ret == null ? Optional.empty() : ((ChunkSystemPoiSection)ret).moonrise$asOptional();
        }
        return Optional.empty();
    }

    public boolean isDirty() {
        return this.isDirty;
    }

    public void setDirty(final boolean dirty) {
        this.isDirty = dirty;
    }

    // returns null if empty
    public CompoundTag save() {
        final RegistryOps<Tag> registryOps = RegistryOps.create(NbtOps.INSTANCE, this.world.registryAccess());

        final CompoundTag ret = new CompoundTag();
        final CompoundTag sections = new CompoundTag();
        ret.put("Sections", sections);

        ret.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());

        final ServerLevel world = this.world;
        final PoiManager poiManager = world.getPoiManager();
        final int chunkX = this.chunkX;
        final int chunkZ = this.chunkZ;

        for (int sectionY = this.minSection; sectionY <= this.maxSection; ++sectionY) {
            final PoiSection section = this.sections[sectionY - this.minSection];
            if (section == null || ((ChunkSystemPoiSection)section).moonrise$isEmpty()) {
                continue;
            }

            final long key = CoordinateUtils.getChunkSectionKey(chunkX, sectionY, chunkZ);
            // codecs are honestly such a fucking disaster. What the fuck is this trash?
            final Codec<PoiSection> codec = PoiSection.codec(() -> {
                poiManager.setDirty(key);
            });

            final DataResult<Tag> serializedResult = codec.encodeStart(registryOps, section);
            final int finalSectionY = sectionY;
            final Tag serialized = serializedResult.resultOrPartial((final String description) -> {
                LOGGER.error("Failed to serialize poi chunk for world: " + WorldUtil.getWorldName(world) + ", chunk: (" + chunkX + "," + finalSectionY + "," + chunkZ + "); description: " + description);
            }).orElse(null);
            if (serialized == null) {
                // failed, should be logged from the resultOrPartial
                continue;
            }

            sections.put(Integer.toString(sectionY), serialized);
        }

        return sections.isEmpty() ? null : ret;
    }

    public static PoiChunk empty(final ServerLevel world, final int chunkX, final int chunkZ) {
        final PoiChunk ret = new PoiChunk(world, chunkX, chunkZ, WorldUtil.getMinSection(world), WorldUtil.getMaxSection(world));
        ret.loaded = true;
        return ret;
    }

    public static PoiChunk parse(final ServerLevel world, final int chunkX, final int chunkZ, final CompoundTag data) {
        final PoiChunk ret = empty(world, chunkX, chunkZ);

        final RegistryOps<Tag> registryOps = RegistryOps.create(NbtOps.INSTANCE, world.registryAccess());

        final CompoundTag sections = data.getCompound("Sections");

        if (sections.isEmpty()) {
            // nothing to parse
            return ret;
        }

        final PoiManager poiManager = world.getPoiManager();

        boolean readAnything = false;

        for (int sectionY = ret.minSection; sectionY <= ret.maxSection; ++sectionY) {
            final String key = Integer.toString(sectionY);
            if (!sections.contains(key)) {
                continue;
            }

            final long coordinateKey = CoordinateUtils.getChunkSectionKey(chunkX, sectionY, chunkZ);
            // codecs are honestly such a fucking disaster. What the fuck is this trash?
            final Codec<PoiSection> codec = PoiSection.codec(() -> {
                poiManager.setDirty(coordinateKey);
            });

            final CompoundTag section = sections.getCompound(key);
            final DataResult<PoiSection> deserializeResult = codec.parse(registryOps, section);
            final int finalSectionY = sectionY;
            final PoiSection deserialized = deserializeResult.resultOrPartial((final String description) -> {
                LOGGER.error("Failed to deserialize poi chunk for world: " + WorldUtil.getWorldName(world) + ", chunk: (" + chunkX + "," + finalSectionY + "," + chunkZ + "); description: " + description);
            }).orElse(null);

            if (deserialized == null || ((ChunkSystemPoiSection)deserialized).moonrise$isEmpty()) {
                // completely empty, no point in storing this
                continue;
            }

            readAnything = true;
            ret.sections[sectionY - ret.minSection] = deserialized;
        }

        ret.loaded = !readAnything; // Set loaded to false if we read anything to ensure proper callbacks to PoiManager are made on #load

        return ret;
    }
}
