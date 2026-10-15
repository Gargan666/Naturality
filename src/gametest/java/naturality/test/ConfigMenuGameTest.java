package naturality.test;

import java.util.ArrayList;
import java.util.List;
import naturality.client.ui.settings.NaturalityCategoryScreen;
import naturality.client.ui.settings.NaturalityCategoryScreen.NaturalityConfigScreen;
import naturality.config.live.NaturalityConfig;
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

    private static void checkSearch(GuiEventListener screen, String query, long expected) {
        search(screen).setValue(query);
        long actual = resets(screen);
        if (actual != expected) throw new AssertionError("Search '" + query + "': expected " + expected + " options, got " + actual);
    }

    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.gui.setScreen(new NaturalityConfigScreen(new net.minecraft.client.gui.screens.TitleScreen()));
            if (list(client.gui.screen()).children().size() != 6) throw new AssertionError("Expected epilepsy mode, presets, visuals, content, gameplay and audio");
            press(client.gui.screen(), "Config Presets");
            for (String name : List.of("Default", "Clientside Only", "No Visual Overhaul"))
                if (descendants(client.gui.screen()).stream().noneMatch(e -> e instanceof Button b && b.getMessage().getString().equals(name)))
                    throw new AssertionError("Missing preset: " + name);
            press(client.gui.screen(), "Clientside Only");
            if (naturality.config.startup.FeatureConfig.get().get(naturality.config.startup.FeatureConfig.Feature.CONTENT)
                    || !naturality.config.gameplay.GameplaySettings.NaturalityServerConfig.get().vanillaPortalEntry)
                throw new AssertionError("Preset button must apply ordinary content and portal settings");
            search(client.gui.screen()).setValue("ghast squash");
            press(client.gui.screen(), "Ghast squash and movement:");
            if (naturality.config.startup.FeatureConfig.get().get(naturality.config.startup.FeatureConfig.Feature.GHAST_SQUASH))
                throw new AssertionError("Individual settings must remain editable after applying a preset");
            search(client.gui.screen()).setValue("");
            press(client.gui.screen(), "No Visual Overhaul");
            if (!naturality.config.startup.FeatureConfig.get().get(naturality.config.startup.FeatureConfig.Feature.CONTENT)
                    || NaturalityConfig.get().lighting.enabled)
                throw new AssertionError("Content-only button must retain content and disable visuals");
            press(client.gui.screen(), "Default");
            if (!NaturalityConfig.get().lighting.enabled || NaturalityConfig.get().clouds.upper.enabled)
                throw new AssertionError("Default button must use exact shipped defaults");
            TestFixtures.screen(client).onClose();
            press(client.gui.screen(), "Gameplay");
            press(client.gui.screen(), "Block and Snow Rules");
            if (resets(client.gui.screen()) != 8) throw new AssertionError("Block rules must include snow generation, sand, three local server settings and three sleep controls");
            TestFixtures.screen(client).onClose();
            press(client.gui.screen(), "Weather and Sky Rules");
            if (resets(client.gui.screen()) != 6) throw new AssertionError("Weather rules must include simulation and snow accumulation");
            TestFixtures.screen(client).onClose();
            press(client.gui.screen(), "Movement and Portal Travel");
            if (resets(client.gui.screen()) != 5) throw new AssertionError("Travel rules must include wind, elytra, navigation instruments and both portal settings");
            TestFixtures.screen(client).onClose();
            TestFixtures.screen(client).onClose();
            press(client.gui.screen(), "Audio");
            if (resets(client.gui.screen()) != 8) throw new AssertionError("Audio must collect all ambience, wind, splash and portal volume settings");
            TestFixtures.screen(client).onClose();
            press(client.gui.screen(), "Visuals");
            press(client.gui.screen(), "Portals");
            if (resets(client.gui.screen()) != 8) throw new AssertionError("Portal appearance should exclude relocated audio");
            search(client.gui.screen()).setValue("SUN BEAMS");
            if (resets(client.gui.screen()) != 1) throw new AssertionError("Search must include other categories, ignoring case");
            search(client.gui.screen()).setValue("ghast squash");
            if (resets(client.gui.screen()) != 1) throw new AssertionError("New visual options must be searchable");
            int savedHeatPower = NaturalityConfig.get().liquids.heatWavePower;
            NaturalityConfig.get().liquids.heatWavePower = 200;
            search(client.gui.screen()).setValue("heat wave power");
            if (resets(client.gui.screen()) != 1 || descendants(client.gui.screen()).stream().noneMatch(e ->
                    e instanceof net.minecraft.client.gui.components.AbstractSliderButton b
                        && b.getMessage().getString().equals("Heat wave power: 200%")))
                throw new AssertionError("Heat power must have a searchable slider reaching twice the original power");
            press(client.gui.screen(), "Reset");
            if (NaturalityConfig.get().liquids.heatWavePower != 50) throw new AssertionError("Heat reset must restore reduced power");
            NaturalityConfig.get().liquids.heatWavePower = savedHeatPower;
            search(client.gui.screen()).setValue("heat waves");
            if (resets(client.gui.screen()) != 1) throw new AssertionError("Heat toggle must be searchable");
            boolean savedHeat = NaturalityConfig.get().liquids.heatWaves;
            press(client.gui.screen(), "Heat waves:");
            if (NaturalityConfig.get().liquids.heatWaves == savedHeat) throw new AssertionError("Heat toggle must change activation");
            press(client.gui.screen(), "Reset");
            if (!NaturalityConfig.get().liquids.heatWaves) throw new AssertionError("Heat toggle reset must enable the effect");
            NaturalityConfig.get().liquids.heatWaves = savedHeat;
            search(client.gui.screen()).setValue("End Wilds biome");
            if (resets(client.gui.screen()) != 1) throw new AssertionError("New content options must be searchable");
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
            if (resets(client.gui.screen()) != 8) throw new AssertionError("Clearing search must restore Portals");
            TestFixtures.screen(client).onClose();
            press(client.gui.screen(), "Sky and Atmosphere");
            press(client.gui.screen(), "Weather");
            if (resets(client.gui.screen()) != 6) throw new AssertionError("Weather appearance must combine startup visuals and live controls");
            search(client.gui.screen()).setValue("overworld override rain");
            if (resets(client.gui.screen()) != 0) throw new AssertionError("World weather overrides must not be editable in config");
            search(client.gui.screen()).setValue("snow wrapping");
            if (resets(client.gui.screen()) != 1) throw new AssertionError("Snow gameplay toggle must be globally searchable");
            search(client.gui.screen()).setValue("glint");
            if (resets(client.gui.screen()) != 5) throw new AssertionError("All glint controls must be searchable");
            search(client.gui.screen()).setValue("");
            TestFixtures.screen(client).onClose();
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
            checkSearch(client.gui.screen(), "#Medium", 5);
            search(client.gui.screen()).setValue("");
            if (resets(client.gui.screen()) != 23 || options.scrollAmount() != scroll)
                throw new AssertionError("Clearing an impact filter must restore the previous page and scroll");
            search(client.gui.screen()).setValue("glow");
        });
        context.waitTicks(2);
        context.takeScreenshot("config-global-search");
        context.runOnClient(client -> {
            search(client.gui.screen()).setValue("");
            TestFixtures.screen(client).onClose();
            TestFixtures.screen(client).onClose();
            TestFixtures.screen(client).onClose();
        });
        context.takeScreenshot("config-main-categories");
        context.runOnClient(client -> press(client.gui.screen(), "Config Presets"));
        context.waitTicks(2);
        context.takeScreenshot("config-presets");
        context.runOnClient(client -> {
            // Every feature remains reachable once, irrespective of its storage group.
            for (var feature : naturality.config.startup.FeatureConfig.Feature.values()) {
                var label = net.minecraft.network.chat.Component.translatable("naturality.config.feature." + feature.key()).getString();
                search(client.gui.screen()).setValue(label);
                long count = descendants(client.gui.screen()).stream().filter(e -> e instanceof net.minecraft.client.gui.components.AbstractButton b
                    && b.getMessage().getString().startsWith(label + ":")).count();
                if (count != 1) throw new AssertionError("Expected one editable search result for " + feature + ", got " + count);
            }
            checkSearch(client.gui.screen(), "#Low", 6);
            checkSearch(client.gui.screen(), "  #mEdIuM  ", 5);
            checkSearch(client.gui.screen(), "#High", 3);
            for (String tag : List.of("#ExtraHigh", "#Extra High", "#extra_high", "#extra-high"))
                checkSearch(client.gui.screen(), tag, 1);
            checkSearch(client.gui.screen(), "#Low #Medium", 11);
            checkSearch(client.gui.screen(), "#High #ExtraHigh", 4);
            checkSearch(client.gui.screen(), "cloud #Medium", 1);
            checkSearch(client.gui.screen(), "#High dynamic lighting", 0);
            checkSearch(client.gui.screen(), "#High heat wave power", 0);
            checkSearch(client.gui.screen(), "#Low heat", 0);
            checkSearch(client.gui.screen(), "#Unknown", 0);
            checkSearch(client.gui.screen(), "#High #Unknown", 0);
            checkSearch(client.gui.screen(), "#High fire heat", 1);
            checkSearch(client.gui.screen(), "#High heat", 1);
            press(client.gui.screen(), "Heat waves:");
            if (NaturalityConfig.get().liquids.heatWaves) throw new AssertionError("Impact-filtered live toggle must remain editable");
            checkSearch(client.gui.screen(), "#High", 3);
            checkSearch(client.gui.screen(), "#High heat", 1);
            press(client.gui.screen(), "Reset");
            if (!NaturalityConfig.get().liquids.heatWaves) throw new AssertionError("Impact-filtered reset must restore the live option");
            checkSearch(client.gui.screen(), "#Low ghast", 1);
            press(client.gui.screen(), "Ghast squash and movement:");
            if (naturality.config.startup.FeatureConfig.get().get(naturality.config.startup.FeatureConfig.Feature.GHAST_SQUASH))
                throw new AssertionError("Impact-filtered startup toggle must remain editable");
            press(client.gui.screen(), "Reset");
            if (!naturality.config.startup.FeatureConfig.get().get(naturality.config.startup.FeatureConfig.Feature.GHAST_SQUASH))
                throw new AssertionError("Impact-filtered reset must restore the startup option");
            checkSearch(client.gui.screen(), "#Medium", 5);
        });
        context.waitTicks(2);
        context.takeScreenshot("config-impact-search");
        context.runOnClient(client -> {
            search(client.gui.screen()).setValue("heat waves");
            var tooltip = naturality.client.ui.settings.NaturalityCategoryScreen.VisualPerformance.tooltip("naturality.config.liquids.heatWaves").getString();
            if (!tooltip.contains("Estimated FPS impact: High") || !tooltip.contains("not a measured average") || tooltip.contains("naturality.config."))
                throw new AssertionError("Performance guidance must disclose its estimate and uncertainty");
            if (naturality.client.ui.settings.NaturalityCategoryScreen.VisualPerformance.estimate("effects.bubblePops") != null)
                throw new AssertionError("Unrated settings must not receive invented performance labels");
            for (var impact : naturality.client.ui.settings.NaturalityCategoryScreen.VisualPerformance.Impact.values())
                if (net.minecraft.network.chat.Component.translatable("naturality.config.performance." + impact.name().toLowerCase(java.util.Locale.ROOT))
                    .getString().startsWith("naturality.")) throw new AssertionError("Untranslated impact tier");
            var heat = descendants(client.gui.screen()).stream()
                .filter(e -> e instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getMessage().getString().startsWith("Heat waves:"))
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).findFirst().orElseThrow();
            client.setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
            heat.setFocused(true);
            heat.setTooltipDelay(java.time.Duration.ZERO);
        });
        context.waitTicks(3);
        context.takeScreenshot("config-performance-estimate");
        context.runOnClient(client -> { TestFixtures.screen(client).onClose(); TestFixtures.screen(client).onClose(); });

        // Exercise compilation of both sides of the newly configurable shader paths.
        context.runOnClient(client -> {
            client.gui.setScreen(new NaturalityCategoryScreen(new net.minecraft.client.gui.screens.TitleScreen(), NaturalityCategoryScreen.Category.ROOT));
            NaturalityConfig.get().lighting.enabled = false;
            NaturalityConfig.get().effects.starPulses = false;
            NaturalityConfig.get().effects.pixelGlint = false;
            NaturalityConfig.get().liquids.emissiveLava = false;
            NaturalityConfig.get().effects.fireAnimation = false;
            NaturalityConfig.get().portalChanges.endParallax = false;
            TestFixtures.screen(client).onClose();
        });
        context.waitFor(client -> client.gui.overlay() == null);
        context.runOnClient(client -> {
            client.gui.setScreen(new NaturalityCategoryScreen(new net.minecraft.client.gui.screens.TitleScreen(), NaturalityCategoryScreen.Category.ROOT));
            NaturalityConfig.get().lighting.enabled = true;
            NaturalityConfig.get().effects.starPulses = true;
            NaturalityConfig.get().effects.pixelGlint = true;
            NaturalityConfig.get().liquids.emissiveLava = true;
            NaturalityConfig.get().effects.fireAnimation = true;
            NaturalityConfig.get().portalChanges.endParallax = true;
            TestFixtures.screen(client).onClose();
        });
        context.waitFor(client -> client.gui.overlay() == null);

        var gson = new com.google.gson.Gson();
        var defaults = gson.fromJson("{}", NaturalityConfig.class);
        if (!defaults.liquids.heatWaves) throw new AssertionError("Existing configs must keep heat waves enabled");
        if (defaults.liquids.heatWavePower != 50) throw new AssertionError("Old configs must receive reduced heat power");
        defaults.liquids.heatWavePower = -1; defaults.liquids.sanitize();
        if (defaults.liquids.heatWavePower != 0) throw new AssertionError("Heat power must allow disabling");
        defaults.liquids.heatWavePower = 300; defaults.liquids.sanitize();
        if (gson.fromJson(gson.toJson(defaults), NaturalityConfig.class).liquids.heatWavePower != 200)
            throw new AssertionError("Heat power must clamp at twice original strength and persist");
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

        try (var world = TestWorlds.disposable(context).create()) {
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> {
                var effects = NaturalityConfig.get().effects;
                boolean worldContours=effects.worldGlintContours, handContours=effects.handGlintContours,
                    previewContours=effects.previewGlintContours, weatherParticles=effects.weatherParticles;
                try {
                    effects.worldGlintContours=effects.handGlintContours=effects.previewGlintContours=false;
                    effects.weatherParticles=false;
                    if (naturality.client.systems.weather.overworld.precipitation.ParticleWeather.enabled(TestFixtures.level(client))) throw new AssertionError("Disabled precipitation must restore vanilla sheets");
                    var texture=net.minecraft.resources.Identifier.withDefaultNamespace("textures/item/diamond_sword.png");
                    naturality.client.rendering.glint.GlintContours.beginFrame();
                    if (naturality.client.rendering.glint.GlintContours.armor(texture,true)!=null) throw new AssertionError("Disabled world outlines captured geometry");
                    naturality.client.rendering.glint.GlintContours.beginHandFrame();
                    if (naturality.client.rendering.glint.GlintContours.armor(texture,true)!=null) throw new AssertionError("Disabled hand outlines captured geometry");
                    naturality.client.rendering.glint.GlintContours.beginPreview(1);
                    if (naturality.client.rendering.glint.GlintContours.armor(texture,true)!=null) throw new AssertionError("Disabled portrait outlines captured geometry");
                } finally {
                    naturality.client.rendering.glint.GlintContours.endFrame();
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
                    naturality.client.portals.end.EndPortalBorderState.EndEyeGlow.start(TestFixtures.level(client), net.minecraft.core.BlockPos.ZERO);
                    if (naturality.client.portals.end.EndPortalBorderState.EndEyeGlow.isActive(net.minecraft.core.BlockPos.ZERO))
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
