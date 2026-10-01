package naturality.test;

import java.util.ArrayList;
import java.util.List;
import naturality.client.config.NaturalityCategoryScreen;
import naturality.client.config.NaturalityConfigScreen;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;

/** Real menu navigation and editable global search, including return-state regression checks. */
public final class ConfigMenuGameTest implements FabricClientGameTest {
    private static List<GuiEventListener> descendants(GuiEventListener element) {
        var result = new ArrayList<GuiEventListener>();
        result.add(element);
        if (element instanceof ContainerEventHandler container)
            for (var child : container.children()) result.addAll(descendants(child));
        return result;
    }

    private static void press(GuiEventListener screen, String label) {
        for (var element : descendants(screen))
            if (element instanceof net.minecraft.client.gui.components.AbstractButton button && button.getMessage().getString().startsWith(label)) {
                button.onPress(new net.minecraft.client.input.InputWithModifiers() {
                    @Override public int input() { return 0; }
                    @Override public int modifiers() { return 0; }
                });
                return;
            }
        throw new AssertionError("Missing button: " + label);
    }

    private static EditBox search(GuiEventListener screen) {
        return descendants(screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }

    private static OptionsList list(GuiEventListener screen) {
        return descendants(screen).stream().filter(OptionsList.class::isInstance).map(OptionsList.class::cast).findFirst().orElseThrow();
    }

    private static long resets(GuiEventListener screen) {
        return descendants(screen).stream().filter(e -> e instanceof Button b && b.getMessage().getString().equals("Reset")).count();
    }

    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.gui.setScreen(new NaturalityConfigScreen(null));
            if (list(client.gui.screen()).children().size() != 3) throw new AssertionError("Expected three main categories");
            press(client.gui.screen(), "Gameplay and Serverside");
            if (resets(client.gui.screen()) != 7) throw new AssertionError("Gameplay must include snow and weather rules");
            client.gui.screen().onClose();
            press(client.gui.screen(), "Blocks and Effects");
            press(client.gui.screen(), "Portals");
            if (resets(client.gui.screen()) != 11) throw new AssertionError("Missing portal controls");
            search(client.gui.screen()).setValue("SUN BEAMS");
            if (resets(client.gui.screen()) != 1) throw new AssertionError("Search must include other categories, ignoring case");
            search(client.gui.screen()).setValue("water rings");
            if (resets(client.gui.screen()) != 2) throw new AssertionError("Multiword global search failed");
            var config = NaturalityConfig.get();
            boolean original = config.liquids.rainRipples;
            press(client.gui.screen(), "Rain water rings:");
            if (config.liquids.rainRipples == original) throw new AssertionError("Search result did not edit the setting");
            press(client.gui.screen(), "Rain water rings:");
            search(client.gui.screen()).setValue("no-such-setting-xyz");
            if (resets(client.gui.screen()) != 0 || list(client.gui.screen()).children().size() != 1)
                throw new AssertionError("No-results state must replace the list");
            search(client.gui.screen()).setValue("");
            if (resets(client.gui.screen()) != 11) throw new AssertionError("Clearing search must restore Portals");
            client.gui.screen().onClose();
            client.gui.screen().onClose();
            press(client.gui.screen(), "Lighting and Atmosphere");
            press(client.gui.screen(), "Weather");
            if (resets(client.gui.screen()) != 5) throw new AssertionError("Weather appearance controls missing");
            search(client.gui.screen()).setValue("overworld override rain");
            if (resets(client.gui.screen()) != 0) throw new AssertionError("World weather overrides must not be editable in config");
            search(client.gui.screen()).setValue("snow wrapping");
            if (resets(client.gui.screen()) != 1) throw new AssertionError("Snow gameplay toggle must be globally searchable");
            search(client.gui.screen()).setValue("glint");
            if (resets(client.gui.screen()) != 5) throw new AssertionError("All glint controls must be searchable");
            search(client.gui.screen()).setValue("");
            client.gui.screen().onClose();
            press(client.gui.screen(), "Clouds");
            var options = list(client.gui.screen());
            options.setScrollAmount(140);
            double scroll = options.scrollAmount();
            search(client.gui.screen()).setValue("blaze");
            if (resets(client.gui.screen()) != 1) throw new AssertionError("Entity light levels must be searchable");
            search(client.gui.screen()).setValue("  ");
            // Master toggle plus eleven controls per layer, including opacity.
            if (resets(client.gui.screen()) != 23 || options.scrollAmount() != scroll)
                throw new AssertionError("Clearing search must restore the previous page and scroll");
            search(client.gui.screen()).setValue("glow");
        });
        context.waitTicks(2);
        context.takeScreenshot("config-global-search");
        context.runOnClient(client -> {
            search(client.gui.screen()).setValue("");
            client.gui.screen().onClose();
            client.gui.screen().onClose();
        });
        context.takeScreenshot("config-main-categories");
        context.runOnClient(client -> client.gui.screen().onClose());

        // Exercise compilation of both sides of the newly configurable shader paths.
        context.runOnClient(client -> {
            client.gui.setScreen(new NaturalityCategoryScreen(null, NaturalityCategoryScreen.Category.ROOT));
            NaturalityConfig.get().lighting.enabled = false;
            NaturalityConfig.get().effects.starPulses = false;
            NaturalityConfig.get().effects.pixelGlint = false;
            NaturalityConfig.get().liquids.emissiveLava = false;
            NaturalityConfig.get().effects.fireAnimation = false;
            NaturalityConfig.get().portalChanges.endParallax = false;
            client.gui.screen().onClose();
        });
        context.waitFor(client -> client.gui.overlay() == null);
        context.runOnClient(client -> {
            client.gui.setScreen(new NaturalityCategoryScreen(null, NaturalityCategoryScreen.Category.ROOT));
            NaturalityConfig.get().lighting.enabled = true;
            NaturalityConfig.get().effects.starPulses = true;
            NaturalityConfig.get().effects.pixelGlint = true;
            NaturalityConfig.get().liquids.emissiveLava = true;
            NaturalityConfig.get().effects.fireAnimation = true;
            NaturalityConfig.get().portalChanges.endParallax = true;
            client.gui.screen().onClose();
        });
        context.waitFor(client -> client.gui.overlay() == null);

        var gson = new com.google.gson.Gson();
        var defaults = gson.fromJson("{}", NaturalityConfig.class);
        if (!defaults.effects.snowOverlays || !defaults.effects.pixelGlint || !defaults.liquids.lavaImpactColumns || !defaults.effects.bubblePops || !defaults.effects.leaves || !defaults.liquids.waterRim || !defaults.portalChanges.endParallax)
            throw new AssertionError("Old config files must keep new effects enabled");
        defaults.effects.leafRestTicks = -1;
        defaults.effects.sanitize();
        defaults.fog.borderStartPercent = 99;
        defaults.fog.borderFullPercent = 0;
        defaults.fog.sanitize();
        var restored = gson.fromJson(gson.toJson(defaults), NaturalityConfig.class);
        if (restored.effects.leafRestTicks != 0 || restored.fog.borderFullPercent != 100)
            throw new AssertionError("New controls must clamp and survive JSON round trips");

        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> {
                var effects = NaturalityConfig.get().effects;
                boolean worldContours=effects.worldGlintContours, handContours=effects.handGlintContours,
                    previewContours=effects.previewGlintContours, weatherParticles=effects.weatherParticles;
                try {
                    effects.worldGlintContours=effects.handGlintContours=effects.previewGlintContours=false;
                    effects.weatherParticles=false;
                    if (naturality.client.weather.ParticleWeather.enabled(client.level)) throw new AssertionError("Disabled precipitation must restore vanilla sheets");
                    var texture=net.minecraft.resources.Identifier.withDefaultNamespace("textures/item/diamond_sword.png");
                    naturality.client.glint.GlintContours.beginFrame();
                    if (naturality.client.glint.GlintContours.armor(texture,true)!=null) throw new AssertionError("Disabled world outlines captured geometry");
                    naturality.client.glint.GlintContours.beginHandFrame();
                    if (naturality.client.glint.GlintContours.armor(texture,true)!=null) throw new AssertionError("Disabled hand outlines captured geometry");
                    naturality.client.glint.GlintContours.beginPreview(1);
                    if (naturality.client.glint.GlintContours.armor(texture,true)!=null) throw new AssertionError("Disabled portrait outlines captured geometry");
                } finally {
                    naturality.client.glint.GlintContours.endFrame();
                    effects.worldGlintContours=worldContours; effects.handGlintContours=handContours;
                    effects.previewGlintContours=previewContours; effects.weatherParticles=weatherParticles;
                }
                boolean smoke = effects.smoke, flames = effects.flames;
                boolean eye = NaturalityConfig.get().portalChanges.eyeGlow;
                try {
                    effects.smoke = false;
                    effects.flames = false;
                    for (var type : new net.minecraft.core.particles.SimpleParticleType[] {
                            net.minecraft.core.particles.ParticleTypes.SMOKE,
                            net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE}) {
                        var particle = client.particleEngine.createParticle(type, 0, 100, 0, 0, 0, 0);
                        if (!(particle instanceof net.minecraft.client.particle.SmokeParticle))
                            throw new AssertionError("Disabled smoke must use vanilla behavior");
                    }
                    for (var type : new net.minecraft.core.particles.SimpleParticleType[] {
                            net.minecraft.core.particles.ParticleTypes.FLAME,
                            net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                            net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME}) {
                        var particle = client.particleEngine.createParticle(type, 0, 100, 0, 0, 0, 0);
                        if (!(particle instanceof net.minecraft.client.particle.FlameParticle))
                            throw new AssertionError("Disabled flames must use vanilla behavior");
                    }
                    NaturalityConfig.get().portalChanges.eyeGlow = false;
                    naturality.client.portal.end.EndEyeGlow.start(client.level, net.minecraft.core.BlockPos.ZERO);
                    if (naturality.client.portal.end.EndEyeGlow.isActive(net.minecraft.core.BlockPos.ZERO))
                        throw new AssertionError("Disabled eye glow must reject new flashes");
                } finally {
                    effects.smoke = smoke;
                    effects.flames = flames;
                    NaturalityConfig.get().portalChanges.eyeGlow = eye;
                }
            });
        }
    }
}
