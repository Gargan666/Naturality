package naturality.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import naturality.client.portals.nether.glow.PortalGlowController;
import naturality.client.portals.nether.glow.PortalGlowGeometry.PortalGlowRenderLayer;
import static naturality.config.startup.FeatureConfig.Feature.*;

public class NaturalityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        naturality.client.systems.enchanting.EnchantingClient.initialize();
        naturality.client.particles.sparks.SparkParticle.initialize();
        net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(
            java.util.List.of(net.minecraft.client.color.block.BlockTintSources.foliage(),
                net.minecraft.client.color.block.BlockTintSources.foliage()), naturality.bootstrap.ContentRegistration.NaturalityBlocks.CLOVERS,
                naturality.bootstrap.ContentRegistration.NaturalityBlocks.FOUR_LEAF_CLOVERS);
        naturality.client.ui.menus.previews.MenuPreview.MenuWorld.initialize();
        naturality.client.systems.navigation.NavigationClient.initialize();
        if (VISUAL_OVERHAUL.enabled()) naturality.client.entities.endcrystals.CrystalVisuals.CrystalTextParticle.initialize();
        if (VISUAL_OVERHAUL.enabled()) naturality.client.entities.fish.FishSwim.initialize();
        if (VISUAL_OVERHAUL.enabled()) naturality.client.entities.zombies.ZombieWalk.initialize();
        naturality.client.systems.sleep.SleepVisuals.initialize();
        naturality.client.ui.accessibility.EpilepsyWarning.initialize();
        naturality.client.systems.weather.overworld.lightning.DistantLightningClient.LightningFlashes.initialize();
        if (VISUAL_OVERHAUL.enabled() || GHAST_SQUASH.enabled() || GHAST_PHYSICS.enabled()) naturality.client.entities.floatingmobtilt.FloatingMobTilt.initialize();
        naturality.client.entities.arrows.MobArrowLayer.MobArrowImpacts.initialize();
        naturality.client.entities.fireballs.FireballModelRenderer.FireballImpactClient.initialize();
        if (GHAST_SQUASH.enabled()) naturality.client.entities.ghasts.GhastSquash.initialize();
        if (GHAST_SQUASH.enabled() || GHAST_PHYSICS.enabled()) {
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.HAPPY_GHAST, naturality.client.entities.ghasts.GhastSquash.LeaningHappyGhastRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.GHAST, naturality.client.entities.ghasts.GhastSquash.SquashingGhastRenderer::new);
        }
        if (FIREBALL_MODELS.enabled()) {
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.DRAGON_FIREBALL,
                context -> new naturality.client.entities.fireballs.FireballModelRenderer<>(context, 2.0F, false));
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.FIREBALL,
                context -> new naturality.client.entities.fireballs.FireballModelRenderer<>(context, 2.0F));
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.SMALL_FIREBALL,
                context -> new naturality.client.entities.fireballs.FireballModelRenderer<>(context, 0.625F));
        }
        if (CONTENT.enabled()) {
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                naturality.entities.maddened.MaddenedZombies.TYPE, naturality.client.entities.maddened.MaddenedZombieRenderer::new);
            naturality.client.effects.insanity.Insanity.initialize();
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                naturality.entities.maddened.MaddenedCreepers.TYPE, naturality.client.entities.maddened.MaddenedCreeperRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                naturality.systems.fishing.VillagerBobbers.TYPE, naturality.client.systems.fishing.FishingLineRendering.VillagerBobberRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                naturality.systems.fishing.VillagerBobbers.PLAYER_RETURN, naturality.client.systems.fishing.FishingLineRendering.PlayerFishingReturnRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(naturality.systems.weather.end.starfall.StarfallWeather.StarfallEntities.STAR,naturality.client.systems.weather.end.starfall.FallingStarRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(naturality.systems.weather.end.starfall.StarfallWeather.StarfallEntities.FIZZLE,naturality.client.systems.weather.end.starfall.FallingStarRenderer::new);
            naturality.client.systems.weather.end.starfall.FallingStarRenderer.StarTrailParticle.initialize();
        }
        if (CONFUSION.enabled()) {
            ClientTickEvents.END_CLIENT_TICK.register(naturality.client.effects.confusion.ConfusionSlots::tick);
            ClientTickEvents.END_CLIENT_TICK.register(naturality.client.effects.confusion.ConfusionSlots.OffhandTransfer::tick);
        }
        naturality.client.config.gameplay.GameplaySettingsClient.initialize();
        naturality.client.systems.weather.framework.WeatherClient.initialize();
        naturality.client.systems.skyevents.framework.SkyEventsClient.initialize();
        naturality.client.systems.event.framework.EventsClient.initialize();
        naturality.client.portals.nether.travel.PortalCrossingClient.initialize();
        naturality.client.portals.nether.opening.PortalOpeningClient.initialize();
        // Registered content still has its necessary renderers and effect UI in content-only play.
        if (CONTENT.enabled() || VISUAL_OVERHAUL.enabled()) naturality.client.api.rendering.layers.SurfaceLayerModel.initialize();
        if (!VISUAL_OVERHAUL.enabled()) return;
        naturality.client.api.rendering.ShaderPreparation.initialize();
        naturality.client.fluids.water.WaterComposite.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(naturality.client.fluids.water.OceanBubbles::tick);
        naturality.client.rendering.heatwaves.HeatWaves.initialize();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> naturality.client.rendering.breaking.BreakingTextures.reset());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> naturality.client.rendering.shadows.BlockGridShadow.reset());
        if (END_AMBIENCE.enabled()) naturality.client.audio.ambience.end.EndAmbience.initialize();
        naturality.client.audio.ambience.caves.CaveAmbience.initialize();
        naturality.client.effects.ambienthorror.AmbientHorror.initialize();
        naturality.client.sky.overworld.meteors.MeteorShower.initialize();
        naturality.client.sky.overworld.rainbow.RainbowRenderer.initialize();
        naturality.client.sky.shared.aurora.AuroraRenderer.initialize();
        naturality.client.systems.event.overworld.bloodmoon.BloodMoonClient.initialize();
        naturality.client.systems.weather.overworld.precipitation.ParticleWeather.initialize();
        if (RISE_PARTICLES.enabled()) naturality.client.systems.weather.end.rise.RiseWeather.initialize();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> naturality.client.systems.weather.wind.WindRendering.close());
        naturality.client.blocks.fire.SurfaceFireModel.initialize();
        naturality.client.systems.weather.wind.WindRendering.FoliageWindModel.initialize();
        naturality.client.rendering.distantdetail.RenderDetail.initialize();
        naturality.client.rendering.lighting.dynamic.DynamicLighting.BellLightingModel.initialize();
        naturality.client.rendering.distantdetail.RenderDetail.DistanceModel.initialize();
        naturality.client.blocks.fire.ProceduralFire.initialize();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> naturality.client.blocks.glass.GlassShine.close());
        naturality.client.fluids.shared.ProceduralFluids.initialize();
        naturality.client.fluids.water.WaterVisuals.initialize();
        naturality.client.fluids.water.WaterIntersection.initialize();
        naturality.client.fluids.lava.LavaIntersection.initialize();
        naturality.client.systems.sleep.SleepVisuals.register(naturality.client.fluids.lava.LavaIntersection.LavaRipples::tick);
        naturality.client.systems.sleep.SleepVisuals.register(naturality.client.fluids.lava.LavaIntersection.LavaEntrySplash::tick);
        naturality.client.fluids.lava.LavaIntersection.LavaSplashParticle.initialize();
        naturality.client.fluids.lava.LavaIntersection.LavafallParticle.initialize();
        naturality.client.systems.sleep.SleepVisuals.register(naturality.client.fluids.lava.LavaIntersection.LavafallSplash::tick);
        naturality.client.systems.sleep.SleepVisuals.register(naturality.client.fluids.water.WaterIntersection.WaterRipples::tick);
        naturality.client.fluids.water.WaterIntersection.WaterfallParticle.initialize();
        naturality.client.fluids.water.WaterShaders.WaterParticleTint.initialize();
        naturality.client.fluids.water.WaterIntersection.UnderwaterMoteParticle.initialize();
        naturality.client.systems.weather.overworld.rain.RainParticleTint.initialize();
        naturality.client.systems.sleep.SleepVisuals.register(naturality.client.fluids.water.WaterIntersection.WaterfallSplash::tick);
        naturality.client.systems.sleep.SleepVisuals.register(naturality.client.fluids.water.WaterIntersection.WaterEntrySplash::tick);
        naturality.client.particles.smoke.SmokeParticle.initialize();
        naturality.client.particles.potionclouds.FlatPotionSmokeParticle.initialize();
        if (DEATH_SMOKE.enabled()) naturality.client.particles.deathsmoke.DeathSmoke.DeathSmokeParticle.initialize();
        naturality.client.particles.flame.AnimatedFlameParticle.initialize();
        if (EXPLOSION_PARTICLES.enabled()) naturality.client.particles.explosions.FlatExplosionParticle.initialize();
        naturality.client.sky.shared.clouds.CloudPipelines.initialize();
		naturality.client.systems.sleep.SleepVisuals.register(naturality.client.rendering.lighting.dynamic.DynamicLighting::tick);
        naturality.client.portals.end.EndPortalBorderLayers.initialize();
        naturality.client.portals.end.EndPortalBorderLayers.EndEyeGlowLayer.initialize();
        naturality.client.systems.sleep.SleepVisuals.register(naturality.client.portals.end.EndPortalBorderState.EndEyeGlow::tick);
		naturality.client.rendering.fog.EndFogComposite.initialize();
		naturality.client.portals.nether.travel.PortalCrossingClient.PortalOverlay.initialize();
		naturality.client.systems.sleep.SleepVisuals.register(naturality.client.portals.nether.opening.PortalOpeningClient::tick);
		PortalGlowRenderLayer.initialize();
		naturality.client.systems.sleep.SleepVisuals.register(PortalGlowController::tick);
	}
}










