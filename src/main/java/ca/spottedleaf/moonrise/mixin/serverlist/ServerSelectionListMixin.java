package ca.spottedleaf.moonrise.mixin.serverlist;

import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerSelectionList.class)
public class ServerSelectionListMixin {

    /**
     * @reason Massively increase the threadpool count so that slow servers do not stall the pinging of other servers
     * on the status list
     * @author Spottedleaf
     */
    @ModifyConstant(
            method = "<clinit>",
            constant = @Constant(intValue = 5, ordinal = 0)
    )
    private static int noPingLimitExecutor(final int constant) {
        return 128;
    }
}
