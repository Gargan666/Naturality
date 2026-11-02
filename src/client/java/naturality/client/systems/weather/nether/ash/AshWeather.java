package naturality.client.systems.weather.nether.ash;

import java.util.ArrayList;
import naturality.api.worlds.LoadedChunks;
import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import naturality.client.systems.weather.framework.WeatherClient.WeatherParticleContext;
import naturality.client.systems.weather.overworld.precipitation.ParticleWeather;
import naturality.client.systems.weather.overworld.precipitation.ParticleWeather.WeatherClusterParticle;
import naturality.config.live.NaturalityConfig;
import naturality.config.startup.FeatureConfig.Feature;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Basalt-delta ash uses the shared snow cards with local Nether shelter rules. */
@org.jspecify.annotations.NullMarked
public final class AshWeather {
    private static final int MAX_CLUSTERS = 256;
    private static final int ROOF_CLEARANCE = 16;
    private static final ArrayList<WeatherClusterParticle> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static @Nullable ClientLevel world;
    private static final WeatherClusterParticle.Environment ENVIRONMENT = new WeatherClusterParticle.Environment() {
        @Override public boolean active(ClientLevel level, BlockPos position) {
            return enabled(level) && LoadedChunks.has(level, position) && level.getBiome(position).is(Biomes.BASALT_DELTAS);
        }
        @Override public double clearance(ClientLevel level, double x, double y, double z) {
            return exposed(level, x, y, z) ? 1 : -1;
        }
    };

    private AshWeather() {}

    public static boolean enabled(ClientLevel level) {
        return Feature.VISUAL_OVERHAUL.enabled() && NaturalityConfig.get().effects.weatherParticles
            && level.dimension().equals(Level.NETHER)
            && !naturality.client.ui.menus.previews.MenuPreview.active();
    }

    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(NaturalityParticles.ASH_CLUSTER, sprites ->
            (_, level, x, y, z, _, _, _, random) -> new WeatherClusterParticle(level, x, y, z,
                sprites.getSprites().get(random.nextInt(sprites.getSprites().size())), random, true, 2, ENVIRONMENT));
        naturality.client.systems.sleep.SleepVisuals.register(AshWeather::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> { clear(); world = null; });
    }

    private static void clear() {
        for (var particle : ACTIVE) particle.remove();
        ACTIVE.clear();
    }

    private static void tick(Minecraft client) {
        var level = client.level;
        if (world != level) { clear(); world = level; }
        if (level == null) return;
        if (client.isPaused()) return;
        ACTIVE.removeIf(particle -> {
            if (level.getGameTime() - particle.lastTickTime() > 10) particle.remove();
            return !particle.isAlive();
        });
        if (!enabled(level)) { ACTIVE.forEach(WeatherClusterParticle::retire); return; }
        int radius = ParticleWeather.radius(client);
        float quality = switch (client.options.particles().get()) {
            case MINIMAL -> .25F;
            case DECREASED -> .5F;
            default -> 1F;
        };
        int target = Math.round(Math.min(MAX_CLUSTERS, 96 * radius * radius / 256F) * quality);
        int remaining = 0;
        for (var particle : ACTIVE) {
            if (!particle.retiring() && ++remaining > target) particle.retire();
        }
        // Retiring cards retain their budget slots until fully transparent.
        var eye = client.gameRenderer.mainCamera().position();
        WeatherParticleContext.begin(level);
        try {
            for (int attempts = 0, births = 0; attempts < 80 && births < 16 && ACTIVE.size() < target; attempts++) {
                double x = eye.x + (RANDOM.nextDouble() * 2 - 1) * radius;
                double z = eye.z + (RANDOM.nextDouble() * 2 - 1) * radius;
                if ((x-eye.x)*(x-eye.x) + (z-eye.z)*(z-eye.z) > radius*radius) continue;
                double y = eye.y - 4 + RANDOM.nextDouble() * 18;
                if (!ENVIRONMENT.active(level, BlockPos.containing(x,y,z)) || !exposed(level,x,y,z)) continue;
                var particle = client.particleEngine.createParticle(NaturalityParticles.ASH_CLUSTER, x,y,z,0,0,0);
                if (particle instanceof WeatherClusterParticle cluster) { ACTIVE.add(cluster); births++; }
            }
        } finally { WeatherParticleContext.end(); }
    }

    /** Require open local air, not the sky above the Nether's bedrock ceiling. */
    private static boolean exposed(ClientLevel level, double x, double y, double z) {
        double extent = ParticleWeather.MAX_CARD_EXTENT;
        var volume = new AABB(x-extent,y-extent,z-extent,x+extent,y+ROOF_CLEARANCE,z+extent);
        if (volume.minY < level.getMinY() || volume.maxY >= level.getMaxY()) return false;
        // Collision queries must never reach an unloaded column, including their
        // one-block border for shapes that extend outside their owning block.
        int minX = ((int)Math.floor(volume.minX)-1)>>4, maxX = ((int)Math.floor(volume.maxX)+1)>>4;
        int minZ = ((int)Math.floor(volume.minZ)-1)>>4, maxZ = ((int)Math.floor(volume.maxZ)+1)>>4;
        for (int cx=minX; cx<=maxX; cx++) for (int cz=minZ; cz<=maxZ; cz++)
            if (level.getChunkSource().getChunkNow(cx,cz) == null) return false;
        return emptyVolume(level, volume)
            || !level.getBlockCollisions(null, volume).iterator().hasNext() && !level.containsAnyLiquid(volume);
    }

    /** Conservative broad phase: air contains neither fluid nor collision geometry.
     * Include the native collision iterator's border for shapes extending out of blocks.
     * Read current sections directly; no cache can outlive edits or chunk replacement. */
    private static boolean emptyVolume(ClientLevel level, AABB volume) {
        int x0 = (int)Math.floor(volume.minX - 1.0E-7) - 1;
        int x1 = (int)Math.floor(volume.maxX + 1.0E-7) + 1;
        int y0 = Math.max(level.getMinY(), (int)Math.floor(volume.minY - 1.0E-7) - 1);
        int y1 = Math.min(level.getMaxY() - 1, (int)Math.floor(volume.maxY + 1.0E-7) + 1);
        int z0 = (int)Math.floor(volume.minZ - 1.0E-7) - 1;
        int z1 = (int)Math.floor(volume.maxZ + 1.0E-7) + 1;
        for (int cx=x0>>4; cx<=x1>>4; cx++) for (int cz=z0>>4; cz<=z1>>4; cz++) {
            var chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) return false;
            for (int sy=y0>>4; sy<=y1>>4; sy++) {
                var section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                if (section.hasOnlyAir()) continue;
                for (int y=Math.max(y0,sy<<4); y<=Math.min(y1,(sy<<4)+15); y++)
                    for (int x=Math.max(x0,cx<<4); x<=Math.min(x1,(cx<<4)+15); x++)
                        for (int z=Math.max(z0,cz<<4); z<=Math.min(z1,(cz<<4)+15); z++)
                            if (!section.getBlockState(x&15,y&15,z&15).isAir()) return false;
            }
        }
        return true;
    }
}
