package ca.spottedleaf.moonrise.fabric.mixin.command;

import ca.spottedleaf.moonrise.patches.command.CommandClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientSuggestionProvider.class)
abstract class ClientSuggestionProviderMixin implements CommandClientCommandSource, FabricClientCommandSource {
    @Override
    public void moonrise$sendSuccess(final Component message) {
        this.sendFeedback(message);
    }

    @Override
    public void moonrise$sendFailure(final Component message) {
        this.sendError(message);
    }
}
