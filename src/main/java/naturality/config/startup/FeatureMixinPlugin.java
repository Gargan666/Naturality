package naturality.config.startup;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import static naturality.config.startup.FeatureConfig.Feature.*;

/** Remove structural changes before vanilla builds block-state and entity-data tables. */
@SuppressWarnings("null") // Mixin's SPI predates null contracts; null lists select its defaults.
@org.jspecify.annotations.NullMarked
public final class FeatureMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { FeatureConfig.get(); }
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String name = mixin.substring(Math.max(mixin.lastIndexOf('.'), mixin.lastIndexOf('$')) + 1);
        // Optional renderer targets must never be resolved when Sodium is absent.
        if (name.startsWith("Sodium") && !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) return false;
        boolean client = mixin.startsWith("naturality.client.");
        // Player body geometry and its camera/model must agree even without optional visuals.
        if (name.startsWith("PlayerRotation") || name.startsWith("PlayerSwimming") || name.startsWith("WaterSurface")) return true;
        if (name.startsWith("MenuPreview")) return true;
        if (Set.of("TitleLogoMixin", "TitleWorldMixin", "MenuPanoramaMixin", "MenuPreviewScreenMixin",
                "MenuPreviewCameraMixin", "MenuPreviewHudMixin", "MenuPreviewOptionsMixin", "MenuPreviewVisibilityMixin",
                "MenuPreviewFogMixin", "MenuPreviewServerMixin", "MenuSkyProjectionMixin",
                "MenuSurfacePacketMixin", "MenuSurfaceChunkMixin", "MenuStartupConnectionMixin",
                "MenuPreviewEntityMixin", "MenuWorldSelectionMixin", "MenuCreateWorldMixin", "MenuCreateWorldAccess",
                "MenuPreviewPlayerMixin", "MenuPreviewSoundMixin", "MenuPreviewMobMixin", "MenuPreviewGenerationMobMixin", "MenuPresetSkyMixin").contains(name)) return true;
        if (name.equals("NavigationCompassMixin") || name.equals("NavigationHudMixin")) return NAVIGATION_INSTRUMENTS.enabled();
        if (name.startsWith("Insanity")) return CONTENT.enabled();
        if (name.equals("MaddenedSpawnMixin")) return CONTENT.enabled() && EVENT_CYCLES.enabled();
        if (name.startsWith("BloodMoonSpawn") || name.startsWith("BloodMoonLocal")) return EVENT_CYCLES.enabled();
        if (name.equals("MaddenedSoundMixin") || name.equals("MaddenedSoundPitchMixin")) return CONTENT.enabled();
        if (name.startsWith("FloatingMobTilt")) return VISUAL_OVERHAUL.enabled() || GHAST_SQUASH.enabled() || GHAST_PHYSICS.enabled();
        if (name.equals("GhastTentaclePhysicsMixin")) return GHAST_PHYSICS.enabled();
        if (name.equals("FishMovementMixin") || name.equals("FishMoveControlAccess") || name.equals("SchoolLeaderAccess") || name.equals("SchoolFollowingMixin")) return FISH_MOVEMENT.enabled();
        if (name.startsWith("GhastCrash")) return GHAST_PHYSICS.enabled();
        if (name.equals("ArrowImpactMixin")) return ARROW_IMPACT_EVENTS.enabled();
        if (name.equals("FireballImpactMixin")) return FIREBALL_IMPACT_EVENTS.enabled();
        if (name.startsWith("MobDeath")) return MOB_DEATH_PIXELS.enabled();
        if (name.startsWith("DeathSmoke")) return DEATH_SMOKE.enabled();
        if (name.equals("ExplosionParticleSizeMixin")) return EXPLOSION_PARTICLES.enabled();
        if (name.startsWith("Confusion")) return CONFUSION.enabled();
        if (name.equals("DistortionEffectMixin")) return DISTORTION.enabled();
        if (name.startsWith("EndGravity") || name.startsWith("Distortion"))
            return END_WEATHER.enabled() || DISTORTION.enabled();
        // Villager animation is part of its server work behavior, even without visual overhaul.
        if (client && name.startsWith("Villager")) return VILLAGER_WORK.enabled();
        // Shared state/accessors also serve the custom fishing entities.
        if (client && Set.of("FishingOwnerStateMixin", "RodItemTransformAccess", "LivingEntityItemResolverAccess",
                "FirstPersonRodTipMixin", "FirstPersonRodFrameMixin", "PlayerRodItemPoseMixin").contains(name))
            return CONTENT.enabled() || FISHING_MODELS.enabled();
        if (client && !VISUAL_OVERHAUL.enabled()) return false;
        if (name.startsWith("Reputation")) return REPUTATION.enabled();
        if (name.startsWith("ChorusFlower") || name.equals("ChorusPlantMixin")) return CHORUS_FLOWERS.enabled();
        if (name.equals("ChorusPlantFeatureMixin") || name.equals("EndBiomeRegionsMixin"))
            return LIRESTONE_CRAGS.enabled() || END_WILDS.enabled();
        if (name.equals("EndTerrainDensityMixin")) return END_TERRAIN.enabled();
        if (name.equals("OceanTerrainMixin")) return OCEAN_TERRAIN.enabled();
        if (name.startsWith("OceanMonument")) return OCEAN_TERRAIN.enabled();
        if (name.equals("EndStoneTerrainMixin") || name.equals("SmallEndIslandLayersMixin")) return END_MATERIALS.enabled();
        if (name.equals("ElytraMomentumMixin")) return ELYTRA_MOMENTUM.enabled();
        if (name.equals("FireballBurnMixin")) return FIREBALL_MODELS.enabled() || FIREBALL_FIRE_SUPPRESSION.enabled();
        if (name.equals("SandPlacementParticlesMixin")) return SAND_PLACEMENT_EVENTS.enabled();
        if (name.equals("SandPlacementEventMixin")) return SAND_PARTICLES.enabled();
        if (name.equals("UndergroundLightningMixin")) return UNDERGROUND_LIGHTNING.enabled();
        if (name.equals("SnowWorldgenMixin")) return SNOW_WORLDGEN.enabled();
        if (name.equals("CrowdPanicMixin")) return CROWD_PANIC.enabled();
        if (name.equals("VillagerBreadMixin")) return VILLAGER_BREAD.enabled() || VILLAGER_SHELTER.enabled() || VILLAGER_WORK.enabled();
        if (name.equals("VillagerGateCloseMixin") || name.equals("VillagerGatePathMixin")) return VILLAGER_GATES.enabled();
        if (name.equals("VillagerWorkMixin")) return VILLAGER_WORK.enabled() || VILLAGER_GATES.enabled();
        if (name.startsWith("Butcher")) return BUTCHER_WORK.enabled();
        if (name.equals("VillagerComposterMixin") || name.equals("VillagerFarmlandLandingMixin")) return FARMER_WORK.enabled();
        if (!client && (name.startsWith("Villager") || name.equals("FishingSpotUpdatesMixin")))
            return VILLAGER_WORK.enabled();
        if (name.equals("FishingHookReturnMixin")) return FISHING_RETURN.enabled();
        if (!client && name.startsWith("Wind")) return WIND_PHYSICS.enabled();
        if (!client && name.startsWith("Weather")) return WEATHER_CYCLES.enabled();
        if (name.equals("ElytraCapeMotionMixin")) return ELYTRA_CAPE.enabled();
        if (name.equals("ElytraFovMixin")) return ELYTRA_MOMENTUM.enabled();
        if (name.equals("EmbeddedArrowStateMixin") || name.equals("MobArrowLayerMixin")) return MOB_ARROWS.enabled();
        if (client && name.startsWith("Lightning")) return LIGHTNING.enabled();
        if (name.equals("EntityShadowRenderMixin")) return PIXEL_SHADOWS.enabled();
        if (name.startsWith("Breaking") || name.startsWith("TerrainParticle") || name.equals("FallingDustSpritesMixin"))
            return PIXEL_BREAKING.enabled();
        if (name.equals("PhysicsLeadMixin")) return PHYSICS_ROPES.enabled();
        if (name.equals("FishingHookModelMixin")) return FISHING_MODELS.enabled();
        if (name.startsWith("EntityFire") || name.startsWith("SoulFire")) return ENTITY_FIRE.enabled();
        if (name.equals("SkyEventsMixin")) return END_SKY.enabled() || SKY_EVENTS.enabled();
        if (name.equals("EndFlashSoundMixin")) return END_FLASHES.enabled();
        if (name.startsWith("WeatherAudio")) return true; // Sleep muffling is a live server feature.
        return true;
    }
}
