package ca.spottedleaf.moonrise.mixin.chunk_system;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(StructureTemplate.Palette.class)
public abstract class StructureTemplate$PaletteMixin {

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
    private <K, V> void makeCacheCHM(final CallbackInfo ci) {
        this.cache = new ConcurrentHashMap<>();
    }
}
