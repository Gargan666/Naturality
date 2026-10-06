package naturality;

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
		NaturalityBlocks.initialize();
		naturality.worldgen.LirestoneBoulderFeature.initialize();
		NaturalityEffects.initialize();
        naturality.starfall.StarfallEntities.initialize();
        naturality.config.GameplaySettings.initialize();
        naturality.weather.WeatherSystem.initialize();
        naturality.sky.SkyEvents.initialize();
        naturality.snow.SnowCompaction.initialize();
        naturality.snow.SnowLighting.initialize();
		NaturalitySounds.initialize();
        NaturalityParticles.initialize();
        naturality.villager.VillagerBobbers.initialize();
        naturality.villager.FishingSpots.initialize();
        naturality.villager.Reputation.initialize();
		naturality.portal.PortalOpeningManager.initialize();
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}



