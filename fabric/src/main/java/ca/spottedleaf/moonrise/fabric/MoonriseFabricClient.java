package ca.spottedleaf.moonrise.fabric;

import ca.spottedleaf.moonrise.patches.command.MoonriseCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

public final class MoonriseFabricClient implements ClientModInitializer {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void onInitializeClient() {
        MoonriseCommand.registerClient((CommandDispatcher) ClientCommandManager.getActiveDispatcher());
    }
}
