package naturality.client.particles.smoke;

import naturality.client.blocks.fire.FireFoliageTint;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Animated buoyant smoke, with shared spatial sources and routed cloud trails for normal fire. */
@org.jspecify.annotations.NullMarked
public final class SmokeParticle extends SingleQuadParticle {
    private static final int TICKS_PER_FRAME = 5;
    private final FabricSpriteSet sprites;
    private final int firstFrame;
    private final double phase;
    private final double curlSpeed;
    private final double riseSpeed;
    private final float initialSize;
    private @Nullable Route route;
    private @Nullable Source source;
    private int born;
    private double travelSpeed;

    /** Smoke billboards share positional facing without changing their sprite or simulation. */
    public static boolean facesCameraPosition(net.minecraft.client.particle.Particle particle) {
        return particle instanceof SmokeParticle
            || particle instanceof net.minecraft.client.particle.SmokeParticle
            || particle instanceof net.minecraft.client.particle.WhiteSmokeParticle
            || particle instanceof net.minecraft.client.particle.CampfireSmokeParticle
            || particle instanceof naturality.client.particles.deathsmoke.DeathSmoke.DeathSmokeParticle
            || particle instanceof naturality.client.particles.potionclouds.FlatPotionSmokeParticle;
    }

    public static void faceCameraPosition(org.joml.Quaternionf rotation, double dx, double dy, double dz) {
        // World-up facing is independent of camera yaw, pitch and roll. At the poles,
        // atan2(0, 0) gives a deterministic yaw; coincident particles use identity.
        double horizontal = Math.hypot(dx, dz);
        if (horizontal * horizontal + dy * dy < 1.0E-12) {
            rotation.identity();
            return;
        }
        rotation.rotationY((float) Math.atan2(dx, dz))
            .rotateX((float) -Math.atan2(dy, horizontal));
    }

    public static void initialize() {
        var registry = ParticleProviderRegistry.getInstance();
        registry.register(ParticleTypes.SMOKE, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) ->
                naturality.config.live.NaturalityConfig.get().effects.smoke
                ? new SmokeParticle(level, x, y, z, vx, vy, vz, sprites, random, 1.0F)
                : new net.minecraft.client.particle.SmokeParticle.Provider(sprites).createParticle(type, level, x, y, z, vx, vy, vz, random));
        registry.register(ParticleTypes.LARGE_SMOKE, sprites -> {
            FireSmoke.sprites = sprites;
            return (type, level, x, y, z, vx, vy, vz, random) ->
                naturality.config.live.NaturalityConfig.get().effects.smoke
                ? new SmokeParticle(level, x, y, z, vx, vy, vz, sprites, random, 2.5F)
                : new net.minecraft.client.particle.LargeSmokeParticle.Provider(sprites).createParticle(type, level, x, y, z, vx, vy, vz, random);
        });
        ClientChunkEvents.CHUNK_LOAD.register(FireSmoke::loaded);
        ClientChunkEvents.CHUNK_UNLOAD.register(FireSmoke::unloaded);
        ClientTickEvents.END_CLIENT_TICK.register(FireSmoke::tick);
    }

    private SmokeParticle(ClientLevel level, double x, double y, double z,
                          double vx, double vy, double vz, FabricSpriteSet sprites,
                          RandomSource random, float scale) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        firstFrame = random.nextInt(Math.max(1, sprites.getSprites().size()));
        phase = random.nextDouble() * Math.PI * 2;
        curlSpeed = 0.045 + random.nextDouble() * 0.035;
        riseSpeed = 0.045 + random.nextDouble() * 0.025;
        lifetime = (Math.max(1, sprites.getSprites().size()) - firstFrame) * TICKS_PER_FRAME;
        initialSize = (0.09F + random.nextFloat() * 0.05F) * scale;
        quadSize = initialSize;
        xd = vx + (random.nextDouble() - 0.5) * 0.012;
        yd = vy + riseSpeed * 0.5;
        zd = vz + (random.nextDouble() - 0.5) * 0.012;
        // Keep collision so plumes collect under ceilings instead of crossing them.
        setSize(0.12F, 0.12F);
        rCol = gCol = bCol = 0.65F + random.nextFloat() * 0.2F;
        alpha = 0;
        updateSprite();
    }

    private void updateSprite() {
        var frames = sprites.getSprites();
        if (!frames.isEmpty()) {
            int frame = route == null ? firstFrame + age / TICKS_PER_FRAME
                    : Math.min(frames.size() - 1, age * frames.size() / lifetime);
            if (frame >= frames.size()) {
                remove();
                return;
            }
            setSprite(frames.get(frame));
        }
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (++age >= lifetime) {
            remove();
            return;
        }
        Route path = route;
        if (path != null) {
            // Every puff follows the source's shared streamline; no per-particle terrain collision search.
            double along = Math.min(path.points.length - 1.001, age * travelSpeed);
            int index = (int) along;
            Vec3 a = path.points[index], b = path.points[index + 1];
            double fraction = along - index;
            double cx = Mth.lerp(fraction, a.x, b.x), cy = Mth.lerp(fraction, a.y, b.y), cz = Mth.lerp(fraction, a.z, b.z);
            double flow = path.phase + along * path.spacing * .14 - FireSmoke.clock * .008;
            double amplitude = (.3 + .35 * path.strength) * Math.min(1, age / 80.0);
            xd += (amplitude * (Math.sin(flow) + .25 * Math.sin(flow * 1.7)) - xd) * .025;
            zd += (amplitude * (Math.cos(flow * .83) + .25 * Math.sin(flow * 1.3)) - zd) * .025;
            // Eddies stay inside the route's already-cleared voxel, without new collision queries per puff.
            double mx = Math.max(0, Math.min(cx - Math.floor(cx), Math.ceil(cx) - cx) - .02);
            double mz = Math.max(0, Math.min(cz - Math.floor(cz), Math.ceil(cz) - cz) - .02);
            setPos(cx + Math.clamp(xd, -mx, mx), cy, cz + Math.clamp(zd, -mz, mz));
            float fade = Mth.clamp((1 - age / (float) lifetime) / .25F, 0, 1);
            alpha = .65F * Math.min(age / 12F, 1) * fade * fade * (3 - 2 * fade);
            updateSprite();
            return;
        }
        double t = phase + age * curlSpeed;
        double spread = 0.012 + 0.018 * age / lifetime;
        // Relax toward a changing flow, avoiding frame-to-frame random jitter.
        xd += (spread * (Math.sin(t) + 0.35 * Math.sin(t * 1.73 + phase)) - xd) * 0.075;
        zd += (spread * (Math.cos(t * 0.87) + 0.35 * Math.sin(t * 1.31)) - zd) * 0.075;
        yd += (riseSpeed * (1 + 0.15 * Math.sin(t * 0.63)) - yd) * 0.06;
        move(xd, yd, zd);
        float life = age / (float) lifetime;
        float fade = Mth.clamp((1 - life) / 0.4F, 0, 1);
        alpha = 0.75F * Math.min(age / 8.0F, 1) * fade * fade * (3 - 2 * fade);
        updateSprite();
    }

    @Override
    public float getQuadSize(float partialTick) {
        Route path = route;
        float size = (path == null ? initialSize : quadSize) * (1 + 1.6F * Mth.clamp((age + partialTick) / lifetime, 0, 1));
        if (path == null) return size;
        // One 32-second wave travels up the whole plume, rather than pulsing each puff independently.
        double distance = (age + partialTick) * travelSpeed * path.spacing;
        double wave = path.phase + (FireSmoke.clock + partialTick) * (Math.PI * 2 / 640) - distance * (Math.PI * 2 / 24);
        size *= (float) (1 + .18 * Math.sin(wave));
        if (level.dimension().equals(net.minecraft.world.level.Level.NETHER))
            size = Math.min(size, (float) Math.max(0, path.points[0].y - .6 + 15 - Mth.lerp(partialTick, yo, y)));
        return size;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /** Client-local spatial emitters. Eight-block cells avoid connected-component rebuilds as fire moves. */
    public static final class FireSmoke {
        private static final int MAX_SOURCES = 32, MAX_PARTICLES = 1024;
        private static final int MIN_GROUP_SIZE = 16;
        private static final Long2ObjectOpenHashMap<ChunkSources> chunks = new Long2ObjectOpenHashMap<>();
        private static final Long2ObjectOpenHashMap<Source> cells = new Long2ObjectOpenHashMap<>();
        private static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap scorched = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        private static final ArrayList<Source> selected = new ArrayList<>();
        private static final ArrayList<SmokeParticle> particles = new ArrayList<>();
        private static final RouteBuilder builder = new RouteBuilder();
        private static @Nullable ClientLevel world;
        private static @Nullable FabricSpriteSet sprites;
        private static @Nullable FabricSpriteSet activeSprites;
        private static int clock, routeCursor, lastRouteQueries, revision;
        private static long spawned;

        private FireSmoke() { }
        public static boolean enabled() { return naturality.config.live.NaturalityConfig.get().effects.smoke; }
        public static boolean usesPlume(net.minecraft.world.level.Level level, BlockPos pos) {
            if (!enabled() || level != world) return false;
            Source source = cells.get(BlockPos.asLong(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3));
            return source != null && groupSize(source) >= MIN_GROUP_SIZE;
        }
        private static int groupSize(Source source) {
            if (source.groupRevision == revision) return source.groupSize;
            int count = 0;
            BlockPos cell = BlockPos.of(source.key);
            for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                Source neighbor = cells.get(BlockPos.asLong(cell.getX() + dx, cell.getY() + dy, cell.getZ() + dz));
                if (neighbor != null) count += neighbor.fires.size();
            }
            source.groupRevision = revision;
            return source.groupSize = count;
        }
        /** Shared loaded-fire index: foliage uses the same cells without a second world scan. */
        @FunctionalInterface public interface GroupConsumer { void accept(long cell, int count); }
        public static void forEachGroup(GroupConsumer consumer) {
            for (Source source : cells.values()) consumer.accept(source.key, groupSize(source));
        }
        public static void forEachScorched(GroupConsumer consumer) {
            for (var entry : scorched.long2IntEntrySet()) consumer.accept(entry.getLongKey(), entry.getIntValue());
        }
        public record Stats(int cells, int sources, int particles, long spawned, int routeQueries) { }
        public static Stats stats() { return new Stats(cells.size(), selected.size(), particles.size(), spawned, lastRouteQueries); }
        public static void particlesCleared() {
            for (SmokeParticle particle : particles) {
                particle.remove();
                Source source = particle.source;
                if (source != null) source.live--;
            }
            particles.clear();
        }

        private static void world(ClientLevel next) {
            if (world == next) return;
            clear();
            world = next;
        }
        private static void clear() {
            particlesCleared();
            selected.clear(); cells.clear(); chunks.clear(); scorched.clear();
            FireFoliageTint.clear();
            world = null; clock = routeCursor = lastRouteQueries = revision = 0; spawned = 0;
        }
        private static void loaded(ClientLevel level, LevelChunk chunk) {
            world(level);
            long key = chunk.getPos().pack();
            unloaded(level, chunk);
            FireFoliageTint.refresh(revision, false);
            ChunkSources owner = new ChunkSources(chunk);
            chunks.put(key, owner);
            scan(owner);
            // Initial chunk contents must be reflected before its first mesh is built.
            FireFoliageTint.refresh(revision, true);
        }
        private static void unloaded(ClientLevel level, LevelChunk chunk) {
            if (world != level) return;
            ChunkSources old = chunks.remove(chunk.getPos().pack());
            if (old == null) return;
            revision++;
            for (long key : old.scorchedCells) scorched.remove(key);
            for (long key : old.cells) {
                Source source = cells.remove(key);
                if (source != null) source.fires.clear();
            }
            selected.removeIf(source -> source.fires.isEmpty());
            for (SmokeParticle particle : particles) {
                Source source = particle.source;
                if (source != null && source.chunk == old) particle.remove();
            }
        }
        public static void changed(LevelChunk chunk, BlockPos pos, @Nullable BlockState previous) {
            if (!(chunk.getLevel() instanceof ClientLevel level) || previous == null) return;
            boolean wasFire = previous.is(Blocks.FIRE), fire = chunk.getBlockState(pos).is(Blocks.FIRE);
            boolean wasScorched = FireFoliageTint.isScorched(previous);
            boolean isScorched = FireFoliageTint.isScorched(chunk.getBlockState(pos));
            if (wasFire == fire && wasScorched == isScorched) return;
            world(level);
            ChunkSources owner = chunks.computeIfAbsent(chunk.getPos().pack(), _ -> new ChunkSources(chunk));
            if (wasFire != fire) track(owner, pos, fire);
            if (wasScorched != isScorched) trackScorched(owner, pos, isScorched);
        }
        private static void track(ChunkSources chunk, BlockPos pos, boolean fire) {
            long key = BlockPos.asLong(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3);
            Source source = cells.get(key);
            if (fire) {
                if (source == null) {
                    source = new Source(key, chunk);
                    cells.put(key, source); chunk.cells.add(key);
                }
                if (source.fires.add(pos.asLong())) revision++;
            } else if (source != null) {
                if (source.fires.remove(pos.asLong())) revision++;
                if (source.fires.isEmpty()) { cells.remove(key); chunk.cells.remove(key); }
            }
        }
        private static void trackScorched(ChunkSources owner, BlockPos pos, boolean present) {
            long key = BlockPos.asLong(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3);
            int count = scorched.get(key) + (present ? 1 : -1);
            if (count <= 0) { scorched.remove(key); owner.scorchedCells.remove(key); }
            else { scorched.put(key, count); owner.scorchedCells.add(key); }
            revision++;
        }
        private static void scan(ChunkSources owner) {
            LevelChunk chunk = owner.chunk;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                var section = chunk.getSection(index);
                if (!section.maybeHas(state -> state.is(Blocks.FIRE) || FireFoliageTint.isScorched(state))) continue;
                int bottom = chunk.getSectionYFromSectionIndex(index) << 4;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    var state = section.getBlockState(x, y, z);
                    boolean fire = state.is(Blocks.FIRE), charred = FireFoliageTint.isScorched(state);
                    if (!fire && !charred) continue;
                    pos.set(chunk.getPos().getMinBlockX() + x, bottom + y, chunk.getPos().getMinBlockZ() + z);
                    if (fire) track(owner, pos, true);
                    if (charred) trackScorched(owner, pos, true);
                }
            }
        }
        private static void tick(Minecraft client) {
            ClientLevel level = client.level;
            if (level == null) { if (world != null) clear(); return; }
            world(level);
            if (client.isPaused()) return;
            clock++;
            FireFoliageTint.refresh(revision, false);
            FireFoliageTint.tick(level);
            if (activeSprites != sprites) {
                for (SmokeParticle particle : particles) particle.remove();
                activeSprites = sprites;
            }
            particles.removeIf(particle -> {
                if (clock - particle.born > particle.lifetime + 20 || !enabled()) particle.remove();
                if (particle.isAlive()) return false;
                Source source = particle.source;
                if (source != null) source.live--;
                return true;
            });
            if (!enabled()) { selected.clear(); return; }
            Vec3 eye = client.gameRenderer.mainCamera().position();
            if (clock % 20 == 0 || selected.isEmpty()) {
                selected.clear();
                for (Source source : cells.values()) {
                    if (source.fires.isEmpty() || groupSize(source) < MIN_GROUP_SIZE) continue;
                    BlockPos cell = BlockPos.of(source.key);
                    double dx = cell.getX() * 8 + 4 - eye.x, dz = cell.getZ() * 8 + 4 - eye.z;
                    source.distance = Math.sqrt(dx * dx + dz * dz);
                    if (source.distance > client.options.getEffectiveRenderDistance() * 16 + 16) continue;
                    source.priority = source.distance / Math.sqrt(source.fires.size());
                    selected.add(source);
                }
                selected.sort(Comparator.comparingDouble(source -> source.priority));
                if (selected.size() > MAX_SOURCES) selected.subList(MAX_SOURCES, selected.size()).clear();
            }
            selected.removeIf(source -> source.fires.isEmpty() || groupSize(source) < MIN_GROUP_SIZE);
            // At most one shared route rebuild per tick, with a hard world-query limit.
            lastRouteQueries = 0;
            for (int i = 0; i < selected.size(); i++) {
                Source source = selected.get(Math.floorMod(routeCursor++, selected.size()));
                if (source.built != 0 && clock - source.built < 100) continue;
                source.route = builder.build(level, source, cloudTop(client));
                source.built = clock;
                lastRouteQueries = builder.queries;
                break;
            }
            FabricSpriteSet set = sprites;
            if (set == null) return;
            int allowance = Math.min(64, MAX_PARTICLES / Math.max(1, selected.size()));
            var setting = client.options.particles().get();
            for (Source source : selected) {
                Route path = source.route;
                if (path == null || path.points.length < 2 || clock < source.nextSpawn) continue;
                boolean far = source.distance > 96;
                int count = Math.max(4, allowance / (far ? 2 : 1));
                if (setting == net.minecraft.server.level.ParticleStatus.DECREASED) count = Math.max(4, count / 2);
                if (setting == net.minecraft.server.level.ParticleStatus.MINIMAL) count = Math.max(3, count / 4);
                double speed = (.035 + .065 * path.strength) / path.spacing;
                int life = Math.max(20, (int) Math.ceil((path.points.length - 1) / speed));
                int interval = Math.max(5, (int) Math.ceil(life / (double) count));
                if (source.live >= count || particles.size() >= MAX_PARTICLES) continue;
                Vec3 start = path.points[0];
                SmokeParticle particle = new SmokeParticle(level, start.x, start.y, start.z, 0, 0, 0,
                        set, level.getRandom(), 2.5F);
                particle.route = path; particle.source = source; particle.born = clock;
                particle.travelSpeed = speed; particle.lifetime = life;
                // Keep successive puffs overlapping even when LOD or the global budget reduces their count.
                particle.quadSize = (float) Math.max(.35 + 2.5 * path.strength, interval * speed * path.spacing * .75);
                particle.xd = particle.yd = particle.zd = 0;
                particle.hasPhysics = false;
                particle.rCol = particle.gCol = particle.bCol = .6F;
                particle.updateSprite();
                naturality.client.systems.weather.wind.WindRendering.WindParticleControl.mark(particle, true);
                client.particleEngine.add(particle);
                particles.add(particle); source.live++; spawned++;
                source.nextSpawn = clock + interval;
            }
        }
        private static double cloudTop(Minecraft client) {
            double top = naturality.client.systems.weather.overworld.precipitation.ParticleWeather.highestActiveCloudTop(client);
            if (!Double.isFinite(top)) top = client.gameRenderer.mainCamera().attributeProbe()
                    .getValue(net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT, 0);
            return Double.isFinite(top) ? top : 192;
        }
    }

    private static final class ChunkSources {
        final LevelChunk chunk;
        final LongOpenHashSet cells = new LongOpenHashSet();
        final LongOpenHashSet scorchedCells = new LongOpenHashSet();
        ChunkSources(LevelChunk chunk) { this.chunk = chunk; }
    }
    private static final class Source {
        final long key;
        final ChunkSources chunk;
        final LongOpenHashSet fires = new LongOpenHashSet();
        @Nullable Route route;
        double distance, priority;
        int live, built, nextSpawn;
        int groupRevision = -1, groupSize;
        Source(long key, ChunkSources chunk) { this.key = key; this.chunk = chunk; }
    }
    private record Route(Vec3[] points, double strength, double spacing, double phase) { }

    /** Bounded shared centerline routing; broad particle cards keep the existing smoke artwork. */
    private static final class RouteBuilder {
        private static final int SIDE = 25, CENTER = SIDE / 2;
        private final int[] parents = new int[SIDE * SIDE], queue = new int[SIDE * SIDE];
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        private int queries;

        private @Nullable Route build(ClientLevel level, Source source, double clouds) {
            queries = 0;
            if (source.fires.isEmpty()) return null;
            // An actual topmost flame is a valid outlet; the cell center may be inside a tree trunk.
            BlockPos origin = BlockPos.of(source.fires.iterator().nextLong());
            for (long packed : source.fires) {
                BlockPos candidate = BlockPos.of(packed);
                if (candidate.getY() > origin.getY()) origin = candidate;
            }
            int fuel = FireSmoke.groupSize(source);
            double strength = Math.min(1, Math.sqrt(Math.max(0, fuel - 1) / 63.0));
            if (level.dimension().equals(net.minecraft.world.level.Level.NETHER)) strength *= .5;
            double height = 6 + strength * Math.max(0, clouds + 16 - origin.getY() - 6);
            if (level.dimension().equals(net.minecraft.world.level.Level.NETHER)) height = Math.min(height, 15);
            // Taller configured cloud layers use longer segments, keeping the route itself bounded.
            double step = Math.max(1, height / 300);
            var wind = naturality.client.systems.weather.framework.WeatherClient.WeatherParticleContext.wind(level);
            double wx = wind.enabled() ? Math.clamp(wind.x() * 8, -.3, .3) : 0;
            double wz = wind.enabled() ? Math.clamp(wind.z() * 8, -.3, .3) : 0;
            double phase = (source.key & 1023) * .017;
            List<Vec3> points = new ArrayList<>();
            Vec3 current = new Vec3(origin.getX() + .5, origin.getY() + .6, origin.getZ() + .5);
            points.add(current);
            while (points.size() < 384 && current.y < origin.getY() + height && queries < 2048) {
                double h = current.y - origin.getY();
                double ascent = Math.min(step, origin.getY() + height - current.y);
                Vec3 next = current.add((wx + .12 * Math.sin(h * .06 + phase)) * ascent, ascent,
                        (wz + .12 * Math.cos(h * .06 + phase)) * ascent);
                boolean open = true;
                int samples = (int) Math.ceil(step * 2);
                for (int i = 1; i <= samples; i++) {
                    if (!clear(level, current.lerp(next, i / (double) samples))) { open = false; break; }
                }
                if (open) {
                    points.add(next); current = next;
                } else {
                    if (!around(level, current, points)) break;
                    Vec3 end = points.getLast();
                    if (end.y > origin.getY() + height) points.set(points.size() - 1, new Vec3(end.x, origin.getY() + height, end.z));
                    current = points.getLast();
                }
            }
            if (points.size() < 2) return null;
            double spacing = 1;
            for (int i = 1; i < points.size(); i++) spacing = Math.max(spacing, points.get(i - 1).distanceTo(points.get(i)));
            return new Route(points.toArray(Vec3[]::new), strength, spacing, phase);
        }
        private boolean clear(ClientLevel level, Vec3 point) {
            if (queries >= 2048) return false;
            queries++;
            probe.set(Mth.floor(point.x), Mth.floor(point.y), Mth.floor(point.z));
            LevelChunk chunk = level.getChunkSource().getChunkNow(probe.getX() >> 4, probe.getZ() >> 4);
            return chunk != null && (level.isOutsideBuildHeight(probe)
                    || chunk.getBlockState(probe).getCollisionShape(level, probe).isEmpty());
        }
        private boolean around(ClientLevel level, Vec3 start, List<Vec3> points) {
            java.util.Arrays.fill(parents, -2);
            int center = CENTER + CENTER * SIDE;
            parents[center] = -1; queue[0] = center;
            int end = 1, found = -1;
            int ox = Mth.floor(start.x), oz = Mth.floor(start.z);
            for (int head = 0; head < end && queries < 2048; head++) {
                int index = queue[head], x = index % SIDE, z = index / SIDE;
                Vec3 point = new Vec3(ox + x - CENTER + .5, start.y, oz + z - CENTER + .5);
                if (index != center && clear(level, point.add(0, 1, 0))) { found = index; break; }
                for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    int nx = x + direction.getStepX(), nz = z + direction.getStepZ();
                    if (nx < 0 || nx >= SIDE || nz < 0 || nz >= SIDE) continue;
                    int next = nx + nz * SIDE;
                    if (parents[next] != -2 || !clear(level, point.add(direction.getStepX(), 0, direction.getStepZ()))) continue;
                    parents[next] = index; queue[end++] = next;
                }
            }
            if (found < 0) return false;
            int length = 0;
            for (int at = found; at != center; at = parents[at]) queue[length++] = at;
            for (int i = length - 1; i >= 0; i--) {
                int at = queue[i];
                points.add(new Vec3(ox + at % SIDE - CENTER + .5, start.y, oz + at / SIDE - CENTER + .5));
            }
            // Force one upward step before trying wind again, so a sideways wind cannot revisit the same roof.
            points.add(points.getLast().add(0, 1, 0));
            return true;
        }
    }
}
