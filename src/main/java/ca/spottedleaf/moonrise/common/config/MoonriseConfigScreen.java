package ca.spottedleaf.moonrise.common.config;

import ca.spottedleaf.moonrise.common.config.moonrise.MoonriseConfig;
import ca.spottedleaf.moonrise.common.config.ui.ConfigWalker;
import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MoonriseConfigScreen {
    private MoonriseConfigScreen() {
    }

    public static Screen create(final Screen parent) {
        final ConfigBuilder builder = ConfigBuilder.create()
                .setSavingRunnable(() -> {
                    MoonriseCommon.saveConfig();
                    MoonriseCommon.reloadConfig();
                })
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.moonrise.config"));

        try {
            ConfigWalker.walk(new MoonriseConfig(), MoonriseCommon.getConfig(), builder);
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }

        return builder.build();
    }
}
