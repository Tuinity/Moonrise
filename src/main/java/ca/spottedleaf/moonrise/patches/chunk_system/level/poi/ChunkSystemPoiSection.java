package ca.spottedleaf.moonrise.patches.chunk_system.level.poi;

import net.minecraft.world.entity.ai.village.poi.PoiSection;
import java.util.Optional;

public interface ChunkSystemPoiSection {

    public boolean moonrise$isEmpty();

    public Optional<PoiSection> moonrise$asOptional();

}
