package ca.spottedleaf.moonrise.fabric;

import ca.spottedleaf.moonrise.patches.command.MoonriseCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class MoonriseFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((final CommandDispatcher<CommandSourceStack> dispatcher, final CommandBuildContext commandBuildContext,
                                                    final Commands.CommandSelection commandSelection) -> {
            MoonriseCommand.register(dispatcher, commandSelection);
        });
    }
}
