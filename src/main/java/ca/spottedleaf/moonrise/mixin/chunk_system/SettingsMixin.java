package ca.spottedleaf.moonrise.mixin.chunk_system;

import net.minecraft.server.dedicated.Settings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Settings.class)
abstract class SettingsMixin {

    /**
     * @reason Redirect users to our config option, as sync-chunk-writes is ignored. Targets the header
     *         constant rather than the store call, as NeoForge replaces the call with its own.
     * @author jpenilla
     */
    @ModifyConstant(
        method = "store",
        constant = @Constant(
            stringValue = "Minecraft server properties"
        )
    )
    private String addSyncChunkWritesNote(final String comment) {
        return comment + "\nNote: sync-chunk-writes in this file is ignored. Moonrise's region file handling makes synchronous chunk"
            + "\nwrites unnecessary. To enable them anyway, use chunk-saving.sync-chunk-writes in the Moonrise config.";
    }
}
