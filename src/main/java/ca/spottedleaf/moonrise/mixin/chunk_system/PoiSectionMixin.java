package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.poi.ChunkSystemPoiSection;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Mixin(PoiSection.class)
abstract class PoiSectionMixin implements ChunkSystemPoiSection {

    @Shadow
    private boolean isValid;

    @Shadow
    @Final
    private Short2ObjectMap<PoiRecord> records;

    @Shadow
    @Final
    public Map<Holder<PoiType>, Set<PoiRecord>> byType;


    @Unique
    private final Optional<PoiSection> noAllocOptional = Optional.of((PoiSection)(Object)this);;

    @Override
    public final boolean moonrise$isEmpty() {
        return this.isValid && this.records.isEmpty() && this.byType.isEmpty();
    }

    @Override
    public final Optional<PoiSection> moonrise$asOptional() {
        return this.noAllocOptional;
    }
}
