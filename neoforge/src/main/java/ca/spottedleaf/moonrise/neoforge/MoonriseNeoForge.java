package ca.spottedleaf.moonrise.neoforge;

import ca.spottedleaf.moonrise.common.config.MoonriseConfigScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod("moonrise")
public final class MoonriseNeoForge {
	public MoonriseNeoForge(final IEventBus modBus) {
		modBus.addListener(FMLClientSetupEvent.class, event -> {
			ModLoadingContext.get().registerExtensionPoint(
					IConfigScreenFactory.class,
					() -> (modContainer, parent) -> MoonriseConfigScreen.create(parent)
			);
		});
	}
}
