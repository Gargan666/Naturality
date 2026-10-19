package naturality;
import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import naturality.bootstrap.ContentRegistration.NaturalityEffects;
import naturality.bootstrap.ContentRegistration.NaturalityItems;
import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import naturality.bootstrap.ContentRegistration.NaturalitySounds;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Naturality implements ModInitializer {
	public static final String MOD_ID = "naturality";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        naturality.systems.rotation.PlayerRotation.initialize();
        naturality.systems.navigation.NavigationInstruments.NavigationTargetPayload.initialize();
        if (naturality.config.startup.FeatureConfig.Feature.NAVIGATION_INSTRUMENTS.enabled()) naturality.systems.navigation.NavigationInstruments.initialize();
        naturality.entities.arrows.ArrowImpactPayload.initialize();
        naturality.config.startup.FeatureConfig.get().save();
        if (naturality.config.startup.FeatureConfig.Feature.CONTENT.enabled()) {
            NaturalityBlocks.initialize();
            NaturalityItems.initialize();
            naturality.worldgen.overworld.clover.CloverPatchesFeature.initialize();
            naturality.worldgen.overworld.sand.SandLayersFeature.initialize();
            naturality.worldgen.nether.basaltdeltas.BasaltDeltasFeature.initialize();
            naturality.worldgen.overworld.moss.TreeMossFeature.initialize();
            naturality.worldgen.overworld.waterfalls.MountainWaterfallFeature.initialize();
            naturality.entities.maddened.MaddenedCreepers.initialize();
            naturality.entities.maddened.MaddenedZombies.initialize();
            naturality.worldgen.end.lirestonecrags.LirestoneCrags.initialize();
            naturality.worldgen.end.endwilds.EndWilds.initialize();
            naturality.worldgen.end.lirestonecrags.LirestoneCrags.LirestoneBoulderFeature.initialize();
            NaturalityEffects.initialize();
            naturality.systems.weather.end.starfall.StarfallWeather.StarfallEntities.initialize();
            naturality.systems.fishing.VillagerBobbers.initialize();
        }
        naturality.config.gameplay.GameplaySettings.initialize();
        naturality.systems.sleep.SleepAcceleration.initialize();
        naturality.entities.fireballs.FireballImpactPayload.initialize();
        naturality.systems.weather.framework.WeatherSystem.initialize();
        naturality.systems.skyevents.framework.SkyEvents.initialize();
        naturality.systems.event.framework.Events.initialize();
        naturality.blocks.snow.SnowGeometry.SnowCompaction.initialize();
        naturality.blocks.snow.SnowGeometry.SnowLighting.initialize();
		NaturalitySounds.initialize();
        NaturalityParticles.initialize();
        if (naturality.config.startup.FeatureConfig.Feature.VILLAGER_WORK.enabled()) naturality.systems.fishing.VillagerBobbers.FishingSpots.initialize();
        if (naturality.config.startup.FeatureConfig.Feature.REPUTATION.enabled()) naturality.systems.reputation.Reputation.initialize();
		naturality.portals.nether.opening.PortalOpeningManager.initialize();
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}



