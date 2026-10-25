package naturality.client.systems.weather.framework;
import naturality.client.systems.weather.wind.WindRendering.WindAudio;

import naturality.client.systems.weather.overworld.lightning.DistantLightningClient;
import naturality.client.systems.weather.overworld.lightning.DistantLightningClient.ThunderAudio;
import naturality.systems.weather.framework.WeatherSystem.WeatherPayload;
import naturality.systems.weather.framework.WeatherSystem;
import naturality.systems.weather.end.framework.EndWeatherSystem.EndWeatherPayload;
import naturality.systems.weather.end.framework.EndWeatherSystem;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import naturality.client.systems.weather.overworld.precipitation.ParticleWeather;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import naturality.config.live.NaturalityConfig;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@org.jspecify.annotations.NullMarked
public final class WeatherClient {
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static @org.jspecify.annotations.Nullable Object connection;
    private WeatherClient() {}
    public static void initialize() {
        if (!naturality.config.startup.FeatureConfig.clientOnly())
            ClientPlayNetworking.registerGlobalReceiver(EndWeatherPayload.TYPE, (p,_) -> EndWeatherSystem.receive(p));
        ClientPlayConnectionEvents.INIT.register((_,_) -> EndWeatherSystem.clearClient());
        ClientPlayConnectionEvents.DISCONNECT.register((_,_) -> EndWeatherSystem.clearClient());
        DistantLightningClient.initialize();
        ThunderAudio.initialize();
        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            WeatherSystem.clearClient();
            WindAudio.stop(client);
            connection = handler;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((_, client) -> {
            WeatherSystem.clearClient();
            WindAudio.stop(client);
            connection = null;
            world = null;
        });
        if (!naturality.config.startup.FeatureConfig.clientOnly())
            ClientPlayNetworking.registerGlobalReceiver(WeatherPayload.TYPE, (payload, _) -> WeatherSystem.receive(payload));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(WeatherClient::tick);
    }
    private static void tick(Minecraft client) {
        // The cache is keyed by dimension, so discard it when the play
        // connection changes to a different saved world/server session.
        Object currentConnection = client.getConnection();
        if (connection != currentConnection) {
            WeatherSystem.clearClient();
            WindAudio.stop(client);
            WeatherSoundEnvironment.reset();
            connection = currentConnection;
        }
        if (world != client.level) { WindAudio.stop(client); world = client.level; WeatherSoundEnvironment.reset(); }
        var world = WeatherClient.world;
        // Exposure is consumed only by optional weather audio, fog and lightning.
        // Keep connection cleanup/network state above, without casting shelter
        // rays every tick when the visual overhaul is disabled at startup.
        if (world == null || client.isPaused()
                || !naturality.config.startup.FeatureConfig.Feature.VISUAL_OVERHAUL.enabled()) return;
        WeatherSoundEnvironment.tick(client);
        var state = WeatherSystem.state(world);
        WindAudio.update(client, state);
    }

    /** Local sky access for weather audio, including the open sides of overhangs. */
    public static final class WeatherSoundEnvironment {
        private static final double[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {.70710678, .70710678}, {.70710678, -.70710678},
            {-.70710678, .70710678}, {-.70710678, -.70710678}
        };
        private static final double[] DISTANCES = {2, 4, 8};
        private static float indoor = 1;
        private static boolean initialized;
        private static boolean underground;

        private WeatherSoundEnvironment() {}

        public static void reset() { indoor = 1; initialized = false; underground = false; }
        public static boolean underground() { return underground; }
        public static float indoor() { return indoor; }
        public static float windExposure() { return underground ? 0 : .15F + .85F * (1 - indoor); }
        /** Share the same outdoor test between particle shelter and player ambience. */
        public static boolean hasOpenSkyAccess(Level level, Vec3 origin, Entity context) {
            return openSkyExposure(level, origin, context) > 0;
        }

        private static float openSkyExposure(Level level, Vec3 origin, Entity context) {
            if (level.dimension().equals(Level.NETHER)) {
                return 1; // Nether wind has no indoor or cave shelter checks.
            }
            var probe = new BlockPos.MutableBlockPos().set(origin.x, origin.y, origin.z);
            if (canSeeSky(level,probe)) return 1;
            for (double distance : DISTANCES) {
                float reach = distance <= 2 ? 1 : distance <= 4 ? .85F : .65F;
                for (double[] direction : DIRECTIONS) {
                    double x = origin.x + direction[0] * distance, z = origin.z + direction[1] * distance;
                    probe.set(x, origin.y, z);
                    if (!canSeeSky(level,probe)) continue;
                    // Sheltered samples only need their block coordinates. Build
                    // an exact-position ray endpoint only when the sky is open.
                    Vec3 sample = new Vec3(x, origin.y, z);
                    if (level.clip(new ClipContext(origin, sample, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, context)).getType() == HitResult.Type.MISS)
                        return reach;
                }
            }
            return 0;
        }
        private static boolean canSeeSky(Level level,BlockPos pos) {
            return level instanceof net.minecraft.client.multiplayer.ClientLevel client
                ?WeatherParticleContext.canSeeSky(client,pos):level.canSeeSky(pos);
        }

        public static void tick(Minecraft client) {
            var level = client.level;
            var cameraEntity = client.getCameraEntity();
            if (level == null || cameraEntity == null) { reset(); return; }
            if (level.dimension().equals(Level.NETHER)) {
                indoor = 0; initialized = true; underground = false;
                return;
            }
            Vec3 eye = client.gameRenderer.mainCamera().position();
            underground = naturality.systems.weather.framework.WeatherSystem.WeatherShelter.underground(level, eye, cameraEntity);
            float open = openSkyExposure(level, eye, cameraEntity);
            float target = 1 - open;
            if (!initialized) { indoor = target; initialized = true; }
            else indoor += Math.clamp(target - indoor, -.08F, .08F);
        }
    }

    /** Thread-confined scratch data, valid only during a particle update, spawn or render pass. */
    public static final class WeatherParticleContext {
        private static final int MAX_ENTRIES = 8192;
        private static final ThreadLocal<PassStack> PASSES = ThreadLocal.withInitial(PassStack::new);

        private WeatherParticleContext() { }

        /** Particle engines and groups expose their own world, including during disconnect. */
        public interface LevelOwner {
            @org.jspecify.annotations.Nullable ClientLevel naturality$particleLevel();
        }

        public static @org.jspecify.annotations.Nullable ClientLevel level(Object owner) {
            return owner instanceof LevelOwner access ? access.naturality$particleLevel() : null;
        }

        /** Each parallel-stream leaf establishes its scope on the thread consuming that leaf. */
        public static <T> Stream<T> scopedStream(ClientLevel level, Stream<T> stream) {
            boolean parallel = stream.isParallel();
            return StreamSupport.stream(new ScopedSpliterator<>(level, stream.spliterator()), parallel)
                .onClose(stream::close);
        }

        private static final class ScopedSpliterator<T> implements Spliterator<T> {
            private final ClientLevel level;
            private final Spliterator<T> delegate;

            private ScopedSpliterator(ClientLevel level, Spliterator<T> delegate) {
                this.level = level;
                this.delegate = delegate;
            }

            @Override public @org.jspecify.annotations.Nullable Spliterator<T> trySplit() {
                var split = delegate.trySplit();
                return split == null ? null : new ScopedSpliterator<>(level, split);
            }

            @Override public void forEachRemaining(@org.jspecify.annotations.Nullable Consumer<? super T> action) {
                var consumer = java.util.Objects.requireNonNull(action);
                begin(level);
                try { delegate.forEachRemaining(consumer); }
                finally { end(); }
            }

            @Override public boolean tryAdvance(@org.jspecify.annotations.Nullable Consumer<? super T> action) {
                // Short-circuiting streams can request single elements. The normal
                // particle map/toList path uses one forEachRemaining scope per leaf.
                var consumer = java.util.Objects.requireNonNull(action);
                begin(level);
                try { return delegate.tryAdvance(consumer); }
                finally { end(); }
            }

            @Override public long estimateSize() { return delegate.estimateSize(); }
            @Override public int characteristics() { return delegate.characteristics(); }
            @Override public @org.jspecify.annotations.Nullable Comparator<? super T> getComparator() {
                return delegate.getComparator();
            }
        }

        // Async particle ticks can overlap spawning/extraction and each other.
        // Reuse the outer scratch maps without sharing them across threads. A nested
        // pass gets independent data and restores its caller's immutable snapshots.
        private static final class PassStack {
            private @org.jspecify.annotations.Nullable Pass reusable;
            private @org.jspecify.annotations.Nullable Pass current;
        }

        private static final class Pass {
            private final @org.jspecify.annotations.Nullable Pass parent;
            private final Long2IntOpenHashMap heights = new Long2IntOpenHashMap();
            private final Long2IntOpenHashMap tints = new Long2IntOpenHashMap();
            private final Long2IntOpenHashMap lights = new Long2IntOpenHashMap();
            private final Long2ByteOpenHashMap sky = new Long2ByteOpenHashMap();
            private final Long2ObjectOpenHashMap<Biome.Precipitation> precipitation = new Long2ObjectOpenHashMap<>();
            private @org.jspecify.annotations.Nullable ClientLevel world;
            private @org.jspecify.annotations.Nullable State state;
            private @org.jspecify.annotations.Nullable Wind wind;

            private Pass(@org.jspecify.annotations.Nullable Pass parent) {
                this.parent = parent;
                heights.defaultReturnValue(Integer.MAX_VALUE);
                tints.defaultReturnValue(Integer.MIN_VALUE);
                lights.defaultReturnValue(Integer.MIN_VALUE);
            }

            private void clear() {
                // Worker pools outlive worlds: keep only empty primitive scratch
                // storage after a pass, never its world, camera or weather snapshot.
                world = null;
                state = null;
                wind = null;
                heights.clear();
                tints.clear();
                lights.clear();
                sky.clear();
                precipitation.clear();
            }
        }

        private static @org.jspecify.annotations.Nullable Pass current(ClientLevel level) {
            var pass = PASSES.get().current;
            return pass != null && pass.world == level ? pass : null;
        }

        public record State(boolean enabled, double rainWindX, double rainWindZ,
                            double snowWindX, double snowWindZ, float snowFallMultiplier, Vec3 eye, int radius,
                            float heavyFog) {
            public float heavyParticleChance(double x,double y,double z) {
                if(heavyFog==0)return 0;
                double dx=x-eye.x,dy=y-eye.y,dz=z-eye.z;
                double squared=dx*dx+dy*dy+dz*dz;
                return squared>=225?heavyFog:heavyFog*(float)(Math.sqrt(squared)/15);
            }
        }
        public record Wind(boolean enabled,double x,double z,@org.jspecify.annotations.Nullable Entity camera) { }
        public static Wind wind(ClientLevel level) {
            var pass = current(level);
            var cached = pass == null ? null : pass.wind;
            if(cached!=null)return cached;
            var weather=ParticleWeather.weatherState(level);
            var result=new Wind(weather!=null && NaturalityConfig.get().effects.particleWind,
                weather==null?0:weather.windX()*.007,weather==null?0:weather.windZ()*.007,Minecraft.getInstance().getCameraEntity());
            if(pass!=null)pass.wind=result;
            return result;
        }

        public static void begin(ClientLevel level) {
            var stack = PASSES.get();
            Pass pass;
            if (stack.current == null) {
                var reusable = stack.reusable;
                if (reusable == null) stack.reusable = reusable = new Pass(null);
                pass = reusable;
            } else pass = new Pass(stack.current);
            pass.world = level;
            stack.current = pass;
        }

        public static void end() {
            var stack = PASSES.get();
            var pass = stack.current;
            if (pass == null) return;
            stack.current = pass.parent;
            pass.clear();
        }

        public static State state(ClientLevel level) {
            var pass = current(level);
            var cached = pass == null ? null : pass.state;
            if (cached != null) return cached;
            var client = Minecraft.getInstance();
            var weather = ParticleWeather.weatherState(level);
            // Keep the original double multiplication (weather wind components are floats).
            double windX = weather == null ? 0 : weather.windX();
            double windZ = weather == null ? 0 : weather.windZ();
            var result = new State(client.level == level && NaturalityConfig.get().effects.weatherParticles
                && level.canHaveWeather() && weather != null && weather.rain() > 0,
                windX * .34, windZ * .34, windX * .10, windZ * .10,
                weather == null ? 1 : weather.snowFallMultiplier(),
                client.gameRenderer.mainCamera().position(), ParticleWeather.radius(client),weather==null?0:weather.heavyRainFog());
            if (pass != null) pass.state = result;
            return result;
        }

        public static int waterTint(ClientLevel level, BlockPos pos) {
            if (naturality.client.ui.menus.previews.MenuPreview.active()) pos = naturality.client.ui.menus.previews.MenuPreview.spawn();
            var pass = current(level);
            if (pass == null) return BiomeColors.getAverageWaterColor(level, pos);
            long key = pos.asLong();
            int cached=pass.tints.get(key);
            if(cached!=Integer.MIN_VALUE)return cached;
            int color = BiomeColors.getAverageWaterColor(level, pos);
            if (pass.tints.size() < MAX_ENTRIES) pass.tints.put(key, color);
            return color;
        }

        public static Biome.Precipitation precipitation(ClientLevel level, BlockPos pos) {
            if (naturality.client.ui.menus.previews.MenuPreview.active()) pos = naturality.client.ui.menus.previews.MenuPreview.spawn();
            var pass = current(level);
            if (pass == null) return level.getPrecipitationAt(pos);
            long key = pos.asLong();
            var cached = pass.precipitation.get(key);
            if (cached != null) return cached;
            var kind = level.getPrecipitationAt(pos);
            if (pass.precipitation.size() < MAX_ENTRIES) pass.precipitation.put(key, kind);
            return kind;
        }

        /** Exact-position light shared for one extraction pass, including dynamic lighting hooks. */
        public static int light(ClientLevel level,BlockPos pos) {
            if (naturality.client.ui.menus.previews.MenuPreview.active()) pos = naturality.client.ui.menus.previews.MenuPreview.spawn().above(8);
            long key=pos.asLong();
            var pass = current(level);
            if(pass!=null) {
                int cached=pass.lights.get(key);
                if(cached!=Integer.MIN_VALUE)return cached;
            }
            int light=naturality.api.worlds.LoadedChunks.has(level,pos)?LightCoordsUtil.getLightCoords(level,pos):15728640;
            if(pass!=null && pass.lights.size()<MAX_ENTRIES)pass.lights.put(key,light);
            return light;
        }
        public static boolean canSeeSky(ClientLevel level,BlockPos pos) {
            var pass = current(level);
            if(pass==null)return level.canSeeSky(pos);
            long key=pos.asLong();
            byte cached=pass.sky.get(key);
            if(cached!=0)return cached==2;
            boolean visible=level.canSeeSky(pos);
            if(pass.sky.size()<MAX_ENTRIES)pass.sky.put(key,(byte)(visible?2:1));
            return visible;
        }

        /** Integer.MIN_VALUE means absent; getChunkNow never loads a chunk. */
        public static int floor(ClientLevel level, int x, int z) {
            long key = ((long)x << 32) ^ (z & 0xffffffffL);
            var pass = current(level);
            if(pass!=null) {
                int cached=pass.heights.get(key);
                if(cached!=Integer.MAX_VALUE)return cached;
            }
            var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            int height = chunk == null ? Integer.MIN_VALUE
                : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING,x&15,z&15)+1;
            if (pass != null && pass.heights.size() < MAX_ENTRIES) pass.heights.put(key, height);
            return height;
        }
    }
}
