package naturality.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import naturality.client.particle.PortalGlowController;
import naturality.client.particle.PortalGlowRenderLayer;

public class NaturalityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        naturality.client.config.GameplaySettingsClient.initialize();
        naturality.client.weather.WeatherClient.initialize();
        naturality.client.sky.SkyEventsClient.initialize();
        naturality.client.sky.MeteorShower.initialize();
        naturality.client.sky.RainbowRenderer.initialize();
        naturality.client.sky.AuroraRenderer.initialize();
        naturality.client.weather.ParticleWeather.initialize();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> naturality.client.weather.WindRendering.close());
        naturality.client.fire.SurfaceFireModel.initialize();
        naturality.client.snow.SurfaceSnowModel.initialize();
        naturality.client.weather.FoliageWindModel.initialize();
        naturality.client.weather.FoliageWindModel.initialize();
        naturality.client.fire.ProceduralFire.initialize();
        naturality.client.fluid.ProceduralFluids.initialize();
        naturality.client.fluid.WaterVisuals.initialize();
        naturality.client.fluid.WaterImmersionOverlay.initialize();
        naturality.client.fluid.WaterIntersection.initialize();
        naturality.client.fluid.LavaIntersection.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(naturality.client.fluid.LavaRipples::tick);
        ClientTickEvents.END_CLIENT_TICK.register(naturality.client.fluid.LavaEntrySplash::tick);
        naturality.client.particle.LavaSplashParticle.initialize();
        naturality.client.particle.LavafallParticle.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(naturality.client.fluid.LavafallSplash::tick);
        ClientTickEvents.END_CLIENT_TICK.register(naturality.client.fluid.WaterRipples::tick);
        naturality.client.particle.WaterfallParticle.initialize();
        naturality.client.particle.WaterParticleTint.initialize();
        naturality.client.particle.RainParticleTint.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(naturality.client.fluid.WaterfallSplash::tick);
        ClientTickEvents.END_CLIENT_TICK.register(naturality.client.fluid.WaterEntrySplash::tick);
        naturality.client.particle.SmokeParticle.initialize();
        naturality.client.particle.AnimatedFlameParticle.initialize();
        naturality.client.cloud.CloudPipelines.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(naturality.client.lighting.DynamicLighting::tick);
        naturality.client.portal.end.EndPortalBorderLayers.initialize();
        naturality.client.portal.end.EndEyeGlowLayer.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(naturality.client.portal.end.EndEyeGlow::tick);
		naturality.client.fog.EndFogComposite.initialize();
		naturality.client.portal.PortalOverlay.initialize();
		naturality.client.portal.PortalCrossingClient.initialize();
		naturality.client.portal.PortalOpeningClient.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(naturality.client.portal.PortalOpeningClient::tick);
		PortalGlowRenderLayer.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(PortalGlowController::tick);
	}
}










