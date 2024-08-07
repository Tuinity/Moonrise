package ca.spottedleaf.moonrise.fabric;

import ca.spottedleaf.moonrise.common.config.MoonriseConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class MoonriseModMenuHook implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return MoonriseConfigScreen::create;
    }
}
