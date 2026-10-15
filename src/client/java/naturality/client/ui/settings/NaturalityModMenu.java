package naturality.client.ui.settings;
import naturality.client.ui.settings.NaturalityCategoryScreen.NaturalityConfigScreen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

@org.jspecify.annotations.NullMarked
public final class NaturalityModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return NaturalityConfigScreen::new;
    }
}
