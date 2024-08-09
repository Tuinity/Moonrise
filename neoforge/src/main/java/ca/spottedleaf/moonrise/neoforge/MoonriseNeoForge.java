package ca.spottedleaf.moonrise.neoforge;

import ca.spottedleaf.moonrise.common.config.MoonriseConfigScreen;
import ca.spottedleaf.moonrise.patches.command.MoonriseCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod("moonrise")
public final class MoonriseNeoForge {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public MoonriseNeoForge(final IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> {
            ModLoadingContext.get().registerExtensionPoint(
                IConfigScreenFactory.class,
                () -> (modContainer, parent) -> MoonriseConfigScreen.create(parent)
            );
            NeoForge.EVENT_BUS.addListener((final RegisterClientCommandsEvent commandsEvent) -> {
                MoonriseCommand.registerClient((CommandDispatcher) commandsEvent.getDispatcher());
            });
        });
    }
}
