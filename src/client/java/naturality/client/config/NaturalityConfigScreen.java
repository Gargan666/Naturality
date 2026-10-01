package naturality.client.config;

import net.minecraft.client.gui.screens.Screen;

/** Mod Menu entry point, sharing navigation, search and saving with every page. */
public final class NaturalityConfigScreen extends NaturalityCategoryScreen {
    public NaturalityConfigScreen(Screen parent) { super(parent, Category.ROOT); }
    public NaturalityConfigScreen(Screen parent, boolean darknessCategories) {
        super(parent, darknessCategories ? Category.DARKNESS : Category.ROOT);
    }
}
