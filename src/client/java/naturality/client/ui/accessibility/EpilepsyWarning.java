package naturality.client.ui.accessibility;

import naturality.config.live.NaturalityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

@org.jspecify.annotations.NullMarked
public final class EpilepsyWarning {
    private EpilepsyWarning() {}
    public static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(EpilepsyWarning::showIfNeeded);}
    public static void showIfNeeded(Minecraft client) {
        if(NaturalityConfig.get().epilepsyWarningAcknowledged || client.gui.overlay()!=null || !(client.gui.screen() instanceof TitleScreen))return;
        Screen parent=client.gui.screen();
        client.gui.setScreen(new ConfirmScreen(yes->{
            var config=NaturalityConfig.get();config.epilepsyMode=yes;config.epilepsyWarningAcknowledged=true;config.save();
            client.gui.setScreen(parent);
        },Component.literal("!EPILEPSY WARNING!"),
            Component.literal("Naturality has certain visual effects that could cause seizure in epileptic people. Turn on epilepsy mode?"),
            Component.literal("YES"),Component.literal("NO")));
    }
}