package ca.spottedleaf.moonrise.mixin.chunk_system;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(StructureTemplate.Palette.class)
abstract class StructureTemplate$PaletteMixin {

    @Shadow
    private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

    /**
     * @reason Make cache CHM to prevent CME
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void makeCacheCHM(final CallbackInfo ci) {
        this.cache = new ConcurrentHashMap<>();
    }
}
