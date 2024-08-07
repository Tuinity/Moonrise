package ca.spottedleaf.moonrise.common.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MoonriseConfigScreen {
	private MoonriseConfigScreen() {
	}

	public static Screen create(final Screen parent) {
		final ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("title.examplemod.config"));

		return builder.build();
	}
}
