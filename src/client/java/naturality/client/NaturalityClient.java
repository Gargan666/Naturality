package naturality.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import naturality.client.particle.PortalGlowController;
import naturality.client.particle.PortalGlowRenderLayer;
import static naturality.config.Feature.*;

public class NaturalityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(
            java.util.List.of(net.minecraft.client.color.block.BlockTintSources.foliage(),
                net.minecraft.client.color.block.BlockTintSources.foliage()), naturality.NaturalityBlocks.CLOVERS);
        naturality.client.menu.MenuWorld.initialize();
        naturality.client.navigation.NavigationClient.initialize();
        if (VISUAL_OVERHAUL.enabled()) naturality.client.particle.CrystalTextParticle.initialize();
        if (VISUAL_OVERHAUL.enabled()) naturality.client.entity.FishSwim.initialize();
        if (VISUAL_OVERHAUL.enabled()) naturality.client.entity.ZombieWalk.initialize();
        naturality.client.sleep.SleepVisuals.initialize();
        naturality.client.config.EpilepsyWarning.initialize();
        naturality.client.weather.LightningFlashes.initialize();
        if (VISUAL_OVERHAUL.enabled() || GHAST_SQUASH.enabled() || GHAST_PHYSICS.enabled()) naturality.client.entity.FloatingMobTilt.initialize();
        naturality.client.entity.MobArrowImpacts.initialize();
        naturality.client.particle.FireballImpactClient.initialize();
        if (GHAST_SQUASH.enabled()) naturality.client.entity.GhastSquash.initialize();
        if (GHAST_SQUASH.enabled() || GHAST_PHYSICS.enabled()) {
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.HAPPY_GHAST, naturality.client.entity.LeaningHappyGhastRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.GHAST, naturality.client.entity.SquashingGhastRenderer::new);
        }
        if (FIREBALL_MODELS.enabled()) {
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.DRAGON_FIREBALL,
                context -> new naturality.client.entity.FireballModelRenderer<>(context, 2.0F, false));
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.FIREBALL,
                context -> new naturality.client.entity.FireballModelRenderer<>(context, 2.0F));
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.SMALL_FIREBALL,
                context -> new naturality.client.entity.FireballModelRenderer<>(context, 0.625F));
        }
        if (CONTENT.enabled()) {
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                naturality.entity.MaddenedZombies.TYPE, naturality.client.entity.MaddenedZombieRenderer::new);
            naturality.client.insanity.Insanity.initialize();
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                naturality.entity.MaddenedCreepers.TYPE, naturality.client.entity.MaddenedCreeperRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                naturality.villager.VillagerBobbers.TYPE, naturality.client.villager.VillagerBobberRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                naturality.villager.VillagerBobbers.PLAYER_RETURN, naturality.client.villager.PlayerFishingReturnRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(naturality.starfall.StarfallEntities.STAR,naturality.client.entity.FallingStarRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(naturality.starfall.StarfallEntities.FIZZLE,naturality.client.entity.FallingStarRenderer::new);
            naturality.client.particle.StarTrailParticle.initialize();
        }
        if (CONFUSION.enabled()) {
            ClientTickEvents.END_CLIENT_TICK.register(naturality.client.confusion.ConfusionSlots::tick);
            ClientTickEvents.END_CLIENT_TICK.register(naturality.client.confusion.OffhandTransfer::tick);
        }
        naturality.client.config.GameplaySettingsClient.initialize();
        naturality.client.weather.WeatherClient.initialize();
        naturality.client.sky.SkyEventsClient.initialize();
        naturality.client.portal.PortalCrossingClient.initialize();
        naturality.client.portal.PortalOpeningClient.initialize();
        // Registered content still has its necessary renderers and effect UI in content-only play.
        if (!VISUAL_OVERHAUL.enabled()) return;
        naturality.client.render.ShaderPreparation.initialize();
        naturality.client.fluid.WaterComposite.initialize();
        naturality.client.fluid.HeatWaves.initialize();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> naturality.client.breaking.BreakingTextures.reset());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> naturality.client.shadow.BlockGridShadow.reset());
        if (END_AMBIENCE.enabled()) naturality.client.sound.EndAmbience.initialize();
        naturality.client.sound.CaveAmbience.initialize();
        naturality.client.horror.AmbientHorror.initialize();
        naturality.client.sky.MeteorShower.initialize();
        naturality.client.sky.RainbowRenderer.initialize();
        naturality.client.sky.AuroraRenderer.initialize();
        naturality.client.sky.BloodMoonClient.initialize();
        naturality.client.weather.ParticleWeather.initialize();
        if (RISE_PARTICLES.enabled()) naturality.client.weather.RiseWeather.initialize();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> naturality.client.weather.WindRendering.close());
        naturality.client.fire.SurfaceFireModel.initialize();
        naturality.client.snow.SurfaceSnowModel.initialize();
        naturality.client.weather.FoliageWindModel.initialize();
        naturality.client.render.RenderDetail.initialize();
        naturality.client.lighting.BellLightingModel.initialize();
        naturality.client.render.DistanceModel.initialize();
        naturality.client.fire.ProceduralFire.initialize();
        naturality.client.fluid.ProceduralFluids.initialize();
        naturality.client.fluid.WaterVisuals.initialize();
        naturality.client.fluid.WaterImmersionOverlay.initialize();
        naturality.client.fluid.WaterIntersection.initialize();
        naturality.client.fluid.LavaIntersection.initialize();
        naturality.client.sleep.SleepVisuals.register(naturality.client.fluid.LavaRipples::tick);
        naturality.client.sleep.SleepVisuals.register(naturality.client.fluid.LavaEntrySplash::tick);
        naturality.client.particle.LavaSplashParticle.initialize();
        naturality.client.particle.LavafallParticle.initialize();
        naturality.client.sleep.SleepVisuals.register(naturality.client.fluid.LavafallSplash::tick);
        naturality.client.sleep.SleepVisuals.register(naturality.client.fluid.WaterRipples::tick);
        naturality.client.particle.WaterfallParticle.initialize();
        naturality.client.particle.WaterParticleTint.initialize();
        naturality.client.particle.UnderwaterMoteParticle.initialize();
        naturality.client.particle.RainParticleTint.initialize();
        naturality.client.sleep.SleepVisuals.register(naturality.client.fluid.WaterfallSplash::tick);
        naturality.client.sleep.SleepVisuals.register(naturality.client.fluid.WaterEntrySplash::tick);
        naturality.client.particle.SmokeParticle.initialize();
        naturality.client.particle.FlatPotionSmokeParticle.initialize();
        if (DEATH_SMOKE.enabled()) naturality.client.particle.DeathSmokeParticle.initialize();
        naturality.client.particle.AnimatedFlameParticle.initialize();
        if (EXPLOSION_PARTICLES.enabled()) naturality.client.particle.FlatExplosionParticle.initialize();
        naturality.client.cloud.CloudPipelines.initialize();
		naturality.client.sleep.SleepVisuals.register(naturality.client.lighting.DynamicLighting::tick);
        naturality.client.portal.end.EndPortalBorderLayers.initialize();
        naturality.client.portal.end.EndEyeGlowLayer.initialize();
        naturality.client.sleep.SleepVisuals.register(naturality.client.portal.end.EndEyeGlow::tick);
		naturality.client.fog.EndFogComposite.initialize();
		naturality.client.portal.PortalOverlay.initialize();
		naturality.client.sleep.SleepVisuals.register(naturality.client.portal.PortalOpeningClient::tick);
		PortalGlowRenderLayer.initialize();
		naturality.client.sleep.SleepVisuals.register(PortalGlowController::tick);
	}
}










