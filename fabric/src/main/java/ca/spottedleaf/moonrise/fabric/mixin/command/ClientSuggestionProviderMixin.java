package ca.spottedleaf.moonrise.fabric.mixin.command;

import ca.spottedleaf.moonrise.patches.command.CommonClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientSuggestionProvider.class)
abstract class ClientSuggestionProviderMixin implements CommonClientCommandSource, FabricClientCommandSource {
    @Override
    public void moonrise$sendSuccess(final Component message) {
        this.sendFeedback(message);
    }

    @Override
    public void moonrise$sendFailure(final Component message) {
        this.sendError(message);
    }
}
