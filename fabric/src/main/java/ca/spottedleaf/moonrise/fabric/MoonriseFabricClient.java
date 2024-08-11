package ca.spottedleaf.moonrise.fabric;

import ca.spottedleaf.moonrise.patches.command.MoonriseCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;

public final class MoonriseFabricClient implements ClientModInitializer {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((final CommandDispatcher<FabricClientCommandSource> commandDispatcher, final CommandBuildContext commandBuildContext) -> {
            MoonriseCommand.registerClient((CommandDispatcher)commandDispatcher);
        });
    }
}
