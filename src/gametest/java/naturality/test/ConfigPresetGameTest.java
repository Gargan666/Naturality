package naturality.test;
import naturality.config.startup.FeatureConfig.Feature;
import naturality.config.startup.FeatureConfig;
import naturality.config.gameplay.GameplaySettings.NaturalityServerConfig;
import naturality.config.live.NaturalityConfig.ConfigPreset;
import naturality.config.live.NaturalityConfig;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;

final class ConfigPresetGameTest {
    private ConfigPresetGameTest() {}
    static void checkPresets() {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var old = gson.fromJson("{}", FeatureConfig.class);
        for (var feature : Feature.values()) if (!old.get(feature)) throw new AssertionError("Missing old-config default: " + feature);
        for (var preset : ConfigPreset.values()) {
            var client = new NaturalityConfig();
            var server = new NaturalityServerConfig();
            var features = new FeatureConfig();
            client.clouds.base.width = 63;
            client.effects.smoke = false;
            client.liquids.heatWaves = false;
            client.liquids.heatWavePower = 197;
            client.effects.cameraFlare = false;
            client.effects.cameraFlareMaxOpacity = 97;
            server.vanillaPortalEntry = true;
            server.sleepAcceleration = false; server.sleepMaxSpeed = 99; server.sleepRampSeconds = 119;
            preset.apply(client, server, features);
            if (server.sleepMaxSpeed != 200 || server.sleepRampSeconds != 30
                    || server.sleepAcceleration != (preset != ConfigPreset.CLIENTSIDE_ONLY))
                throw new AssertionError("Sleep options must follow preset gameplay defaults");
            if (client.clouds.base.width != new NaturalityConfig().clouds.base.width)
                throw new AssertionError("Presets must reset numeric settings");
            if (preset == ConfigPreset.DEFAULT && !gson.toJson(client).equals(gson.toJson(new NaturalityConfig())))
                throw new AssertionError("Default must exactly match the shipped defaults");
            if (preset == ConfigPreset.DEFAULT && client.clouds.upper.enabled)
                throw new AssertionError("Default must preserve normally disabled options");
            if (preset == ConfigPreset.DEFAULT && !gson.toJson(server).equals(gson.toJson(new NaturalityServerConfig())))
                throw new AssertionError("Default must restore the current server defaults");
            if (client.liquids.heatWavePower != 50 || client.effects.cameraFlareMaxOpacity != 14)
                throw new AssertionError("Presets must restore current heat and flare strength defaults");
            if (preset == ConfigPreset.CLIENTSIDE_ONLY) {
                for (var feature : Feature.values()) if (feature.group != Feature.Group.VISUAL && features.effective(feature))
                    throw new AssertionError("Clientside preset retained server feature: " + feature);
                if (server.portalIntroAnimation || !server.vanillaPortalEntry || server.fireWrapping || server.snowWrapping
                        || server.snowCompaction || server.weatherThaw || server.weatherSnowAccumulation)
                    throw new AssertionError("Clientside preset retained server gameplay");
                if (!gson.toJson(client).equals(gson.toJson(new NaturalityConfig())))
                    throw new AssertionError("Clientside preset must retain default visuals");
            }
            if (preset == ConfigPreset.NO_VISUAL_OVERHAUL) {
                for (var feature : Feature.values()) if (feature.group == Feature.Group.VISUAL && features.effective(feature))
                    throw new AssertionError("Content-only retained visual feature: " + feature);
                if (!features.effective(Feature.CONTENT) || client.effects.smoke || client.lighting.enabled
                        || client.fog.enabled || client.liquids.water || client.clouds.enabled || client.dynamicLighting.enabled)
                    throw new AssertionError("Content-only settings incorrect");
                if (client.liquids.heatWaves || client.effects.cameraFlare || features.effective(Feature.GHAST_PHYSICS)
                        || features.effective(Feature.ARROW_IMPACT_EVENTS) || features.effective(Feature.FIREBALL_IMPACT_EVENTS))
                    throw new AssertionError("Content-only retained newer visual effects or their server animation hooks");
                if (!features.effective(Feature.VILLAGER_WORK) || !features.effective(Feature.END_TERRAIN))
                    throw new AssertionError("Content-only must retain content and gameplay");
            }
            String applied = gson.toJson(client) + gson.toJson(server) + gson.toJson(features);
            preset.apply(client, server, features);
            if (!applied.equals(gson.toJson(client) + gson.toJson(server) + gson.toJson(features)))
                throw new AssertionError("Applying the same preset twice must be stable");
            // Produce fixtures through the real preset implementation for separate-process startup tests.
            try {
                var directory = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                    .resolve("preset-fixtures").resolve(preset.name().toLowerCase(java.util.Locale.ROOT));
                Files.createDirectories(directory);
                Files.writeString(directory.resolve("naturality.json"), gson.toJson(client));
                Files.writeString(directory.resolve("naturality-server.json"), gson.toJson(server));
                Files.writeString(directory.resolve("naturality-features.json"), gson.toJson(features));
            } catch (java.io.IOException error) { throw new AssertionError(error); }
            features.set(Feature.GHAST_SQUASH, false);
            client.effects.leafRestTicks = 17;
            var restored = gson.fromJson(gson.toJson(features), FeatureConfig.class);
            var restoredClient = gson.fromJson(gson.toJson(client), NaturalityConfig.class);
            if (restored.get(Feature.GHAST_SQUASH) || restoredClient.effects.leafRestTicks != 17)
                throw new AssertionError("Individual edits must survive after a preset");
            if (gson.toJson(features).contains("preset") || gson.toJson(client).contains("preset"))
                throw new AssertionError("A preset must not become a persistent override");
        }
    }
}
