package naturality.client.weather;

import java.util.ArrayList;
import naturality.NaturalityParticles;
import naturality.client.particle.WeatherClusterParticle;
import naturality.config.NaturalityConfig;
import naturality.weather.WeatherSystem;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;

/** Bounded, camera-near spawning; existing cards always move in world coordinates. */
public final class ParticleWeather {
    public static final int MAX_CLUSTERS = 1536;
    private static final double MAX_CARD_EXTENT = 3.4;
    private static final ArrayList<WeatherClusterParticle> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private ParticleWeather() {}
    public static void initialize() {
        WeatherClusterParticle.initialize();
        naturality.client.sleep.SleepVisuals.register(ParticleWeather::tick);
    }
    public static boolean enabled(ClientLevel level) { return naturality.config.NaturalityConfig.get().effects.weatherParticles && level.canHaveWeather() && weatherState(level) != null; }
    public static naturality.weather.@org.jspecify.annotations.Nullable WeatherState weatherState(ClientLevel level) {
        var state = WeatherSystem.state(level);
        return state == null && naturality.client.menu.MenuPreview.active()
                ? new naturality.weather.WeatherState(level.getRainLevel(1) * 25, 0, 50, 0) : state;
    }
    public static int activeCount() { return ACTIVE.size(); }
    public static long snowCount() { return ACTIVE.stream().filter(WeatherClusterParticle::snow).count(); }
    public static void clear() { for (var p : ACTIVE) p.remove(); ACTIVE.clear(); }
    public static int radius(Minecraft client) { return Math.clamp(Math.round(client.options.weatherRadius().get() * 1.5F), 0, 36); }
    private static void tick(Minecraft client) {
        var level = client.level;
        if (level == null) { clear(); world = null; return; }
        WeatherParticleContext.begin(level);
        try { tickParticles(client); }
        finally { WeatherParticleContext.end(); }
    }
    private static void tickParticles(Minecraft client) {
        if (world != client.level) { clear(); world = client.level; }
        var world = ParticleWeather.world;
        if (world == null || !enabled(world)) { clear(); return; }
        if (client.isPaused()) return;
        if (naturality.client.menu.MenuPreview.active()) { tickPreview(client); return; }
        ACTIVE.removeIf(p -> {
            // Engine eviction/resource reload may stop ticking a still-alive object.
            if (world.getGameTime() - p.lastTickTime() > 10) p.remove();
            return !p.isAlive();
        });
        int radius = radius(client);
        var state = WeatherSystem.state(world);
        if (state == null) { clear(); return; }
        float quality = switch (client.options.particles().get()) {
            case MINIMAL -> .25F;
            case DECREASED -> .5F;
            default -> 1F;
        };
        int target = Math.round(Math.min(MAX_CLUSTERS, 80 * state.precipitationDensity() * radius * radius / 256F) * quality);
        while (ACTIVE.size() > target) ACTIVE.removeLast().remove();
        if(ACTIVE.size()>=target)return;
        var eye = client.gameRenderer.mainCamera().position();
        double cloudTop = highestActiveCloudTop(client);
        int births = 0;
        for (int attempts = 0; attempts < 320 && births < 80 && ACTIVE.size() < target; attempts++) {
            double x = eye.x + (RANDOM.nextDouble() * 2 - 1) * radius;
            double z = eye.z + (RANDOM.nextDouble() * 2 - 1) * radius;
            if ((x-eye.x)*(x-eye.x)+(z-eye.z)*(z-eye.z) > radius*radius) continue;
            double y = eye.y - 4 + RANDOM.nextDouble() * 18;
            if (y + MAX_CARD_EXTENT > cloudTop) continue;
            var pos = BlockPos.containing(x, y, z);
            if (!naturality.util.LoadedChunks.has(world, pos)) continue;
            var kind = WeatherParticleContext.precipitation(world, pos);
            if (kind == Biome.Precipitation.NONE || clearance(world, x, y, z, MAX_CARD_EXTENT) <= 0) continue;
            var particle = client.particleEngine.createParticle(kind == Biome.Precipitation.SNOW
                ? NaturalityParticles.SNOW_CLUSTER : NaturalityParticles.RAIN_CLUSTER,
                x, y, z, 0, 0, 0);
            if (particle instanceof WeatherClusterParticle cluster) { ACTIVE.add(cluster); births++; }
        }
    }
    /** A bounded foreground sheet: the orbit eye is often outside loaded terrain. */
    private static void tickPreview(Minecraft client) {
        var level = client.level;
        if (level == null) return;
        var climate = weatherState(level);
        var kind = WeatherParticleContext.precipitation(level, naturality.client.menu.MenuPreview.spawn());
        ACTIVE.removeIf(p -> !p.isAlive() || p.snow() != (kind == Biome.Precipitation.SNOW) || !previewContains(p.position()));
        float quality = switch (client.options.particles().get()) {
            case MINIMAL -> .25F; case DECREASED -> .5F; default -> 1F;
        };
        int target = climate == null || kind == Biome.Precipitation.NONE ? 0
                : Math.round(Math.min(256, climate.precipitationDensity() * 100) * quality);
        while (ACTIVE.size() > target) ACTIVE.removeLast().remove();
        var camera = client.gameRenderer.mainCamera();
        var projection = naturality.client.menu.MenuPreview.projection();
        for (int births = 0; births < 24 && ACTIVE.size() < target; births++) {
            var offset = new org.joml.Vector3f((RANDOM.nextFloat() * 2 - 1) / projection.m00(),
                    (RANDOM.nextFloat() * 2 - 1) / projection.m11(), -16 - RANDOM.nextFloat() * 32);
            camera.rotation().transform(offset);
            var eye = camera.position();
            var particle = client.particleEngine.createParticle(kind == Biome.Precipitation.SNOW
                    ? NaturalityParticles.SNOW_CLUSTER : NaturalityParticles.RAIN_CLUSTER,
                    eye.x + offset.x, eye.y + offset.y, eye.z + offset.z, 0, 0, 0);
            if (particle instanceof WeatherClusterParticle cluster) ACTIVE.add(cluster);
        }
    }
    public static boolean previewContains(net.minecraft.world.phys.Vec3 position) {
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var relative = position.subtract(camera.position());
        var local = new org.joml.Vector3f((float)relative.x, (float)relative.y, (float)relative.z);
        new org.joml.Quaternionf(camera.rotation()).conjugate().transform(local);
        var projection = naturality.client.menu.MenuPreview.projection();
        return Math.abs(local.x * projection.m00()) < 1.15F && Math.abs(local.y * projection.m11()) < 1.15F
                && local.z < -4 && local.z > -80;
    }
    /** The rendered layers' highest top edge; no visible clouds means no spawn ceiling. */
    public static double highestActiveCloudTop(Minecraft client) {
        if (client.options.cloudStatus().get() == CloudStatus.OFF) return Double.POSITIVE_INFINITY;
        var camera = client.gameRenderer.mainCamera();
        var probe = camera.attributeProbe();
        if (probe.getValue(EnvironmentAttributes.CLOUD_COLOR, 0).w() <= 0)
            return Double.POSITIVE_INFINITY;
        float baseHeight = probe.getValue(EnvironmentAttributes.CLOUD_HEIGHT, 0);
        if (!Float.isFinite(baseHeight)) return Double.POSITIVE_INFINITY;
        var clouds = NaturalityConfig.get().clouds;
        if (!clouds.enabled) return baseHeight + (client.options.cloudStatus().get() == CloudStatus.FANCY ? 4 : 0);
        double top = Double.NEGATIVE_INFINITY;
        if (clouds.base.enabled && clouds.base.opacityPercent > 0)
            top = Math.max(top, baseHeight + clouds.base.heightOffset + (clouds.base.style == 2 ? 0 : clouds.base.thickness));
        if (clouds.upper.enabled && clouds.upper.opacityPercent > 0)
            top = Math.max(top, baseHeight + clouds.upper.heightOffset + (clouds.upper.style == 2 ? 0 : clouds.upper.thickness));
        return top == Double.NEGATIVE_INFINITY ? Double.POSITIVE_INFINITY : top;
    }
    /** Conservative whole-card clearance: no chunk loads and no cards straddling a roof. */
    public static double clearance(ClientLevel level, double x, double y, double z, double extent) {
        if(extent==0) {
            int height=WeatherParticleContext.floor(level,(int)Math.floor(x),(int)Math.floor(z));
            return height==Integer.MIN_VALUE?-1:y-Math.max(level.getMinY(),height);
        }
        double floor = level.getMinY();
        for (int bx = (int)Math.floor(x-extent); bx <= (int)Math.floor(x+extent); bx++)
            for (int bz = (int)Math.floor(z-extent); bz <= (int)Math.floor(z+extent); bz++) {
                int height = WeatherParticleContext.floor(level, bx, bz);
                if (height == Integer.MIN_VALUE) return -1;
                floor = Math.max(floor, height);
            }
        return y - extent - floor;
    }
}
