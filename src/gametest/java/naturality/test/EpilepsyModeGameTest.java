package naturality.test;

import naturality.config.NaturalityConfig;
import naturality.client.config.EpilepsyWarning;
import naturality.client.weather.LightningFlashes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.components.Button;

public final class EpilepsyModeGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        check(!new NaturalityConfig().epilepsyMode,"Epilepsy mode defaults off");
        check(LightningFlashes.opacity(0,false)==0,"Normal flash begins smoothly at zero");
        for(double hold:new double[]{.2,.4,.6})for(double decay:new double[]{.8,1,1.2}) {
            check(Math.abs(LightningFlashes.opacity(.05,false,hold,decay)-.45F)<.001,"Buildup reaches half brightness after 0.05 seconds");
            check(LightningFlashes.opacity(.1,false,hold,decay)>.85,"Buildup reaches potent full brightness after 0.1 seconds");
            check(LightningFlashes.opacity(.1+hold,false,hold,decay)>.85,"Full brightness lasts through the randomized hold");
            check(Math.abs(LightningFlashes.opacity(.1+hold+decay/2,false,hold,decay)-.45F)<.001,"Decay smoothly reaches half brightness at its midpoint");
            check(LightningFlashes.opacity(.1+hold+decay,false,hold,decay)<.001,"Flash fades fully at the end of its randomized decay");
        }
        check(LightningFlashes.opacity(0,true)==0,"Reduced cue eases in rather than flashing");
        for(int i=0;i<=100;i++)check(LightningFlashes.opacity(i/100.0,true)<.08,"Reduced blue cue has low contrast");
        for(boolean yes:new boolean[]{false,true}) {
            context.runOnClient(c->{
                var config=NaturalityConfig.get();config.epilepsyWarningAcknowledged=false;config.epilepsyMode=false;
                c.gui.setScreen(new TitleScreen());EpilepsyWarning.showIfNeeded(c);
                check(c.gui.screen() instanceof ConfirmScreen,"First main menu shows the warning");
                check(c.gui.screen().getTitle().getString().equals("!EPILEPSY WARNING!"),"Exact warning title");
            });
            if(!yes){context.waitTicks(2);context.takeScreenshot("naturality-first-launch-epilepsy-warning");}
            context.runOnClient(c->{
                var config=NaturalityConfig.get();String label=yes?"YES":"NO";
                var button=c.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow();
                button.onPress(null);
                check(config.epilepsyMode==yes && config.epilepsyWarningAcknowledged,"Warning choice applies and is acknowledged");
                try {
                    var file=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("naturality.json");
                    var saved=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
                    check(saved.get("epilepsyWarningAcknowledged").getAsBoolean() && saved.get("epilepsyMode").getAsBoolean()==yes,"Choice and acknowledgement are saved to disk");
                }catch(java.io.IOException e){throw new AssertionError(e);}
                check(c.gui.screen() instanceof TitleScreen,"Choice returns to the main menu");
                EpilepsyWarning.showIfNeeded(c);check(c.gui.screen() instanceof TitleScreen,"Acknowledged warning does not recur");
            });
        }
        context.runOnClient(c->{NaturalityConfig.get().epilepsyMode=false;NaturalityConfig.get().save();});
        try(var world=context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @a");world.getServer().runCommand("tp @a 0 120 0");
            context.waitTicks(30);
            context.runOnClient(c->{NaturalityConfig.get().epilepsyMode=true;LightningFlashes.strike();});
            context.waitTicks(5);context.takeScreenshot("lightning-epilepsy-blue-tint");
            context.runOnClient(c->{NaturalityConfig.get().epilepsyMode=false;NaturalityConfig.get().save();});
        }
    }
}
