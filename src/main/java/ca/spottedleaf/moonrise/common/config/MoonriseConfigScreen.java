package ca.spottedleaf.moonrise.common.config;

import ca.spottedleaf.moonrise.common.config.moonrise.MoonriseConfig;
import ca.spottedleaf.moonrise.common.config.ui.ConfigWalker;
import ca.spottedleaf.moonrise.common.util.ConfigHolder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MoonriseConfigScreen {
    private MoonriseConfigScreen() {
    }

    public static Screen create(final Screen parent) {
        final ConfigBuilder builder = ConfigBuilder.create()
                .setSavingRunnable(() -> {
                    ConfigHolder.saveConfig();
                    ConfigHolder.reloadConfig();
                })
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.moonrise.config"));

        try {
            ConfigWalker.walk(new MoonriseConfig(), ConfigHolder.getConfig(), builder);
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }

        return builder.build();
    }
}
