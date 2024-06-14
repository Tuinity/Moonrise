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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Mixin(PoiSection.class)
public abstract class PoiSectionMixin implements ChunkSystemPoiSection {

    @Shadow
    private boolean isValid;

    @Shadow
    @Final
    private Short2ObjectMap<PoiRecord> records;

    @Shadow
    @Final
    public Map<Holder<PoiType>, Set<PoiRecord>> byType;


    @Unique
    private Optional<PoiSection> noAllocOptional;

    /**
     * @reason Initialise fields
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>(Ljava/lang/Runnable;ZLjava/util/List;)V",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(final CallbackInfo ci) {
        this.noAllocOptional = Optional.of((PoiSection)(Object)this);
    }

    @Override
    public final boolean moonrise$isEmpty() {
        return this.isValid && this.records.isEmpty() && this.byType.isEmpty();
    }

    @Override
    public final Optional<PoiSection> moonrise$asOptional() {
        return this.noAllocOptional;
    }
}